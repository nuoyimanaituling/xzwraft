package raft.core.rpc;

import raft.core.node.NodeEndpoint;
import raft.core.rpc.message.*;

import javax.annotation.Nonnull;
import java.util.Collection;

/**
 * Connector.
 */
public interface Connector {

    /**
     * Initialize connector.
     * <p>
     * SHOULD NOT call more than one.
     * </p>
     */
    void initialize();

//    void sendPreVote(@Nonnull PreVoteRpc rpc, @Nonnull Collection<NodeEndpoint> destinationEndpoints);

//    void replyPreVote(@Nonnull PreVoteResult result, @Nonnull PreVoteRpcMessage rpcMessage);

    /**
     * Send request vote rpc.
     * <p>
     * Remember to exclude self node before sending.
     * </p>
     * <p>
     * Do nothing if destination endpoints is empty.
     * </p>
     *
     * @param rpc                  rpc
     * @param destinationEndpoints destination endpoints
     */
    void sendRequestVote(@Nonnull RequestVoteRpc rpc, @Nonnull Collection<NodeEndpoint> destinationEndpoints);

    /**
     * Reply request vote result.
     *
     * @param result     result
     * @param rpcMessage rpc message
     */
    void replyRequestVote(@Nonnull RequestVoteResult result, @Nonnull RequestVoteRpcMessage rpcMessage);

    /**
     *回复RequestVote给单个节点
     */
    void replyRequestVote(@Nonnull RequestVoteResult result, @Nonnull NodeEndpoint destinationEndpoint);


    /**
     * Send append entries rpc.
     *
     * @param rpc                 rpc
     * @param destinationEndpoint destination endpoint
     */
    void sendAppendEntries(@Nonnull AppendEntriesRpc rpc, @Nonnull NodeEndpoint destinationEndpoint);

    /**
     * 回复appendEntries给单个节点
     */
    void replyAppendEntries(@Nonnull AppendEntriesResult result, @Nonnull NodeEndpoint destinationEndpoint);


    /**
     * Reply append entries result.
     *
     * @param result result
     * @param rpcMessage rpc message
     */
    void replyAppendEntries(@Nonnull AppendEntriesResult result, @Nonnull AppendEntriesRpcMessage rpcMessage);


    /**
     * Called when node becomes leader.
     * <p>
     * Connector may use this chance to close inbound channels.
     * </p>
     */
    void resetChannels();

    /**
     * Close connector.
     */
    void close();

}
