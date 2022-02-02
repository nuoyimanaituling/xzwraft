package xzwraft.core.log.statemachine;

public interface StateMachineContext {

    void generateSnapshot(int lastIncludedIndex);

}
