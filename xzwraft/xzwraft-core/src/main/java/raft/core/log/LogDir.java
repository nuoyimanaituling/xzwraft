package raft.core.log;

import java.io.File;

public interface LogDir {

    void initialize();

    boolean exists();

    File getEntriesFile();

    File getEntryOffsetIndexFile();

    File get();

    boolean renameTo(LogDir logDir);

}
