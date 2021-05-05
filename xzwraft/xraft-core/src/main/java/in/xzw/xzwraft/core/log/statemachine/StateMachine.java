package in.xzw.xzwraft.core.log.statemachine;



import javax.annotation.Nonnull;



// 核心组件负责其中的方法，间接调用kv组件
public interface StateMachine {

    // 获取lastApplied
    int getLastApplied();

    void applyLog(StateMachineContext context, int index,  @Nonnull byte[] commandBytes, int firstLogIndex);

    void shutdown();
}
