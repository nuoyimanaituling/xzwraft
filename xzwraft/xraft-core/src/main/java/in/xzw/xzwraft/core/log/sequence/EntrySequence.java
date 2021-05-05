package in.xzw.xzwraft.core.log.sequence;

import in.xzw.xzwraft.core.log.entry.Entry;
import in.xzw.xzwraft.core.log.entry.EntryMeta;

import java.util.List;


//日志条目序列
public interface EntrySequence {

    boolean isEmpty();

    int getFirstLogIndex();

    int getLastLogIndex();

    int getNextLogIndex();

    List<Entry> subView(int fromIndex);

    // [fromIndex, toIndex)
    List<Entry> subList(int fromIndex, int toIndex);
   // 检查某个日志条目是否存在
    boolean isEntryPresent(int index);
   // 回去某个日志条目的元信息
    EntryMeta getEntryMeta(int index);

    Entry getEntry(int index);

    Entry getLastEntry();
   // 追加日志条目
    void append(Entry entry);
    // 追加多条日志
    void append(List<Entry> entries);
    // 推进commitIndex
    void commit(int index);
   // 获取当前commitIndex
    int getCommitIndex();
    // 移除某个索引之后的日志条目
    void removeAfter(int index);
  //  关闭日志序列
    void close();
}
