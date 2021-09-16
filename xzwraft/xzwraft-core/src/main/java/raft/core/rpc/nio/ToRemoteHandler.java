package raft.core.rpc.nio;

import com.google.common.eventbus.EventBus;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raft.core.node.NodeId;

public class ToRemoteHandler extends AbstractHandler {

    private static final Logger logger = LoggerFactory.getLogger(ToRemoteHandler.class);
    private final NodeId selfNodeId;

    // ToRemoteHandler预先知道要连接的远程服务器节点的ID
    ToRemoteHandler(EventBus eventBus, NodeId remoteId, NodeId selfNodeId) {
        super(eventBus);
        this.remoteId = remoteId;
        this.selfNodeId = selfNodeId;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // 连接成功后发送自己的节点的ID
        ctx.write(selfNodeId);
        channel = new NioChannel(ctx.channel());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        logger.debug("receive {} from {}", msg, remoteId);
        super.channelRead(ctx, msg);
    }

}
