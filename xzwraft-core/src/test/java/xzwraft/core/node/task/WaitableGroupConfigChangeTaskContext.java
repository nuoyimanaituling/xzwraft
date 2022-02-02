package xzwraft.core.node.task;

import xzwraft.core.node.NodeEndpoint;
import xzwraft.core.node.NodeId;

class WaitableGroupConfigChangeTaskContext implements GroupConfigChangeTaskContext {

    private boolean logAppended = false;

    @Override
    public synchronized void addNode(NodeEndpoint endpoint, int nextIndex, int matchIndex) {
        logAppended = true;
        notify();
    }

    @Override
    public synchronized void downgradeNode(NodeId nodeId) {
        logAppended = true;
        notify();
    }

    @Override
    public void removeNode(NodeId nodeId) {
    }

    @Override
    public void done() {
    }

    synchronized void awaitLogAppended() throws InterruptedException {
        if(!logAppended) {
            wait();
        }
        logAppended = true;
    }

}
