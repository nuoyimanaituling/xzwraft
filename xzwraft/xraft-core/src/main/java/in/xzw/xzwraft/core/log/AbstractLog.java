package in.xzw.xzwraft.core.log;

import com.google.common.eventbus.EventBus;
import in.xzw.xzwraft.core.log.entry.*;
import in.xzw.xzwraft.core.log.sequence.EntrySequence;
import in.xzw.xzwraft.core.log.sequence.GroupConfigEntryList;
import in.xzw.xzwraft.core.log.statemachine.EmptyStateMachine;
import in.xzw.xzwraft.core.log.statemachine.StateMachine;
import in.xzw.xzwraft.core.log.statemachine.StateMachineContext;

import in.xzw.xzwraft.core.node.NodeEndpoint;
import in.xzw.xzwraft.core.node.NodeId;
import in.xzw.xzwraft.core.rpc.message.AppendEntriesRpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.annotation.Nonnull;

import java.util.*;

abstract class AbstractLog implements Log {

    private static final Logger logger = LoggerFactory.getLogger(AbstractLog.class);

    protected final EventBus eventBus;

    protected EntrySequence entrySequence;

    protected GroupConfigEntryList groupConfigEntryList;


    protected int commitIndex = 0;

    private StateMachineContext stateMachineContext;
    protected StateMachine stateMachine = new EmptyStateMachine();

    AbstractLog(EventBus eventBus) {
        this.eventBus = eventBus;
    }


    void setStateMachineContext(StateMachineContext stateMachineContext) {
        this.stateMachineContext = stateMachineContext;
    }

    @Override
    public void setStateMachine(StateMachine stateMachine) {
        this.stateMachine = stateMachine;
    }

    @Override
    public StateMachine getStateMachine() {
        return this.stateMachine;
    }


    @Override
    @Nonnull
    public EntryMeta getLastEntryMeta() {
        if (entrySequence.isEmpty()) {
            return new EntryMeta(Entry.KIND_NO_OP, 0, 0);
        }
        return entrySequence.getLastEntry().getMeta();
    }

    @Override
    public Set<NodeEndpoint> getLastGroup() {
        return groupConfigEntryList.getLastGroup();
    }


    @Override
    public AppendEntriesRpc createAppendEntriesRpc(int term, NodeId selfId, int nextIndex, int maxEntries) {
        // maxentries表示最大传输的日志条目数
        int nextLogIndex = entrySequence.getNextLogIndex();
        if (nextIndex > nextLogIndex) {
            throw new IllegalArgumentException("illegal next index " + nextIndex);
        }
        AppendEntriesRpc rpc = new AppendEntriesRpc();
        rpc.setTerm(term);
        rpc.setLeaderId(selfId);
        rpc.setLeaderCommit(commitIndex);
        // 设置前一条日志的元信息，有可能不存在
        Entry entry =entrySequence.getEntry(nextIndex-1);
        if (entry != null) {
            rpc.setPrevLogIndex(entry.getIndex());
            rpc.setPrevLogTerm(entry.getTerm());
        }
        // 设置rpc消息中的entries
        if (!entrySequence.isEmpty()){
            int maxIndex = (maxEntries == ALL_ENTRIES ? nextLogIndex : Math.min(nextLogIndex, nextIndex + maxEntries));
            rpc.setEntries(entrySequence.subList(nextIndex, maxIndex));
        }
        return rpc;

    }



    @Override
    public int getNextIndex() {
        return entrySequence.getNextLogIndex();
    }

    @Override
    public int getCommitIndex() {
        return commitIndex;
    }

    @Override
    public boolean isNewerThan(int lastLogIndex, int lastLogTerm) {
        EntryMeta lastEntryMeta = getLastEntryMeta();
        logger.debug("last entry ({}, {}), candidate ({}, {})", lastEntryMeta.getIndex(), lastEntryMeta.getTerm(), lastLogIndex, lastLogTerm);
        return lastEntryMeta.getTerm() > lastLogTerm || lastEntryMeta.getIndex() > lastLogIndex;
    }

    @Override
    public NoOpEntry appendEntry(int term) {
        NoOpEntry entry = new NoOpEntry(entrySequence.getNextLogIndex(), term);
        entrySequence.append(entry);
        return entry;
    }

    @Override
    public GeneralEntry appendEntry(int term, byte[] command) {
        GeneralEntry entry = new GeneralEntry(entrySequence.getNextLogIndex(), term, command);
        entrySequence.append(entry);
        return entry;
    }


    @Override
    public boolean appendEntriesFromLeader(int prevLogIndex, int prevLogTerm, List<Entry> leaderEntries) {
        // 先检查从leader节点传过来的prelogindex，prelogterm是否匹配本地日志，如果不匹配，则返回false
        if (!checkIfPreviousLogMatches(prevLogIndex, prevLogTerm)) {
            return false;
        }
        // leader节点传递过来的日志条目为空
        if (leaderEntries.isEmpty()) {
            return true;
        }
        // 移除冲突的日志条目并返回接下来要追加的日志条目
        EntrySequenceView newEntries =removeUnmatchedLog(new EntrySequenceView(leaderEntries));
        appendEntriesFromLeader(newEntries);
        return true;
    }

    private void appendEntriesFromLeader(EntrySequenceView leaderEntries) {
        if (leaderEntries.isEmpty()) {
            return ;
        }
        logger.debug("append entries from leader from {} to {}", leaderEntries.getFirstLogIndex(), leaderEntries.getLastLogIndex());
        for (Entry leaderEntry : leaderEntries) {
            entrySequence.append(leaderEntry);
        }

    }

    @Override
    public void advanceCommitIndex(int newCommitIndex, int currentTerm) {
        if (!validateNewCommitIndex(newCommitIndex, currentTerm)) {
            return ;
        }
        logger.debug("advance commit index from {} to {}", commitIndex, newCommitIndex);
        entrySequence.commit(newCommitIndex);

//        advanceApplyIndex();

    }


    private boolean checkIfPreviousLogMatches(int prevLogIndex, int prevLogTerm) {
       // 检查指定索引的日志目录
        EntryMeta meta = entrySequence.getEntryMeta(prevLogIndex);
        // 如果日志不存在的话
        if (meta == null) {
            logger.debug("previous log {} not found", prevLogIndex);
            return false;
        }
        // 检查当前任期
        int term = meta.getTerm();
        if (term != prevLogTerm) {
            logger.debug("different term of previous log, local {}, remote {}", term, prevLogTerm);
            return false;
        }
        return true;
    }


    private EntrySequenceView removeUnmatchedLog(EntrySequenceView leaderEntries) {
        assert !leaderEntries.isEmpty();
        // 找到第一个不匹配的日志索引
        int firstUnmatched = findFirstUnmatchedLog(leaderEntries);
        if(firstUnmatched < 0) {
            return new EntrySequenceView(Collections.emptyList());
        }
        removeEntriesAfter(firstUnmatched - 1);
        return leaderEntries.subView(firstUnmatched);
    }


    private int findFirstUnmatchedLog(EntrySequenceView leaderEntries) {

        int logIndex;
        EntryMeta followerEntryMeta;
        for (Entry leaderEntry : leaderEntries) {
            logIndex = leaderEntry.getIndex();
            followerEntryMeta = entrySequence.getEntryMeta(logIndex);
            // 日志不存在或者term不一致
            if (followerEntryMeta == null || followerEntryMeta.getTerm() != leaderEntry.getTerm()) {
                return logIndex;
            }
        }
        // 否则没有不一致的日志条目
        return -1;
    }


    private void removeEntriesAfter(int index) {
        if (entrySequence.isEmpty() || index >= entrySequence.getLastLogIndex()) {
            return ;
        }

        logger.debug("remove entries after {}", index);
        entrySequence.removeAfter(index);
//        if (index < commitIndex) {
//            commitIndex = index;
//        }
    }


    private boolean isApplicable(Entry entry) {
        return entry.getKind() == Entry.KIND_GENERAL;
    }
    // 检查新的commitIndex
    private boolean validateNewCommitIndex(int newCommitIndex, int currentTerm) {

        // 小于当前的commitIndex
        if (newCommitIndex <= commitIndex) {
            return false;
        }
        EntryMeta meta = entrySequence.getEntryMeta(newCommitIndex);
        if (meta == null) {
            logger.debug("log of new commit index {} not found", newCommitIndex);
            return false;
        }
        if (meta.getTerm() != currentTerm) {
            logger.debug("log term of new commit index != current term ({} != {})", meta.getTerm(), currentTerm);
            return false;
        }
        return true;
    }

    @Override
    public void close() {
        entrySequence.close();
        stateMachine.shutdown();

    }

    private static class EntrySequenceView implements Iterable<Entry> {
//        static final EntrySequenceView EMPTY = new EntrySequenceView(Collections.emptyList());

        private final List<Entry> entries;
        private int firstLogIndex= -1;
        private int lastLogIndex =-1;

        EntrySequenceView(List<Entry> entries) {
            this.entries = entries;
            if (!entries.isEmpty()) {
                firstLogIndex = entries.get(0).getIndex();
                lastLogIndex = entries.get(entries.size() - 1).getIndex();
            }
        }

        Entry get(int index) {
            if (entries.isEmpty() || index < firstLogIndex || index > lastLogIndex) {
                return null;
            }
            return entries.get(index - firstLogIndex);
        }

        boolean isEmpty() {
            return entries.isEmpty();
        }

        int getFirstLogIndex() {
            return firstLogIndex;
        }

        int getLastLogIndex() {
            return lastLogIndex;
        }

        EntrySequenceView subView(int fromIndex) {
            if (entries.isEmpty() || fromIndex > lastLogIndex) {
                return new EntrySequenceView(Collections.emptyList());
            }
            return new EntrySequenceView(
                    entries.subList(fromIndex - firstLogIndex, entries.size())
            );
        }

        @Override
        @Nonnull
        public Iterator<Entry> iterator() {
            return entries.iterator();
        }

    }

}
