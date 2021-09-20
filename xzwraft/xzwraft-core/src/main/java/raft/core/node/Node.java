package raft.core.node;


import raft.core.log.statemachine.StateMachine;
import raft.core.node.role.RoleNameAndLeaderId;

import javax.annotation.Nonnull;

/**
 * Node.
 */
public interface Node {
    // node接口作为暴露给上层服务的接口



    /**
     * Start node.
     */
    void start();

    /**
     * Append log.
     *
     * @param commandBytes command bytes
     * @throws       */
    void appendLog(@Nonnull byte[] commandBytes);

//    void enqueueReadIndex(@Nonnull String requestId);


    /**
     * Stop node.
     *
     * @throws InterruptedException if interrupted
     */
    void stop() throws InterruptedException;

    void registerStateMachine(StateMachine stateMachine);


     @Nonnull
     RoleNameAndLeaderId getRoleNameAndLeaderId() ;

}
