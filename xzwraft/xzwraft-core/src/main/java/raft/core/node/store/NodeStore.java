package raft.core.node.store;


import raft.core.node.NodeId;

import javax.annotation.Nullable;

/**
 * Node store.
 */
public interface NodeStore {
    /**
     * 每台服务器启动或者关闭时都需要保存的数据
     * currentTerm
     * voteFor
     * P74
     * 否则会出现重复投票情况，保存重启之前的数据
     */

    /**
     * Get term.
     *
     * @return term
     */
    int getTerm();

    /**
     * Set term.
     *
     * @param term term
     */
    void setTerm(int term);

    /**
     * Get voted for.
     *
     * @return voted for
     */
    @Nullable
    NodeId getVotedFor();

    /**
     * Set voted for
     *
     * @param votedFor voted for
     */
    void setVotedFor(@Nullable NodeId votedFor);

    /**
     * Close store.
     */
    void close();

}
