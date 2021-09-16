package raft.core.log.sequence;

import raft.core.log.entry.Entry;
import raft.core.log.entry.EntryMeta;

import java.util.List;
import java.util.Set;

public interface EntrySequence {

    boolean isEmpty();

    int getFirstLogIndex();

    int getLastLogIndex();

    int getNextLogIndex();

    List<Entry> subList(int fromIndex);

    /**
     * [fromIndex, toIndex)
     * @param fromIndex
     * @param toIndex
     * @return
     */
    List<Entry> subList(int fromIndex, int toIndex);

    boolean isEntryPresent(int index);

    /**
     * 获取某个索引日志条目的元数据
     * @param index
     * @return
     */
    EntryMeta getEntryMeta(int index);

    Entry getEntry(int index);

    Entry getLastEntry();

    /**
     * @param entry 追加一条日志
     */
    void append(Entry entry);

    /**
     * @param entries 追加多条日志条目
     */
    void append(List<Entry> entries);

    /**
     * 推进commitIndex
     * @param index
     */
    void commit(int index);

    /**
     * 获取当前commitIndex
     * @return
     */
    int getCommitIndex();

    /**
     * 移除某个索引之后的日志条目
     * @param index
     */
    void removeAfter(int index);

    /**
     * 关闭日志序列
     */
    void close();

}
