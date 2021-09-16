package raft.core.log;


import raft.core.log.entry.Entry;
import raft.core.log.entry.EntryMeta;
import raft.core.log.entry.GeneralEntry;
import raft.core.log.entry.NoOpEntry;
import raft.core.node.NodeId;
import raft.core.rpc.message.AppendEntriesRpc;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Set;

/**
 * Log
 */
public interface Log {

    int ALL_ENTRIES = -1;

    /**
     * Get meta of last entry.
     * @return entry meta
     */
    @Nonnull
    EntryMeta getLastEntryMeta();

    /**
     * Create append entries rpc from log.
     * 创建appendEntries消息
     * @param term       current term
     * @param selfId     self node id
     * @param nextIndex  next index
     * @param maxEntries max entries
     * @return append entries rpc
     */
    AppendEntriesRpc createAppendEntriesRpc(int term, NodeId selfId, int nextIndex, int maxEntries);


    /**
     * Get next log index.
     * 获取下一条日志的索引，在节点成为leader节点后使用，重置follow服务器的日志复制进度，此时所有
     * follow服务器的初始nextLogIndex都是当前服务器下一条日志的索引。
     * @return next log index
     */
    int getNextIndex();

    /**
     * Get commit index.
     * 获取当前的commitIndex
     * @return commit index
     */
    int getCommitIndex();

    /**
     * Test if last log self is new than last log of leader.
     * 判断对象的lastLogIndex与lastLogTerm是否比自己新
     * @param lastLogIndex last log index
     * @param lastLogTerm  last log term
     * @return true if last log self is newer than last log of leader, otherwise false
     */
    boolean isNewerThan(int lastLogIndex, int lastLogTerm);

    /**
     * Append a NO-OP log entry.
     * 增加一个no-op日志
     * @param term current term
     * @return no-op entry
     */
    NoOpEntry appendEntry(int term);

    /**
     * Append a general log entry.
     * 增加一条普通日志
     * @param term    current term
     * @param command command in bytes
     * @return general entry
     */
    GeneralEntry appendEntry(int term, byte[] command);

//    /**
//     * Append a log entry for adding node.
//     *
//     * @param term            current term
//     * @param nodeEndpoints   current node configs
//     * @param newNodeEndpoint new node config
//     * @return add node entry
//     */
//    AddNodeEntry appendEntryForAddNode(int term, Set<NodeEndpoint> nodeEndpoints, NodeEndpoint newNodeEndpoint);

//    /**
//     * Append a log entry for removing node.
//     *
//     * @param term          current term
//     * @param nodeEndpoints current node configs
//     * @param nodeToRemove  node to remove
//     * @return remove node entry
//     */
//    RemoveNodeEntry appendEntryForRemoveNode(int term, Set<NodeEndpoint> nodeEndpoints, NodeId nodeToRemove);

    /**
     * Append entries to log.
     * 追加来自leader的日志
     * @param prevLogIndex expected index of previous log entry
     * @param prevLogTerm  expected term of previous log entry
     * @param entries      entries to append
     * @return true if success, false if previous log check failed
     */
//    AppendEntriesState appendEntriesFromLeader(int prevLogIndex, int prevLogTerm, List<Entry> entries);

    boolean appendEntriesFromLeader(int prevLogIndex, int prevLogTerm, List<Entry> entries);

    /**
     * Advance commit index.
     * 推进commitIndex
     * <p>
     * The log entry with new commit index must be the same term as the one in parameter,
     * otherwise commit index will not change.
     * </p>
     *
     * @param newCommitIndex new commit index
     * @param currentTerm    current term
     */
//    List<GroupConfigEntry> advanceCommitIndex(int newCommitIndex, int currentTerm);

      void advanceCommitIndex(int newCommitIndex, int currentTerm);


    /**
     *
     * 上层服务提供的应用日志的回调接口
     * Set state machine.
     * <p>
     * It will be called when
     * <ul>
     * <li>apply the log entry</li>
     * <li>generate snapshot</li>
     * <li>apply snapshot</li>
     * </ul>
     *
     * @param stateMachine state machine
     */
//    void setStateMachine(StateMachine stateMachine);

//    StateMachine getStateMachine();

    /**
     * Close log files.
     */
    void close();

}
