package xzwraft.core.log.sequence;

import xzwraft.core.log.entry.Entry;
import xzwraft.core.log.entry.EntryMeta;

import java.util.List;

public interface EntrySequence {

    boolean isEmpty();

    int getFirstLogIndex();

    int getLastLogIndex();

    int getNextLogIndex();

    List<Entry> subView(int fromIndex);

    // [fromIndex, toIndex)
    List<Entry> subList(int fromIndex, int toIndex);

    GroupConfigEntryList buildGroupConfigEntryList();

    boolean isEntryPresent(int index);

    EntryMeta getEntryMeta(int index);

    Entry getEntry(int index);

    Entry getLastEntry();

    void append(Entry entry);

    void append(List<Entry> entries);

    void commit(int index);

    int getCommitIndex();

    void removeAfter(int index);

    void close();

}
