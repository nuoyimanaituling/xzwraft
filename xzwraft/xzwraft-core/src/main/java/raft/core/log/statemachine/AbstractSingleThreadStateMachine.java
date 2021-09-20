package raft.core.log.statemachine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raft.core.support.SingleThreadTaskExecutor;
import raft.core.support.TaskExecutor;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * @Author xzw
 * @create 2021/9/19 13:58
 */
public abstract class AbstractSingleThreadStateMachine implements StateMachine {
    private static final Logger logger = LoggerFactory.getLogger(AbstractSingleThreadStateMachine.class);
    private volatile int lastApplied = 0;
    private final TaskExecutor taskExecutor;
    public AbstractSingleThreadStateMachine() {
        taskExecutor = new SingleThreadTaskExecutor("state-machine");
    }

    /**
     * 获取lastApplied
     * @return
     */
    @Override
    public int getLastApplied() {
        return lastApplied;
    }

    /**
     * 应用日志
     * @param context
     * @param index
     * @param commandBytes
     * @param firstLogIndex
     */
    @Override
    public void applyLog(StateMachineContext context, int index,  @Nonnull byte[] commandBytes, int firstLogIndex) {
        taskExecutor.submit(() -> doApplyLog(context, index, commandBytes, firstLogIndex));
    }

    private void doApplyLog(StateMachineContext context,int index,byte[] commandBytes,int firstLogIndex ){
        // 忽略已经应用过的日志
        if (index <= lastApplied){
            return;
        }
        logger.debug("apply log {}", index);
        applyCommand(commandBytes);
        // 跟新lastApplied
        lastApplied = index;

    }

    /**
     * // 应用命令抽象方法
     * @param commandBytes
     */
    protected abstract void applyCommand(@Nonnull byte[] commandBytes);

    @Override
    public void shutdown() {
        try {
            taskExecutor.shutdown();
        } catch (InterruptedException e) {
            throw new StateMachineException(e);
        }
    }




}
