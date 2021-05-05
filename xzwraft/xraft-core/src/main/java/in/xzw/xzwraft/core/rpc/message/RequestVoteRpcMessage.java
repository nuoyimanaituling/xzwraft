package in.xzw.xzwraft.core.rpc.message;

import in.xzw.xzwraft.core.node.NodeId;
import in.xzw.xzwraft.core.rpc.Channel;

public class RequestVoteRpcMessage extends AbstractRpcMessage<RequestVoteRpc> {

    public RequestVoteRpcMessage(RequestVoteRpc rpc, NodeId sourceNodeId, Channel channel) {
        super(rpc, sourceNodeId, channel);
    }

}
