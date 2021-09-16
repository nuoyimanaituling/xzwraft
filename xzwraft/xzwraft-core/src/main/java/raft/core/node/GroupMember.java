package raft.core.node;

/**
 * State of group member.
 *
 * @see ReplicatingState
 */
class GroupMember {


    private final NodeEndpoint endpoint;
    /**
     * 复制状态
     */
    private ReplicatingState replicatingState;
    private boolean major;

    GroupMember(NodeEndpoint endpoint) {
        this(endpoint, null, true);
    }

    GroupMember(NodeEndpoint endpoint, ReplicatingState replicatingState, boolean major) {
        this.endpoint = endpoint;
        this.replicatingState = replicatingState;
        this.major = major;
    }

    NodeEndpoint getEndpoint() {
        return endpoint;
    }

    NodeId getId() {
        return endpoint.getId();
    }

    boolean idEquals(NodeId id) {
        return endpoint.getId().equals(id);
    }

    boolean advanceReplicatingState(int lastEntryIndex) {
        return ensureReplicatingState().advance(lastEntryIndex);
    }

    boolean backOffNextIndex() {
        return ensureReplicatingState().backOffNextIndex();
    }


    void setReplicatingState(ReplicatingState replicatingState) {
        this.replicatingState = replicatingState;
    }

    boolean isReplicationStateSet() {
        return replicatingState != null;
    }

    private ReplicatingState ensureReplicatingState() {
        if (replicatingState == null) {
            throw new IllegalStateException("replication state not set");
        }
        return replicatingState;
    }

    int getNextIndex() {
        return ensureReplicatingState().getNextIndex();
    }

    int getMatchIndex() {
        return ensureReplicatingState().getMatchIndex();
    }

    /**
     * Test if should replicate.
     * <p>
     * Return true if
     * <ol>
     * <li>not replicating</li>
     * <li>replicated but no response in specified timeout</li>
     * </ol>
     * </p>
     *
//     * @param readTimeout read timeout
     * @return true if should, otherwise false
     */
//    boolean shouldReplicate(long readTimeout) {
//        ReplicatingState replicatingState = ensureReplicatingState();
//        return !replicatingState.isReplicating() ||
//                System.currentTimeMillis() - replicatingState.getLastReplicatedAt() >= readTimeout;
//    }

    @Override
    public String toString() {
        return "GroupMember{" +
                "endpoint=" + endpoint +
                ", major=" + major +
                ", replicatingState=" + replicatingState +
                '}';
    }

}
