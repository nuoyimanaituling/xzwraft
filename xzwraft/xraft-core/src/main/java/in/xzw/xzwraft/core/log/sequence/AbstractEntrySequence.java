package in.xzw.xzwraft.core.log.sequence;

import in.xzw.xzwraft.core.log.entry.Entry;
import in.xzw.xzwraft.core.log.entry.EntryMeta;

import java.util.List;

// 操作日志序列表的索引
abstract class AbstractEntrySequence implements EntrySequence {

    //日志索引偏移
    int logIndexOffset;
    // 下一条日志的索引
    int nextLogIndex;
    AbstractEntrySequence(int logIndexOffset) {
        this.logIndexOffset = logIndexOffset;
        this.nextLogIndex = logIndexOffset;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override

    // 当两者相等，说明日志条目序列为空
    public boolean isEmpty() {
        return logIndexOffset ==nextLogIndex;
    }

    @Override
    // 获取第一条日志的索引
    public int getFirstLogIndex() {
        if (isEmpty()) {
            throw new EmptySequenceException();
        }
        return doGetFirstLogIndex();

    }

    int doGetFirstLogIndex() {
        return logIndexOffset;
    }


    // 获取最后一条日志的索引
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
    public int getNextLogIndex() {
        return nextLogIndex;
    }

    @Override
    public List<Entry> subView(int fromIndex) {
        return null;
    }

    @Override
    public List<Entry> subList(int fromIndex, int toIndex) {
        if (isEmpty()) {
            throw new EmptySequenceException();
        }
        if (fromIndex < doGetFirstLogIndex() || toIndex > doGetLastLogIndex() + 1 || fromIndex > toIndex) {
            throw new IllegalArgumentException("illegal from index " + fromIndex + " or to index " + toIndex);
        }

        return doSubList(fromIndex,toIndex);
    }
    protected abstract List<Entry> doSubList(int fromIndex, int toIndex);

    // 判断索引的日志条目是否存在
    @Override
    public boolean isEntryPresent(int index) {
        return !isEmpty() && index >= doGetFirstLogIndex() && index <= doGetLastLogIndex();
    }

    @Override
    public EntryMeta getEntryMeta(int index) {
        Entry entry =getEntry(index);

        return entry!=null? entry.getMeta():null;
    }
    //获取指定索引的日志条目
    // 没有实现具体的doGetEntry方法由子类去实现
    @Override
    public Entry getEntry(int index) {
        if(!isEntryPresent(index)){
            return null;
        }
        return doGetEntry(index);
    }

    protected abstract Entry doGetEntry(int index);

    @Override
    public Entry getLastEntry() {
        return isEmpty()? null:doGetEntry(doGetLastLogIndex());
    }

    @Override
    // 判断传过来的entry是不是我们想要的nexylogIndex
    public void append(Entry entry) {
        if (entry.getIndex() != nextLogIndex) {
            throw new IllegalArgumentException("entry index must be " + nextLogIndex);
        }
        doAppend(entry);
        // 表示下一条仍待加入的日志序列
        nextLogIndex++;
    }
    protected abstract void doAppend(Entry entry);

    @Override
    public void append(List<Entry> entries) {
        for (Entry entry : entries) {
            append(entry);
        }
    }

    @Override
    public void removeAfter(int index) {
        if (isEmpty() || index >= doGetLastLogIndex()) {
            return;
        }
        doRemoveAfter(index);
    }
    protected abstract void doRemoveAfter(int index);

    @Override
    public void close() {

    }
}
