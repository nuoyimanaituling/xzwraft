package raft.core.log.sequence;

import raft.core.log.entry.Entry;
import raft.core.log.entry.EntryMeta;
import java.util.Collections;
import java.util.List;

/**
 * 具体的范围增加，查询，删除都是由子类去实现的
 */
abstract class AbstractEntrySequence implements EntrySequence {

    /**
     * 日志索引偏移
     */
    int logIndexOffset;
    /**
     * 下一条日志的索引
     */
    int nextLogIndex;

    AbstractEntrySequence(int logIndexOffset) {
        this.logIndexOffset = logIndexOffset;
        this.nextLogIndex = logIndexOffset;
    }
    /**
     * 当日志索引偏移与下一条日志的索引相等，则代表当前日志条目序列为空
     * @return
     */
    @Override
    public boolean isEmpty() {
        return logIndexOffset == nextLogIndex;
    }

    @Override
    public int getFirstLogIndex() {
        if (isEmpty()) {
            throw new EmptySequenceException();
        }
        return doGetFirstLogIndex();
    }

    int doGetFirstLogIndex() {
        return logIndexOffset;
    }

    @Override
    public int getLastLogIndex() {
        if (isEmpty()) {
            throw new EmptySequenceException();
        }
        return doGetLastLogIndex();
    }

    int doGetLastLogIndex() {
        return nextLogIndex - 1;
    }

    @Override
    public boolean isEntryPresent(int index) {
        return !isEmpty() && index >= doGetFirstLogIndex() && index <= doGetLastLogIndex();
    }

    @Override
    public Entry getEntry(int index) {
        if (!isEntryPresent(index)) {
            return null;
        }
        return doGetEntry(index);
    }

    @Override
    public EntryMeta getEntryMeta(int index) {
        Entry entry = getEntry(index);
        return entry != null ? entry.getMeta() : null;
    }


    /**
     * 抽象方法子类实现
     * @param index
     * @return
     */
    protected abstract Entry doGetEntry(int index);

    @Override
    public Entry getLastEntry() {
        return isEmpty() ? null : doGetEntry(doGetLastLogIndex());
    }

    @Override
    public List<Entry> subList(int fromIndex) {
        if (isEmpty() || fromIndex > doGetLastLogIndex()) {
            return Collections.emptyList();
        }
        return subList(Math.max(fromIndex, doGetFirstLogIndex()), nextLogIndex);
    }

    // [fromIndex, toIndex)
    @Override
    public List<Entry> subList(int fromIndex, int toIndex) {
        if (isEmpty()) {
            throw new EmptySequenceException();
        }
        if (fromIndex < doGetFirstLogIndex() || toIndex > doGetLastLogIndex() + 1 || fromIndex > toIndex) {
            throw new IllegalArgumentException("illegal from index " + fromIndex + " or to index " + toIndex);
        }
        return doSubList(fromIndex, toIndex);
    }

    /**
     * 子类实现范围查找
     * @param fromIndex
     * @param toIndex
     * @return
     */
    protected abstract List<Entry> doSubList(int fromIndex, int toIndex);

    @Override
    public int getNextLogIndex() {
        return nextLogIndex;
    }

    @Override
    public void append(List<Entry> entries) {
        for (Entry entry : entries) {
            append(entry);
        }
    }

    @Override
    public void append(Entry entry) {
        if (entry.getIndex() != nextLogIndex) {
            throw new IllegalArgumentException("entry index must be " + nextLogIndex);
        }
        doAppend(entry);
        nextLogIndex++;
    }

    /**
     * 由子类进行entry增加
     * @param entry
     */
    protected abstract void doAppend(Entry entry);


    @Override
    public void removeAfter(int index) {
        if (isEmpty() || index >= doGetLastLogIndex()) {
            return;
        }
        doRemoveAfter(index);
    }

    /**
     * @param index 具体删除index
     */
    protected abstract void doRemoveAfter(int index);

}
