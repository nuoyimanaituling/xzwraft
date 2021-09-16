package raft.core.rpc.message;


import raft.core.node.NodeId;
import raft.core.rpc.Channel;

public abstract class AbstractRpcMessage<T> {

    private final T rpc;
    private final NodeId sourceNodeId;
    private final Channel channel;

    AbstractRpcMessage(T rpc, NodeId sourceNodeId, Channel channel) {
        this.rpc = rpc;
        this.sourceNodeId = sourceNodeId;
        this.channel = channel;
    }

//    AbstractRpcMessage(T rpc, NodeId sourceNodeId) {
//        this.rpc = rpc;
//        this.sourceNodeId = sourceNodeId;
//    }

    public T get() {
        return this.rpc;
    }

    public NodeId getSourceNodeId() {
        return sourceNodeId;
    }

    public Channel getChannel() {
        return channel;
    }

}
