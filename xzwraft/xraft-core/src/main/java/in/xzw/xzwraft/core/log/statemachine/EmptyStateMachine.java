package in.xzw.xzwraft.core.log.statemachine;




import javax.annotation.Nonnull;

public class EmptyStateMachine implements StateMachine {

    private int lastApplied = 0;

    @Override
    public int getLastApplied() {
        return lastApplied;
    }

    @Override
    public void applyLog(StateMachineContext context, int index, @Nonnull byte[] commandBytes, int firstLogIndex) {
        lastApplied = index;
    }

    @Override
    public void shutdown() {
    }

}
