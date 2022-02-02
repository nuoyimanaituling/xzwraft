package xzwraft.core.rpc.message;

import xzwraft.core.node.NodeEndpoint;

public class AddServerResult {

    private final GroupConfigChangeStatus status;
    private final NodeEndpoint leaderHint;

    public AddServerResult(GroupConfigChangeStatus status, NodeEndpoint leaderHint) {
        this.status = status;
        this.leaderHint = leaderHint;
    }

    public GroupConfigChangeStatus getStatus() {
        return status;
    }

    public NodeEndpoint getLeaderHint() {
        return leaderHint;
    }

    @Override
    public String toString() {
        return "AddServerResult{" +
                "status=" + status +
                ", leaderHint=" + leaderHint +
                '}';
    }

}
