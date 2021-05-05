package in.xzw.xzwraft.core.node;

import com.google.common.eventbus.EventBus;
import in.xzw.xzwraft.core.log.Log;

import in.xzw.xzwraft.core.node.config.NodeConfig;
import in.xzw.xzwraft.core.node.store.NodeStore;
import in.xzw.xzwraft.core.rpc.Connector;
import in.xzw.xzwraft.core.schedule.Scheduler;
import in.xzw.xzwraft.core.support.TaskExecutor;


public class NodeContext {
    private NodeId selfId;
    private Log log;
    private Connector connector;
    private NodeStore store;
    private Scheduler scheduler;
    private EventBus eventBus;
    private TaskExecutor taskExecutor;
    private NodeGroup group;
    private NodeConfig config;

    public NodeId selfId() {
        return selfId;
    }

    public void setSelfId(NodeId selfId) {
        this.selfId = selfId;
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

    public NodeConfig config() {
        return config;
    }

    public NodeStore store() {
        return store;
    }

    public void setStore(NodeStore store) {
        this.store = store;
    }

    public NodeGroup getGroup() {
        return group;
    }

    public void setConfig(NodeConfig config) {
        this.config = config;
    }

    public NodeGroup group() {
        return group;
    }

    public void setGroup(NodeGroup group) {
        this.group = group;
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

    public GroupMember findMember(NodeId nodeId){
        GroupMember member = group.getMember(nodeId);
        if (member == null) {
            throw new IllegalArgumentException("no such node " + nodeId);
        }
        return member;
    }


}
