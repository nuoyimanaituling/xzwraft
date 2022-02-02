package xzwraft.core.node.task;

import xzwraft.core.node.NodeId;

public class NullGroupConfigChangeTask implements GroupConfigChangeTask {

    @Override
    public boolean isTargetNode(NodeId nodeId) {
        return false;
    }

    @Override
    public void onLogCommitted() {
    }

    @Override
    public GroupConfigChangeTaskResult call() throws Exception {
        return null;
    }

    @Override
    public String toString() {
        return "NullGroupConfigChangeTask{}";
    }

}
