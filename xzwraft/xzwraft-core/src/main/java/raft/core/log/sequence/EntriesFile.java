package raft.core.log.sequence;
import raft.core.log.entry.Entry;
import raft.core.log.entry.EntryFactory;
import raft.core.support.RandomAccessFileAdapter;
import raft.core.support.SeekableFile;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class EntriesFile {

    private final SeekableFile seekableFile;

    public EntriesFile(File file) throws FileNotFoundException {
        this(new RandomAccessFileAdapter(file));
    }

    // 构造函数，普通文件
    public EntriesFile(SeekableFile seekableFile) {
        this.seekableFile = seekableFile;
    }

    // 追加日志条目
    public long appendEntry(Entry entry) throws IOException {
        long offset = seekableFile.size();
        seekableFile.seek(offset);
        seekableFile.writeInt(entry.getKind());
        seekableFile.writeInt(entry.getIndex());
        seekableFile.writeInt(entry.getTerm());
        byte[] commandBytes = entry.getCommandBytes();
        seekableFile.writeInt(commandBytes.length);
        seekableFile.write(commandBytes);
        return offset;
    }
    // 从指定偏移加载日志条目 加载一条
    public Entry loadEntry(long offset, EntryFactory factory) throws IOException {
        if (offset > seekableFile.size()) {
            throw new IllegalArgumentException("offset > size");
        }
        // 根据offset定位到文件具体位置
        seekableFile.seek(offset);
        int kind = seekableFile.readInt();
        int index = seekableFile.readInt();
        int term = seekableFile.readInt();
        int length = seekableFile.readInt();
        byte[] bytes = new byte[length];
        seekableFile.read(bytes);
        return factory.create(kind, index, term, bytes);
    }

    public long size() throws IOException {
        return seekableFile.size();
    }

    public void clear() throws IOException {
        truncate(0L);
    }

    public void truncate(long offset) throws IOException {
        seekableFile.truncate(offset);
    }

    public void close() throws IOException {
        seekableFile.close();
    }

}
