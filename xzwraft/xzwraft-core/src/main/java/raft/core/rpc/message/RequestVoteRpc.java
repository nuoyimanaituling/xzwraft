package raft.core.rpc.message;



import raft.core.node.NodeId;

import java.io.Serializable;

public class RequestVoteRpc implements Serializable {

    /**
     * 选举term
     */
    private int term;
    /**
     * 候选者id，即发送者自己
     */
    private NodeId candidateId;
    /**
     * 候选者最后一条日志的索引序列
     */
    private int lastLogIndex = 0;
    /**
     * 候选者最后一条日志的term
     */
    private int lastLogTerm = 0;

    public int getTerm() {
        return term;
    }

    public void setTerm(int term) {
        this.term = term;
    }

    public NodeId getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(NodeId candidateId) {
        this.candidateId = candidateId;
    }

    public int getLastLogIndex() {
        return lastLogIndex;
    }

    public void setLastLogIndex(int lastLogIndex) {
        this.lastLogIndex = lastLogIndex;
    }

    public int getLastLogTerm() {
        return lastLogTerm;
    }

    public void setLastLogTerm(int lastLogTerm) {
        this.lastLogTerm = lastLogTerm;
    }

    @Override
    public String toString() {
        return "RequestVoteRpc{" +
                "candidateId=" + candidateId +
                ", lastLogIndex=" + lastLogIndex +
                ", lastLogTerm=" + lastLogTerm +
                ", term=" + term +
                '}';
    }

}
