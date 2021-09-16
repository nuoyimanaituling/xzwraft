package raft.core.log;

import com.google.common.eventbus.EventBus;
import raft.core.log.sequence.FileEntrySequence;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

@NotThreadSafe
public class FileLog extends AbstractLog {

    private final RootDir rootDir;

    public FileLog(File baseDir, EventBus eventBus) {
        super(eventBus);
        rootDir = new RootDir(baseDir);

        LogGeneration latestGeneration = rootDir.getLatestGeneration();
        // TODO add log
        if (latestGeneration != null) {

            FileEntrySequence fileEntrySequence = new FileEntrySequence(latestGeneration, latestGeneration.getLastIncludedIndex()+1);

            // TODO apply last group config entry

        } else {
            LogGeneration firstGeneration = rootDir.createFirstGeneration();
            entrySequence = new FileEntrySequence(firstGeneration, 1);
        }
    }

}
