package raft.core.rpc.message;



import raft.core.log.entry.Entry;
import raft.core.node.NodeId;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public class AppendEntriesRpc implements Serializable {


    private String messageId;
    /**
     * 选举term
     */
    private int term;
    /**
     * leader节点id
     */
    private NodeId leaderId;
    /**
     * 前一条日志的索引
     */
    private int prevLogIndex = 0;

    /**
     * 前一条日志的term
     */
    private int prevLogTerm;
    /**
     * 复制的日志条目
     */
    private List<Entry> entries = Collections.emptyList();
    /**
     * leader的commitIndex
     */
    private int leaderCommit;


    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public int getTerm() {
        return term;
    }

    public void setTerm(int term) {
        this.term = term;
    }

    public NodeId getLeaderId() {
        return leaderId;
    }

    public void setLeaderId(NodeId leaderId) {
        this.leaderId = leaderId;
    }

    public int getPrevLogIndex() {
        return prevLogIndex;
    }

    public void setPrevLogIndex(int prevLogIndex) {
        this.prevLogIndex = prevLogIndex;
    }

    public int getPrevLogTerm() {
        return prevLogTerm;
    }

    public void setPrevLogTerm(int prevLogTerm) {
        this.prevLogTerm = prevLogTerm;
    }


    public List<Entry> getEntries() {
        return entries;
    }

    public void setEntries(List<Entry> entries) {
        this.entries = entries;
    }

    public int getLeaderCommit() {
        return leaderCommit;
    }

    public void setLeaderCommit(int leaderCommit) {
        this.leaderCommit = leaderCommit;
    }

    public int getLastEntryIndex() {
        return this.entries.isEmpty() ? this.prevLogIndex : this.entries.get(this.entries.size() - 1).getIndex();
    }

    @Override
    public String toString() {
        return "AppendEntriesRpc{" +
                "messageId='" + messageId +
                "', entries.size=" + entries.size() +
                ", leaderCommit=" + leaderCommit +
                ", leaderId=" + leaderId +
                ", prevLogIndex=" + prevLogIndex +
                ", prevLogTerm=" + prevLogTerm +
                ", term=" + term +
                '}';
    }
}
