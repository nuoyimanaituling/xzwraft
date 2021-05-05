package in.xzw.xzwraft.core.log.statemachine;



import in.xzw.xzwraft.core.support.SingleThreadTaskExecutor;
import in.xzw.xzwraft.core.support.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public abstract class AbstractSingleThreadStateMachine implements StateMachine {

    private static final Logger logger = LoggerFactory.getLogger(AbstractSingleThreadStateMachine.class);
    private volatile int lastApplied = 0;
    private final TaskExecutor taskExecutor;
    private final SortedMap<Integer, List<String>> readIndexMap = new TreeMap<>();

    public AbstractSingleThreadStateMachine() {
        taskExecutor = new SingleThreadTaskExecutor("state-machine");
    }

    @Override
    public int getLastApplied() {
        return lastApplied;
    }

    @Override
    public void applyLog(StateMachineContext context, int index, @Nonnull byte[] commandBytes, int firstLogIndex) {
        taskExecutor.submit(() -> doApplyLog(context, index, commandBytes, firstLogIndex));
    }

    private void doApplyLog(StateMachineContext context, int index,  @Nonnull byte[] commandBytes, int firstLogIndex) {
        if (index <= lastApplied) {
            return;
        }
        logger.debug("apply log {}", index);
        applyCommand(commandBytes);
        lastApplied = index;

    }

//    @Override
//    public void advanceLastApplied(int index) {
//        taskExecutor.submit(() -> {
//            if (index <= lastApplied) {
//                return;
//            }
//            lastApplied = index;
//            onLastAppliedAdvanced();
//        });
//    }

    private void onLastAppliedAdvanced() {
        logger.debug("last applied index advanced, {}", lastApplied);
        SortedMap<Integer, List<String>> subMap = readIndexMap.headMap(lastApplied + 1);
        for (List<String> requestIds : subMap.values()) {
            for (String requestId : requestIds) {
                onReadIndexReached(requestId);
            }
        }
        subMap.clear();
    }

    /**
     * Should generate or not.
     *
     * @param firstLogIndex first log index in log files, may not be {@code 0}
     * @param lastApplied   last applied log index
     * @return true if should generate, otherwise false
     */
    public abstract boolean shouldGenerateSnapshot(int firstLogIndex, int lastApplied);

    protected abstract void applyCommand(@Nonnull byte[] commandBytes);



    protected abstract void onReadIndexReached(String requestId);



    protected abstract void doApplySnapshot(@Nonnull InputStream input) throws IOException;

    @Override
    public void shutdown() {
        try {
            taskExecutor.shutdown();
        } catch (InterruptedException e) {
            throw new StateMachineException(e);
        }
    }

}
