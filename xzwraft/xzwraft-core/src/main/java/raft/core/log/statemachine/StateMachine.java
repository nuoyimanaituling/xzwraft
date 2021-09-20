package raft.core.log.statemachine;

import raft.core.log.entry.Entry;

import javax.swing.plaf.nimbus.State;

/**
 *
 * kv组件可以实现核心组件所要求的某个接口，然后把自己注册到核心组件中，核心组件负责调用相应的接口方法，间接调用kv
 * 组件，这个接口就是状态机接口：StateMachine
 *
 * 状态机接口
 * @Author xzw
 * @create 2021/9/19 11:50
 */
public interface StateMachine {


    /**
     * 获取lastApplied
     * @return
     */
    int getLastApplied();

    // 应用日志 applyLog方法是日之子组件回调上层服务的主要方法，提供了状态机上下文，日志的索引，命令的二进制数据
    // 以及第一条日志的索引，上层服务在applyLog方法的实现中应用命令，修改数据，并进行回复客户端等操作
    void applyLog(StateMachineContext context,int index,byte[] commandBytes,int firstLogIndex);

    /**
     * 关闭状态机
     */
    void shutdown();



}
