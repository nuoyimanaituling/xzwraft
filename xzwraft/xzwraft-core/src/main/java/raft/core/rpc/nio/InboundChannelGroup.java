package raft.core.rpc.nio;

import io.netty.channel.ChannelFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raft.core.node.NodeId;

import javax.annotation.concurrent.ThreadSafe;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@ThreadSafe
public class InboundChannelGroup {

    private static final Logger logger = LoggerFactory.getLogger(InboundChannelGroup.class);
    private final List<NioChannel> channels = new CopyOnWriteArrayList<>();

    public void add(NodeId remoteId, NioChannel channel) {
        // 增加入口连接
        logger.debug("channel INBOUND-{} connected", remoteId);
        channel.getDelegate().closeFuture().addListener((ChannelFutureListener) future -> {
            // 连接关闭时移除
            logger.debug("channel INBOUND-{} disconnected", remoteId);
            remove(channel);
        });
    }

    private void remove(NioChannel channel) {
        channels.remove(channel);
    }

    void closeAll() {
        logger.debug("close all inbound channels");
        for (NioChannel channel : channels) {
            channel.close();
        }
    }

}
