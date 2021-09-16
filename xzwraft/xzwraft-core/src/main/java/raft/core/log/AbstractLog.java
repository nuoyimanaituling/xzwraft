package raft.core.log;

import com.google.common.eventbus.EventBus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raft.core.log.entry.Entry;
import raft.core.log.entry.EntryMeta;
import raft.core.log.entry.GeneralEntry;
import raft.core.log.entry.NoOpEntry;
import raft.core.log.sequence.EntrySequence;
import raft.core.node.NodeId;
import raft.core.rpc.message.AppendEntriesRpc;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;

abstract class AbstractLog implements Log {

    private static final Logger logger = LoggerFactory.getLogger(AbstractLog.class);

    protected final EventBus eventBus;
    protected EntrySequence entrySequence;
    protected int commitIndex = 0;

    AbstractLog(EventBus eventBus) {
        this.eventBus = eventBus;
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
    public AppendEntriesRpc createAppendEntriesRpc(int term, NodeId selfId, int nextIndex, int maxEntries) {
        int nextLogIndex = entrySequence.getNextLogIndex();
        if (nextIndex > nextLogIndex) {
            throw new IllegalArgumentException("illegal next index " + nextIndex);
        }
        AppendEntriesRpc rpc = new AppendEntriesRpc();
        rpc.setMessageId(UUID.randomUUID().toString());
        rpc.setTerm(term);
        rpc.setLeaderId(selfId);
        rpc.setLeaderCommit(commitIndex);
        // 设置前一条日志的元信息，有可能不存在
        Entry entry  = entrySequence.getEntry(nextIndex-1);
        if (entry != null){
            rpc.setPrevLogTerm(entry.getTerm());
            rpc.setPrevLogIndex(entry.getIndex());
        }
        // 设置最大读取的日志条数，比如如果传输全部日志条目，那么会造成网络拥堵
        if (!entrySequence.isEmpty()) {
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
        // check previous log 检查前一条日志是否匹配
        //// 先检查从leader节点过来的prevLogIndex呵呵prevLogTerm是否匹配本地日志，如果不匹配则返回false，则追加失败
        if (!checkIfPreviousLogMatches(prevLogIndex, prevLogTerm)) {
            return false;
        }
        // heartbeat no-0p
        if (leaderEntries.isEmpty()) {
            return true;
        }
        // 移除冲突的日志条目并返回接下来要追加的日志条目
        assert prevLogIndex + 1 == leaderEntries.get(0).getIndex();
        EntrySequenceView newEntries = removeUnmatchedLog(new EntrySequenceView(leaderEntries));
        // 追加日志
        appendEntriesFromLeader(newEntries);
        return true;
    }

    private void appendEntriesFromLeader(EntrySequenceView leaderEntries) {
        if (leaderEntries.isEmpty()) {
            return;
        }
        logger.debug("append entries from leader from {} to {}", leaderEntries.getFirstLogIndex(), leaderEntries.getLastLogIndex());
        for (Entry leaderEntry : leaderEntries) {
            entrySequence.append(leaderEntry);
        }
    }


    private EntrySequenceView removeUnmatchedLog(EntrySequenceView leaderEntries) {
        // leader节点过来的entries不应该为空
        assert !leaderEntries.isEmpty();
        // 找到第一个不匹配的日志索引
        int firstUnmatched = findFirstUnmatchedLog(leaderEntries);
        if(firstUnmatched < 0){
            return new EntrySequenceView(Collections.emptyList());
        }
        // 移除不匹配的日志索引开始的所有日志
        removeEntriesAfter(firstUnmatched - 1);
        // 追回之后追加的日志条目
        return leaderEntries.subView(firstUnmatched);
    }

    /**
     * 查找第一条不匹配的日志
     * @param leaderEntries
     * @return
     */
    private int findFirstUnmatchedLog(EntrySequenceView leaderEntries) {
        assert !leaderEntries.isEmpty();
        int logIndex;
        EntryMeta followerEntryMeta;
        for (Entry leaderEntry : leaderEntries) {
            logIndex = leaderEntry.getIndex();
            followerEntryMeta = entrySequence.getEntryMeta(logIndex);
            // 找到了第一条不匹配的日志，返回索引
            if (followerEntryMeta == null || followerEntryMeta.getTerm() != leaderEntry.getTerm()) {
                return logIndex;
            }
        }
        // 全部匹配，那么就返回nextIndex，这样subview追加的时候就会为为为空
        return leaderEntries.getLastLogIndex() + 1;
    }

    private boolean checkIfPreviousLogMatches(int prevLogIndex, int prevLogTerm) {
      // 检查指定索引的日志条目
        EntryMeta meta = entrySequence.getEntryMeta(prevLogIndex);
        if(meta ==  null){
            logger.debug("previous log {} not found",prevLogIndex);
            return false;
        }
        int term = meta.getTerm();
        if (term != prevLogTerm){
            logger.debug("different term of previous log local{} ,remote{} ",term ,prevLogTerm);
            return false;
        }
        return true;
    }

    private void removeEntriesAfter(int index) {
        if (entrySequence.isEmpty() || index >= entrySequence.getLastLogIndex()) {
            return;
        }
        logger.debug("remove entries after {}", index);
        entrySequence.removeAfter(index);

    }

    @Override
    public void advanceCommitIndex(int newCommitIndex, int currentTerm) {
        // 推进commitIndex需要检查日志的term是否与当前term一致
        if (!validateNewCommitIndex(newCommitIndex, currentTerm)) {
            return;
        }
        logger.debug("advance commit index from {} to {}", commitIndex, newCommitIndex);
        entrySequence.commit(newCommitIndex);
        commitIndex = newCommitIndex;
//        advanceApplyIndex();
    }

    private boolean isApplicable(Entry entry) {
        return entry.getKind() == Entry.KIND_GENERAL;
    }

    private boolean validateNewCommitIndex(int newCommitIndex, int currentTerm) {
        // 小于当前的commitIndex
        if (newCommitIndex <= commitIndex) {
            return false;
        }
        // 推进的时候检查是不是在文件中
        Entry entry = entrySequence.getEntry(newCommitIndex);
        if (entry == null) {
            logger.debug("log of new commit index {} not found", newCommitIndex);
            return false;
        }
        if (entry.getTerm() != currentTerm) {
            logger.debug("log term of new commit index != current term ({} != {})", entry.getTerm(), currentTerm);
            return false;
        }
        return true;
    }





    @Override
    public void close() {
        entrySequence.close();
    }


    private static class EntrySequenceView implements Iterable<Entry> {

        private final List<Entry> entries;
        private int firstLogIndex;
        private int lastLogIndex;

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
