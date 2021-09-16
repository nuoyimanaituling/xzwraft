package raft.core.rpc.nio;

import com.google.common.base.Preconditions;
import com.google.common.eventbus.EventBus;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raft.core.node.NodeEndpoint;
import raft.core.node.NodeId;
import raft.core.rpc.Channel;
import raft.core.rpc.ChannelConnectException;
import raft.core.rpc.Connector;
import raft.core.rpc.message.*;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// TODO add test
@ThreadSafe
public class NioConnector implements Connector {

    private static final Logger logger = LoggerFactory.getLogger(NioConnector.class);
    private final NioEventLoopGroup bossNioEventLoopGroup = new NioEventLoopGroup(1);
    private final NioEventLoopGroup workerNioEventLoopGroup;
    // 是否和上层服务等共享IO线程池
    private final boolean workerGroupShared;
    // 解决核心组件和通信组件之间双向依赖问题的pubsub工具
    private final EventBus eventBus;
    // 节点间通信端口
    private final int port;
    private final InboundChannelGroup inboundChannelGroup = new InboundChannelGroup();
    private final OutboundChannelGroup outboundChannelGroup;

    private final ExecutorService executorService = Executors.newCachedThreadPool((r) -> {
        Thread thread = new Thread(r);
        thread.setUncaughtExceptionHandler((t, e) -> {
            logException(e);
        });
        return thread;
    });

    public NioConnector(NodeId selfNodeId, EventBus eventBus, int port, int logReplicationInterval) {
        this(new NioEventLoopGroup(), false, selfNodeId, eventBus, port, logReplicationInterval);
    }

    public NioConnector(NioEventLoopGroup workerNioEventLoopGroup, NodeId selfNodeId, EventBus eventBus, int port, int logReplicationInterval) {
        this(workerNioEventLoopGroup, true, selfNodeId, eventBus, port, logReplicationInterval);
    }

    public NioConnector(NioEventLoopGroup workerNioEventLoopGroup, boolean workerGroupShared,
                        NodeId selfNodeId, EventBus eventBus,
                        int port, int logReplicationInterval) {
        // 复用工作线程池
        this.workerNioEventLoopGroup = workerNioEventLoopGroup;
        this.workerGroupShared = workerGroupShared;
        this.eventBus = eventBus;
        this.port = port;
        outboundChannelGroup = new OutboundChannelGroup(workerNioEventLoopGroup, eventBus, selfNodeId, logReplicationInterval);
    }

    // should not call more than once
    @Override
    public void initialize() {
        ServerBootstrap serverBootstrap = new ServerBootstrap()
                .group(bossNioEventLoopGroup, workerNioEventLoopGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new Decoder());
                        pipeline.addLast(new Encoder());
                        pipeline.addLast(new FromRemoteHandler(eventBus, inboundChannelGroup));
                    }
                });
        logger.debug("node listen on port {}", port);
        try {
            serverBootstrap.bind(port).sync();
        } catch (InterruptedException e) {
            throw new ConnectorException("failed to bind port", e);
        }
    }

    @Override
    public void sendRequestVote(@Nonnull RequestVoteRpc rpc, @Nonnull Collection<NodeEndpoint> destinationEndpoints) {
        Preconditions.checkNotNull(rpc);
        Preconditions.checkNotNull(destinationEndpoints);
        for (NodeEndpoint endpoint : destinationEndpoints) {
            logger.debug("send {} to node {}", rpc, endpoint.getId());
            executorService.execute(() -> getChannel(endpoint).writeRequestVoteRpc(rpc));
        }
    }

    private void logException(Throwable e) {
        if (e instanceof ChannelConnectException) {
            logger.warn(e.getMessage());
        } else {
            logger.warn("failed to process channel", e);
        }
    }

    @Override
    public void replyRequestVote(@Nonnull RequestVoteResult result, @Nonnull RequestVoteRpcMessage rpcMessage) {
        Preconditions.checkNotNull(result);
        Preconditions.checkNotNull(rpcMessage);
        logger.debug("reply {} to node {}", result, rpcMessage.getSourceNodeId());
        try {
            rpcMessage.getChannel().writeRequestVoteResult(result);
        } catch (Exception e) {
            logException(e);
        }
    }



    @Override
    public void sendAppendEntries(@Nonnull AppendEntriesRpc rpc, @Nonnull NodeEndpoint destinationEndpoint) {
        Preconditions.checkNotNull(rpc);
        Preconditions.checkNotNull(destinationEndpoint);
        logger.debug("send {} to node {}", rpc, destinationEndpoint.getId());
        executorService.execute(() -> getChannel(destinationEndpoint).writeAppendEntriesRpc(rpc));
    }

    @Override
    public void replyAppendEntries(@Nonnull AppendEntriesResult result, @Nonnull NodeEndpoint destinationEndpoint) {

    }

    @Override
    public void replyRequestVote(@Nonnull RequestVoteResult result, @Nonnull NodeEndpoint destinationEndpoint) {

    }

    @Override
    public void replyAppendEntries(@Nonnull AppendEntriesResult result, @Nonnull AppendEntriesRpcMessage rpcMessage) {
        Preconditions.checkNotNull(result);
        Preconditions.checkNotNull(rpcMessage);
        logger.debug("reply {} to node {}", result, rpcMessage.getSourceNodeId());
        try {
            rpcMessage.getChannel().writeAppendEntriesResult(result);
        } catch (Exception e) {
            logException(e);
        }
    }

    @Override
    public void resetChannels() {
        inboundChannelGroup.closeAll();
    }

    private Channel getChannel(NodeEndpoint endpoint) {
        // 按照节点id创建或者获取连接
        return outboundChannelGroup.getOrConnect(endpoint.getId(), endpoint.getAddress());
    }

    @Override
    public void close() {
        logger.debug("close connector");
        inboundChannelGroup.closeAll();
        outboundChannelGroup.closeAll();
        bossNioEventLoopGroup.shutdownGracefully();
        if (!workerGroupShared) {
            workerNioEventLoopGroup.shutdownGracefully();
        }
    }

}
