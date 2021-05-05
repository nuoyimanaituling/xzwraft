package in.xzw.xzwraft.core.log;

import java.io.File;

public interface LogDir {

    void initialize();

    boolean exists();


  // 获取entriesFil对应的文件
    File getEntriesFile();
    // 获取entryindexfile对应文件
    File getEntryOffsetIndexFile();
    // 获取目录
    File get();
    // 重命名目录
    boolean renameTo(LogDir logDir);

}
