package raft.core.node;

import com.google.common.eventbus.EventBus;
import raft.core.log.Log;
import raft.core.node.store.NodeStore;
import raft.core.rpc.Connector;
import raft.core.schedule.Scheduler;
import raft.core.support.TaskExecutor;
/**
 * Node context.
 * <p>
 * Node context should not change after initialization. e.g.
 * </p>
 */
public class NodeContext {
    /**
     * 当前节点id
     */
    private NodeId selfId;
    /**
     * 成员列表
     */
    private NodeGroup group;

    private Log log;
    /**
     * Rpc组件
     */
    private Connector connector;
    /**
     * 部分角色状态数据存储
     */
    private NodeStore store;
    /**
     * 定时器组件
     */
    private Scheduler scheduler;


    private EventBus eventBus;
    /**
     * 主线程执行器
     */
    private TaskExecutor taskExecutor;

    public NodeId selfId() {
        return selfId;
    }

    public void setSelfId(NodeId selfId) {
        this.selfId = selfId;
    }

    public NodeGroup group() {
        return group;
    }

    public void setGroup(NodeGroup group) {
        this.group = group;
    }

    public Log log() {
        return log;
    }

    public void setLog(Log log) {
        this.log = log;
    }

    public Connector connector() {
        return connector;
    }

    public void setConnector(Connector connector) {
        this.connector = connector;
    }

    public NodeStore store() {
        return store;
    }

    public void setStore(NodeStore store) {
        this.store = store;
    }

    public Scheduler scheduler() {
        return scheduler;
    }

    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }


    public EventBus eventBus() {
        return eventBus;
    }

    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public TaskExecutor taskExecutor() {
        return taskExecutor;
    }

    public void setTaskExecutor(TaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    public GroupMember findMember(NodeId id){
        return  group.findMember(id);
    }


}
