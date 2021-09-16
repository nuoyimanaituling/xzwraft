package raft.core.rpc.nio;

import com.google.common.eventbus.EventBus;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raft.core.node.NodeId;
import raft.core.rpc.Address;
import raft.core.rpc.ChannelConnectException;

import javax.annotation.concurrent.ThreadSafe;
import java.net.ConnectException;
import java.util.concurrent.*;

/**
 * 是一个管理出口连接的组
 */
@ThreadSafe
public class OutboundChannelGroup {

    private static final Logger logger = LoggerFactory.getLogger(OutboundChannelGroup.class);
    private final EventLoopGroup workerGroup;
    private final EventBus eventBus;
    private final NodeId selfNodeId;
    private final int connectTimeoutMillis;
    private final ConcurrentMap<NodeId, Future<NioChannel>> channelMap = new ConcurrentHashMap<>();

    OutboundChannelGroup(EventLoopGroup workerGroup, EventBus eventBus, NodeId selfNodeId, int logReplicationInterval) {
        this.workerGroup = workerGroup;
        this.eventBus = eventBus;
        this.selfNodeId = selfNodeId;
        this.connectTimeoutMillis = logReplicationInterval / 2;
    }

    /**
     * 实现了一个线程安全的惰性连接方法，getChannel方法根据EndPoint查找可用的连接
     * 如果找不到就新建一个连接。这是一个惰性连接方法
     * @param nodeId
     * @param address
     * @return
     */
    NioChannel getOrConnect(NodeId nodeId, Address address) {
        Future<NioChannel> future = channelMap.get(nodeId);
        if (future == null) {
            FutureTask<NioChannel> newFuture = new FutureTask<>(() -> connect(nodeId, address));
            future = channelMap.putIfAbsent(nodeId, newFuture);
            if (future == null) {
                future = newFuture;
                newFuture.run();
            }
        }
        try {
            return future.get();
        } catch (Exception e) {
            channelMap.remove(nodeId);
            if (e instanceof ExecutionException) {
                Throwable cause = e.getCause();
                if (cause instanceof ConnectException) {
                    throw new ChannelConnectException("failed to get channel to node " + nodeId +
                            ", cause " + cause.getMessage(), cause);
                }
            }
            throw new ChannelException("failed to get channel to node " + nodeId, e);
        }
    }


    /**
     * 连接远程节点
     * @param nodeId
     * @param address
     * @return
     * @throws InterruptedException
     */
    private NioChannel connect(NodeId nodeId, Address address) throws InterruptedException {
        Bootstrap bootstrap = new Bootstrap()
                .group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new Decoder());
                        pipeline.addLast(new Encoder());
                        pipeline.addLast(new ToRemoteHandler(eventBus, nodeId, selfNodeId));
                    }
                });
        ChannelFuture future = bootstrap.connect(address.getHost(), address.getPort()).sync();
        if (!future.isSuccess()) {
            throw new ChannelException("failed to connect", future.cause());
        }
        logger.debug("channel OUTBOUND-{} connected", nodeId);
        Channel nettyChannel = future.channel();
        nettyChannel.closeFuture().addListener((ChannelFutureListener) cf -> {
            logger.debug("channel OUTBOUND-{} disconnected", nodeId);
            channelMap.remove(nodeId);
        });
        return new NioChannel(nettyChannel);
    }

    void closeAll() {
        logger.debug("close all outbound channels");
        channelMap.forEach((nodeId, nioChannelFuture) -> {
            try {
                nioChannelFuture.get().close();
            } catch (Exception e) {
                logger.warn("failed to close", e);
            }
        });
    }

}
