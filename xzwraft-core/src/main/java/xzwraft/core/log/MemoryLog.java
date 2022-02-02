package xzwraft.core.log;

import com.google.common.eventbus.EventBus;
import xzwraft.core.log.entry.Entry;
import xzwraft.core.log.entry.EntryMeta;
import xzwraft.core.log.sequence.EntrySequence;
import xzwraft.core.log.sequence.MemoryEntrySequence;
import xzwraft.core.log.snapshot.*;
import xzwraft.core.node.NodeEndpoint;
import xzwraft.core.rpc.message.InstallSnapshotRpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xzwraft.core.log.snapshot.*;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;

@NotThreadSafe
public class MemoryLog extends AbstractLog {

    private static final Logger logger = LoggerFactory.getLogger(MemoryLog.class);

    public MemoryLog() {
        this(new EventBus());
    }

    public MemoryLog(EventBus eventBus) {
        this(new EmptySnapshot(), new MemoryEntrySequence(), eventBus);
    }

    public MemoryLog(Snapshot snapshot, EntrySequence entrySequence, EventBus eventBus) {
        super(eventBus);
        this.snapshot = snapshot;
        this.entrySequence = entrySequence;
    }

    @Override
    protected Snapshot generateSnapshot(EntryMeta lastAppliedEntryMeta, Set<NodeEndpoint> groupConfig) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            stateMachine.generateSnapshot(output);
        } catch (IOException e) {
            throw new LogException("failed to generate snapshot", e);
        }
        return new MemorySnapshot(lastAppliedEntryMeta.getIndex(), lastAppliedEntryMeta.getTerm(), output.toByteArray(), groupConfig);
    }

    @Override
    protected SnapshotBuilder newSnapshotBuilder(InstallSnapshotRpc firstRpc) {
        return new MemorySnapshotBuilder(firstRpc);
    }

    @Override
    protected void replaceSnapshot(Snapshot newSnapshot) {
        int logIndexOffset = newSnapshot.getLastIncludedIndex() + 1;
        EntrySequence newEntrySequence = new MemoryEntrySequence(logIndexOffset);
        List<Entry> remainingEntries = entrySequence.subView(logIndexOffset);
        newEntrySequence.append(remainingEntries);
        logger.debug("snapshot -> {}", newSnapshot);
        snapshot = newSnapshot;
        logger.debug("entry sequence -> {}", newEntrySequence);
        entrySequence = newEntrySequence;
    }

}
