package in.xzw.xzwraft.core.rpc;

import in.xzw.xzwraft.core.rpc.message.*;

import javax.annotation.Nonnull;

/**
 * Channel between nodes.
 */
public interface Channel {

//    void writePreVoteRpc(@Nonnull PreVoteRpc rpc);
//
//    void writePreVoteResult(@Nonnull PreVoteResult result);

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
