package raft.core.node.role;

import raft.core.node.NodeId;

public abstract class AbstractNodeRole {

    private final RoleName name;
    protected final int term;

    AbstractNodeRole(RoleName name, int term) {
        this.name = name;
        this.term = term;
    }

    public RoleName getName() {
        return name;
    }

    public int getTerm() {
        return term;
    }


    public abstract NodeId getLeaderId(NodeId selfId);

    /**
     * 取消超时或者定时任务
     */
    public abstract void cancelTimeoutOrTask();


}
