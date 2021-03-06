package raft.core.log;

import com.google.common.eventbus.EventBus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raft.core.log.entry.Entry;
import raft.core.log.entry.EntryMeta;
import raft.core.log.entry.GeneralEntry;
import raft.core.log.entry.NoOpEntry;
import raft.core.log.sequence.EntrySequence;
import raft.core.log.statemachine.AbstractSingleThreadStateMachine;
import raft.core.log.statemachine.EmptyStateMachine;
import raft.core.log.statemachine.StateMachine;
import raft.core.log.statemachine.StateMachineContext;
import raft.core.node.NodeEndpoint;
import raft.core.node.NodeId;
import raft.core.rpc.message.AppendEntriesRpc;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;

abstract class AbstractLog implements Log {

    private static final Logger logger = LoggerFactory.getLogger(AbstractLog.class);

    protected final EventBus eventBus;
    protected EntrySequence entrySequence;
    protected StateMachine stateMachine = new EmptyStateMachine();
    protected int commitIndex = 0;
    private StateMachineContext stateMachineContext;
    AbstractLog(EventBus eventBus) {
        this.eventBus = eventBus;
    }


    void setStateMachineContext(StateMachineContext stateMachineContext) {
        this.stateMachineContext = stateMachineContext;
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
        // ??????????????????????????????????????????????????????
        Entry entry  = entrySequence.getEntry(nextIndex-1);
        if (entry != null){
            rpc.setPrevLogTerm(entry.getTerm());
            rpc.setPrevLogIndex(entry.getIndex());
        }
        // ??????????????????????????????????????????????????????????????????????????????????????????????????????
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
        stateMachine.applyLog(null,entry.getIndex(),entry.getCommandBytes(),0);
        return entry;
    }

    @Override
    public boolean appendEntriesFromLeader(int prevLogIndex, int prevLogTerm, List<Entry> leaderEntries) {
        // check previous log ?????????????????????????????????
        //// ????????????leader???????????????prevLogIndex???prevLogTerm???????????????????????????????????????????????????false??????????????????
        // todo???????????????bug??????????????????????????????????????????????????????????????????false
//        if (!checkIfPreviousLogMatches(prevLogIndex, prevLogTerm)) {
//            return false;
//        }
        // heartbeat no-0p
        if (leaderEntries.isEmpty()) {
            return true;
        }
        // ?????????????????????????????????????????????????????????????????????
        assert prevLogIndex + 1 == leaderEntries.get(0).getIndex();
        EntrySequenceView newEntries = removeUnmatchedLog(new EntrySequenceView(leaderEntries));
        // ????????????
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
        // leader???????????????entries???????????????
        assert !leaderEntries.isEmpty();
        // ???????????????????????????????????????
        int firstUnmatched = findFirstUnmatchedLog(leaderEntries);
        if(firstUnmatched < 0){
            return new EntrySequenceView(Collections.emptyList());
        }
        // ???????????????????????????????????????????????????
        removeEntriesAfter(firstUnmatched - 1);
        // ?????????????????????????????????
        return leaderEntries.subView(firstUnmatched);
    }

    /**
     * ?????????????????????????????????
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
            // ???????????????????????????????????????????????????
            if (followerEntryMeta == null || followerEntryMeta.getTerm() != leaderEntry.getTerm()) {
                return logIndex;
            }
        }
        // ??????????????????????????????nextIndex?????????subview?????????????????????????????????
        return leaderEntries.getLastLogIndex() + 1;
    }

    private boolean checkIfPreviousLogMatches(int prevLogIndex, int prevLogTerm) {
      // ?????????????????????????????????
        EntryMeta meta = entrySequence.getEntryMeta(prevLogIndex);
        if(meta ==  null){
            logger.debug("previous log {} not found",prevLogIndex);
            return false;
        }
        int term = meta.getTerm();
        if (term != prevLogTerm){
            logger.debug("different term of previous log local{} ,remote{}",term ,prevLogTerm);
            return false;
        }
        return true;
    }

    private void removeEntriesAfter(int index) {
        if (entrySequence.isEmpty() || index >= entrySequence.getLastLogIndex()) {
            return;
        }
        entrySequence.subList(entrySequence.getFirstLogIndex(), index + 1).forEach(this::applyEntry);
        logger.debug("remove entries after {}", index);
        entrySequence.removeAfter(index);

    }

    private void applyEntry(Entry entry) {
        // skip no-op entry and membership-change entry
        if (isApplicable(entry)) {
            stateMachine.applyLog(stateMachineContext, entry.getIndex(), entry.getCommandBytes(), entrySequence.getFirstLogIndex());
        }
    }


    @Override
    public void advanceCommitIndex(int newCommitIndex, int currentTerm) {
        // ??????commitIndex?????????????????????term???????????????term??????
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
        // ???????????????commitIndex
        if (newCommitIndex <= commitIndex) {
            return false;
        }
        // ??????????????????????????????????????????
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
