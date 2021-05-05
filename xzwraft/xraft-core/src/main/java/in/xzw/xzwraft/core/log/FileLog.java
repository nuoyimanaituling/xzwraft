package in.xzw.xzwraft.core.log;

import com.google.common.eventbus.EventBus;

import in.xzw.xzwraft.core.log.sequence.FileEntrySequence;

import in.xzw.xzwraft.core.node.NodeEndpoint;


import javax.annotation.concurrent.NotThreadSafe;
import java.io.File;

import java.util.Set;

@NotThreadSafe
public class FileLog extends AbstractLog {

    private final RootDir rootDir;

    public FileLog(File baseDir,EventBus eventBus,Set<NodeEndpoint> baseGroup) {
        super(eventBus);
        rootDir = new RootDir(baseDir);
        LogGeneration latestGeneration = rootDir.getLatestGeneration();
        // TODO add log
        if (latestGeneration != null) {
            Set<NodeEndpoint> initialGroup = baseGroup;
            // 日志存在
           entrySequence =new FileEntrySequence(latestGeneration,latestGeneration.getLastIncludedIndex());
            }
         else {
             // 日志不存在
            LogGeneration firstGeneration = rootDir.createFirstGeneration();
            entrySequence = new FileEntrySequence(firstGeneration, 1);
        }
    }


//    private class StateMachineContextImpl implements StateMachineContext {
//
//        private FileSnapshotWriter snapshotWriter = null;
//
//        @Override
//        public void generateSnapshot(int lastIncludedIndex) {
//            throw new UnsupportedOperationException();
//        }
//
//        public OutputStream getOutputForGeneratingSnapshot(int lastIncludedIndex, int lastIncludedTerm, Set<NodeEndpoint> groupConfig) throws Exception {
//            if (snapshotWriter != null) {
//                snapshotWriter.close();
//            }
//            snapshotWriter = new FileSnapshotWriter(rootDir.getLogDirForGenerating().getSnapshotFile(), lastIncludedIndex, lastIncludedTerm, groupConfig);
//            return snapshotWriter.getOutput();
//        }
//
//        public void doneGeneratingSnapshot(int lastIncludedIndex) throws Exception {
//            if (snapshotWriter == null) {
//                throw new IllegalStateException("snapshot not created");
//            }
//            snapshotWriter.close();
//            eventBus.post(new SnapshotGeneratedEvent(lastIncludedIndex));
//        }
//    }
}
