package in.xzw.xzwraft.core.rpc.message;

import in.xzw.xzwraft.core.node.NodeId;
import in.xzw.xzwraft.core.rpc.Channel;

public class AppendEntriesRpcMessage extends AbstractRpcMessage<AppendEntriesRpc> {

    public AppendEntriesRpcMessage(AppendEntriesRpc rpc, NodeId sourceNodeId, Channel channel) {
        super(rpc, sourceNodeId, channel);
    }

}
