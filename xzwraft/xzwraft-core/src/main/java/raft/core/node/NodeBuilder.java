package raft.core.node;

import com.google.common.base.Preconditions;
import com.google.common.eventbus.EventBus;

import io.netty.channel.nio.NioEventLoopGroup;
import raft.core.log.FileLog;
import raft.core.log.Log;
import raft.core.log.MemoryLog;
import raft.core.node.store.FileNodeStore;
import raft.core.node.store.MemoryNodeStore;
import raft.core.node.store.NodeStore;
import raft.core.rpc.Connector;
import raft.core.rpc.nio.NioConnector;
import raft.core.schedule.DefaultScheduler;
import raft.core.schedule.Scheduler;
import raft.core.support.SingleThreadTaskExecutor;
import raft.core.support.TaskExecutor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executors;

/**
 * Node builder.
 */
public class NodeBuilder {

    /**
     * Group.
     */
    private final NodeGroup group;

    /**
     * Self id.
     */
    private final NodeId selfId;

    /**
     * Event bus, INTERNAL.
     */
    private final EventBus eventBus;


    /**
     * Starts as standby or not.
     */
    private boolean standby = false;

    /**
     * Log.
     * If data directory specified,  will be created.
     */
    private Log log = null;

    /**
     * Store for current term and last node id voted for.
     * If data directory specified, {@link raft.core.node.store.FileNodeStore} will be created.
     * Default to {@link raft.core.node.store.MemoryNodeStore}.
     */
    private NodeStore store = null;

    /**
     * Scheduler, INTERNAL.
     */
    private Scheduler scheduler = null;

    /**
     * Connector, component to communicate between nodes, INTERNAL.
     */
    private Connector connector = null;

    /**
     * Task executor for node, INTERNAL.
     */
    private TaskExecutor taskExecutor = null;

    /**
     * Task executor for group config change task, INTERNAL.
     */
    private TaskExecutor groupConfigChangeTaskExecutor = null;

    /**
     * Event loop group for worker.
     * If specified, reuse. otherwise create one.
     */
    private NioEventLoopGroup workerNioEventLoopGroup = null;

    // TODO add doc
    public NodeBuilder(@Nonnull NodeEndpoint endpoint) {
        this(Collections.singletonList(endpoint), endpoint.getId());
    }

    // TODO add doc
    public NodeBuilder(@Nonnull Collection<NodeEndpoint> endpoints, @Nonnull NodeId selfId) {
        Preconditions.checkNotNull(endpoints);
        Preconditions.checkNotNull(selfId);
        this.group = new NodeGroup(endpoints, selfId);
        this.selfId = selfId;
        this.eventBus = new EventBus(selfId.getValue());

        /**
         * 然后就把您的邮箱给记了下来，给您写这一封邮件的原因是，
         */
    }

    /**
     * Create.
     *
     * @param selfId self id
     * @param group  group
     */
    @Deprecated
    public NodeBuilder(@Nonnull NodeId selfId, @Nonnull NodeGroup group) {
        Preconditions.checkNotNull(selfId);
        Preconditions.checkNotNull(group);
        this.selfId = selfId;
        this.group = group;
        this.eventBus = new EventBus(selfId.getValue());
    }

    /**
     * Set standby.
     *
     * @param standby standby
     * @return this
     */
    public NodeBuilder setStandby(boolean standby) {
        this.standby = standby;
        return this;
    }



    /**
     * Set connector.
     *
     * @param connector connector
     * @return this
     */
   public NodeBuilder setConnector(@Nonnull Connector connector) {
        Preconditions.checkNotNull(connector);
        this.connector = connector;
        return this;
    }

    /**
     * Set event loop for worker.
     * If specified, it's caller's responsibility to close worker event loop.
     *
     * @param workerNioEventLoopGroup worker event loop
     * @return this
     */
    public NodeBuilder setWorkerNioEventLoopGroup(@Nonnull NioEventLoopGroup workerNioEventLoopGroup) {
        Preconditions.checkNotNull(workerNioEventLoopGroup);
        this.workerNioEventLoopGroup = workerNioEventLoopGroup;
        return this;
    }

    /**
     * Set scheduler.
     *
     * @param scheduler scheduler
     * @return this
     */
    public NodeBuilder setScheduler(@Nonnull Scheduler scheduler) {
        Preconditions.checkNotNull(scheduler);
        this.scheduler = scheduler;
        return this;
    }

    /**
     * Set task executor.
     *
     * @param taskExecutor task executor
     * @return this
     */
    public NodeBuilder setTaskExecutor(@Nonnull TaskExecutor taskExecutor) {
        Preconditions.checkNotNull(taskExecutor);
        this.taskExecutor = taskExecutor;
        return this;
    }

    /**
     * Set group config change task executor.
     *
     * @param groupConfigChangeTaskExecutor group config change task executor
     * @return this
     */
    public NodeBuilder setGroupConfigChangeTaskExecutor(@Nonnull TaskExecutor groupConfigChangeTaskExecutor) {
        Preconditions.checkNotNull(groupConfigChangeTaskExecutor);
        this.groupConfigChangeTaskExecutor = groupConfigChangeTaskExecutor;
        return this;
    }

    /**
     * Set store.
     *
     * @param store store
     * @return this
     */
   public NodeBuilder setStore(@Nonnull NodeStore store) {
        Preconditions.checkNotNull(store);
        this.store = store;
        return this;
    }

    /**
     * Set data directory.
     *
     * @param dataDirPath data directory
     * @return this
     */
    public NodeBuilder setDataDir(@Nullable String dataDirPath) {
        if (dataDirPath == null || dataDirPath.isEmpty()) {
            return this;
        }
        File dataDir = new File(dataDirPath);
        if (!dataDir.isDirectory() || !dataDir.exists()) {
            throw new IllegalArgumentException("[" + dataDirPath + "] not a directory, or not exists");
        }
        log = new FileLog(dataDir, eventBus);
        store = new FileNodeStore(new File(dataDir, FileNodeStore.FILE_NAME));
        return this;
    }

    /**
     * Build node.
     *
     * @return node
     */
    @Nonnull
    public Node build() {
        return new NodeImpl(buildContext());
    }

    /**
     * Build context for node.
     *
     * @return node context
     */
    @Nonnull
    private NodeContext buildContext() {
        NodeContext context = new NodeContext();
        context.setGroup(group);
        context.setLog(log != null ? log : new MemoryLog(eventBus));
        context.setStore(store != null ? store : new MemoryNodeStore());
        context.setSelfId(selfId);
        context.setEventBus(eventBus);
        context.setScheduler(scheduler != null ? scheduler : new DefaultScheduler(3000,4000,0,1000));
        context.setConnector(connector != null ? connector : createNioConnector());
        context.setTaskExecutor(taskExecutor != null ? taskExecutor :
                new SingleThreadTaskExecutor("node")
        );
        return context;
    }

    /**
     * Create nio connector.
     *
     * @return nio connector
     */
    @Nonnull
    private NioConnector createNioConnector() {
        int port = group.findSelf().getEndpoint().getPort();
        if (workerNioEventLoopGroup != null) {
            return new NioConnector(workerNioEventLoopGroup, selfId, eventBus, port,1000);
        }
        return new NioConnector(new NioEventLoopGroup(10), false, selfId, eventBus, port,1000);
    }




}
