package raft.kvstore.message;

import raft.core.node.NodeId;

/**
 * 要求客户端重定向时，可以提供leader节点的NodeId
 * 在刚启动时，leaderid尚未确定，非leader节点只能返回null，客户端在leaderId为null时，
 * 可以随机选择剩下的节点中的一个，或者重试
 */
public class Redirect {

    private final String leaderId;

    public Redirect(NodeId leaderId) {
        this(leaderId != null ? leaderId.getValue() : null);
    }

    public Redirect(String leaderId) {
        this.leaderId = leaderId;
    }

    public String getLeaderId() {
        return leaderId;
    }

    @Override
    public String toString() {
        return "Redirect{" + "leaderId=" + leaderId + '}';
    }

}
