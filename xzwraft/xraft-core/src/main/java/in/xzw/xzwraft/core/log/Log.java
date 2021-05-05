package in.xzw.xzwraft.core.log;

import in.xzw.xzwraft.core.log.entry.Entry;
import in.xzw.xzwraft.core.log.entry.EntryMeta;
import in.xzw.xzwraft.core.log.entry.GeneralEntry;
import in.xzw.xzwraft.core.log.entry.NoOpEntry;
import in.xzw.xzwraft.core.log.statemachine.StateMachine;
import in.xzw.xzwraft.core.node.NodeEndpoint;
import in.xzw.xzwraft.core.node.NodeId;
import in.xzw.xzwraft.core.rpc.message.AppendEntriesRpc;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Set;

public interface Log {

    int ALL_ENTRIES = -1;

    @Nonnull
    // 选举开始，发送消息时候使用
    EntryMeta getLastEntryMeta();
   //  创建复制消息时使用
    AppendEntriesRpc createAppendEntriesRpc(int term, NodeId selfId, int nextIndex, int maxEntries);
    // 获取下一条日志的索引，节点成为leader服务器时使用，重置其他服务器到的nextlogindex为当前服务器下一条日志的索引

    int getNextIndex();
    // 获取当前的commitindex
    int getCommitIndex();

    // 判断对象的lastLogIndex与lastLogTerm是否比自己新,即选择是否投票时使用的方法
    boolean isNewerThan(int lastLogIndex, int lastLogTerm);

    // 增加一个NO-OP日志
    NoOpEntry appendEntry(int term);
    // 增加一条普通日志
    GeneralEntry appendEntry(int term,byte[] command);
     // 追加从leader服务器过来的日志条目序列
    boolean appendEntriesFromLeader(int prevLogIndex, int prevLogTerm, List<Entry> entries);
    // 推进commitindex
    void advanceCommitIndex(int newCommitIndex, int currentTerm);
    //关闭
    void close();
    void setStateMachine(StateMachine stateMachine);

    StateMachine getStateMachine();
   Set<NodeEndpoint> getLastGroup();





}
