package raft.core.rpc;

import raft.core.rpc.message.AppendEntriesResult;
import raft.core.rpc.message.AppendEntriesRpc;
import raft.core.rpc.message.RequestVoteResult;
import raft.core.rpc.message.RequestVoteRpc;

import javax.annotation.Nonnull;

/**
 * @Author xzw
 * @create 2021/9/15 19:57
 */
public interface Channel {

    /**
     * Write request vote rpc.
     *
     * @param rpc rpc
     */
    void writeRequestVoteRpc(@Nonnull RequestVoteRpc rpc);

    /**
     * Write request vote result.
     *
     * @param result result
     */
    void writeRequestVoteResult(@Nonnull RequestVoteResult result);

    /**
     * Write append entries rpc.
     *
     * @param rpc rpc
     */
    void writeAppendEntriesRpc(@Nonnull AppendEntriesRpc rpc);

    /**
     * Write append entries result.
     *
     * @param result result
     */
    void writeAppendEntriesResult(@Nonnull AppendEntriesResult result);


    /**
     * Close channel.
     */
    void close();
}
