package raft.core.log.statemachine;



import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;

public class EmptyStateMachine implements StateMachine {

    private int lastApplied = 0;

    @Override
    public int getLastApplied() {
        return lastApplied;
    }

    @Override
    public void applyLog(StateMachineContext context, int index, byte[] commandBytes, int firstLogIndex) {
        lastApplied = index;
    }


    @Override
    public void shutdown() {
    }

}
