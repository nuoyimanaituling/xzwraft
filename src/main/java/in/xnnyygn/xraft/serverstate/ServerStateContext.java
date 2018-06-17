package in.xnnyygn.xraft.serverstate;

import in.xnnyygn.xraft.schedule.ElectionTimeoutScheduler;
import in.xnnyygn.xraft.schedule.LogReplicationTask;
import in.xnnyygn.xraft.messages.RaftMessage;
import in.xnnyygn.xraft.server.ServerId;

// TODO rename to a better one
public interface ServerStateContext extends ElectionTimeoutScheduler {

    ServerId getSelfNodeId();

    int getNodeCount();

    void setNodeState(AbstractServerState nodeState);

    LogReplicationTask scheduleLogReplicationTask();

    void sendRpcOrResultMessage(RaftMessage message);

}
