package raft.core.log.statemachine;



import raft.core.node.NodeEndpoint;

import java.io.OutputStream;
import java.util.Set;

/**
 * 补充日志增量快照的时候使用
 */
public interface StateMachineContext {

    @Deprecated
    void generateSnapshot(int lastIncludedIndex);

    OutputStream getOutputForGeneratingSnapshot(int lastIncludedIndex, int lastIncludedTerm, Set<NodeEndpoint> groupConfig) throws Exception;

    void doneGeneratingSnapshot(int lastIncludedIndex) throws Exception;

}
