package xzwraft.core.log.statemachine;

import xzwraft.core.log.snapshot.Snapshot;
import xzwraft.core.node.role.AbstractNodeRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;

public abstract class AbstractDirectStateMachine implements StateMachine {

    private static final Logger logger = LoggerFactory.getLogger(AbstractNodeRole.class);
    protected int lastApplied = 0;

    @Override
    public int getLastApplied() {
        return lastApplied;
    }

    @Override
    public void applyLog(StateMachineContext context, int index, @Nonnull byte[] commandBytes, int firstLogIndex) {
        logger.debug("apply log {}", index);
        applyCommand(commandBytes);
        lastApplied = index;
        if (shouldGenerateSnapshot(firstLogIndex, index)) {
            context.generateSnapshot(index);
        }
    }

    protected abstract void applyCommand(@Nonnull byte[] commandBytes);

    @Override
    public void applySnapshot(@Nonnull Snapshot snapshot) throws IOException {
        logger.info("apply snapshot, last included index {}", snapshot.getLastIncludedIndex());
        doApplySnapshot(snapshot.getDataStream());
        lastApplied = snapshot.getLastIncludedIndex();
    }

    protected abstract void doApplySnapshot(@Nonnull InputStream input) throws IOException;

}
