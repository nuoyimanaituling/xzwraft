package xzwraft.core.log.sequence;

import xzwraft.core.log.entry.Entry;
import xzwraft.core.log.entry.GroupConfigEntry;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.ArrayList;
import java.util.List;

@NotThreadSafe
public class MemoryEntrySequence extends AbstractEntrySequence {

    private final List<Entry> entries = new ArrayList<>();

    public MemoryEntrySequence() {
        this(1);
    }

    public MemoryEntrySequence(int logIndexOffset) {
        super(logIndexOffset);
    }

    @Override
    protected List<Entry> doSubList(int fromIndex, int toIndex) {
        return entries.subList(fromIndex - logIndexOffset, toIndex - logIndexOffset);
    }

    @Override
    protected Entry doGetEntry(int index) {
        return entries.get(index - logIndexOffset);
    }

    @Override
    protected void doAppend(Entry entry) {
        entries.add(entry);
    }

    @Override
    public void commit(int index) {
    }

    @Override
    public int getCommitIndex() {
        // TODO implement me
        throw new UnsupportedOperationException();
    }

    @Override
    public GroupConfigEntryList buildGroupConfigEntryList() {
        GroupConfigEntryList list = new GroupConfigEntryList();
        for (Entry entry : entries) {
            if (entry instanceof GroupConfigEntry) {
                list.add((GroupConfigEntry) entry);
            }
        }
        return list;
    }

    @Override
    protected void doRemoveAfter(int index) {
        if (index < doGetFirstLogIndex()) {
            entries.clear();
            nextLogIndex = logIndexOffset;
        } else {
            entries.subList(index - logIndexOffset + 1, entries.size()).clear();
            nextLogIndex = index + 1;
        }
    }

    @Override
    public void close() {
    }

    @Override
    public String toString() {
        return "MemoryEntrySequence{" +
                "logIndexOffset=" + logIndexOffset +
                ", nextLogIndex=" + nextLogIndex +
                ", entries.size=" + entries.size() +
                '}';
    }

}
