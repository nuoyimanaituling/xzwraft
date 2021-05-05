

# 领导者选举

有论文得出的需要持久化的数据：

currentTerm：
votedFor：投过票给谁

log[] :日志条目，第一个日志的id为1





# 集群成员信息：

```
集群成员表
NodeGroup
	NodeId nodeid
	Map<NodeId,GroupMember> memberMap
```



```
GroupMember：
	Nodepoint{NodeId，Address}
	ReplicationState：{nextIndex，matchIndex}
	
```

​	

## 角色：

### Leader(具有属性):

```
term
复制进度
日志复制定时器：LogreplicationTask logreplicationtask
```



### follower（具有属性）:

```
term
votedFor（投票给了谁）
leaderId 
选举超时定时器：
	Electiontimeout electionTimeout （选举超时定时器）
```

### candidate（具有属性）:

```
term
votescount（收到的票数）
选举超时定时器:Electiontimeout electionTimeout 
```

两大定时器完成的功能：

```
选举超时定时器：
	新建选举超时
	取消选举超时
	重置选举超时
```

定时器抽象：

```
Scheduler接口 返回一个定时器
 LogReplicaitonTask scheduleLogReplicationTask（Runnable task）
 ElectionTimeout scheduleElectiontimeout（Runnable task）
```

```
DefaultScheduler implements Scheduler
  DefaultScheduler(){
   	 创建一个执行器 scheduledExecutorService
  }
  实现重写方法：
scheduleElectiontimeout（task）{
	
  scheduledFuture =scheduledExecutorService.schedul(task)
  return new ElectionTimeout（scheduledFuture）
}
scheduleLogReplicationTask（task）{

  scheduledFuture=scheduledExecutorService.schedulewithFixedDelay
  return new LogReplicaitonTask(scheduledFuture)
}
```

# 消息模型

```
Candidate调用
RequestVoteRpc
    term	候选人的任期号
    candidateId	请求选票的候选人的 Id
    lastLogIndex	候选人的最后日志条目的索引值
    lastLogTerm	候选人最后日志条目的任期号
```

```
RequestVoteResult
	term	当前任期号，以便于候选人去更新自己的任期号
	voteGranted	候选人赢得了此张选票时为真
```

```
AppendEntriesRpc
领导者调用 用于日志条目的复制 同时也被当做心跳使用
    term	领导者的任期
    leaderId	领导者ID 因此跟随者可以对客户端进行重定向（译者注：跟随者根据领导者id把客户端的请求重定向到领导者，比如有时客户端把请求发给了跟随者而不是领导者）
    prevLogIndex	紧邻新日志条目之前的那个日志条目的索引
    prevLogTerm	紧邻新日志条目之前的那个日志条目的任期
    entries[]	需要被保存的日志条目（被当做心跳使用是 则日志条目内容为空；为了提高效率可能一次性发送多个）
    leaderCommit	领导者的已知已提交的最高的日志条目的索引
```

```
AppendEntriesResult
term	当前任期,对于领导者而言 它会更新自己的任期
success	结果为真 如果跟随者所含有的条目和prevLogIndex以及prevLogTerm匹配上了
```

# 核心组件（调用间接组件访问其他接口）

## 间接层

```
NodeContext 类
private NodeId selfId;
	// 成员列表
    private NodeGroup group;
    private Log log;
    // Rpc接口
    private Connector connector;
    // 部分角色状态数据存储
    private NodeStore store;
    // 定时器组件
    private Scheduler scheduler;
    private EventBus eventBus;
    // 主线程执行器
    private TaskExecutor taskExecutor;

```

## 其他组件

### Rpc接口

```java
void initialize();
    /**
     * Send request vote rpc.
     * <p>
     * Remember to exclude self node before sending.
     * </p>
     * <p>
     * Do nothing if destination endpoints is empty.
     * </p>
     *
     * @param rpc                  rpc
     * @param destinationEndpoints destination endpoints
     */
    void sendRequestVote(@Nonnull RequestVoteRpc rpc, @Nonnull Collection<NodeEndpoint> destinationEndpoints);

    /**
     * Reply request vote result.
     *
     * @param result     result
     * @param rpcMessage rpc message
     */
    void replyRequestVote(@Nonnull RequestVoteResult result, @Nonnull RequestVoteRpcMessage rpcMessage);

    /**
     * Send append entries rpc.
     *
     * @param rpc                 rpc
     * @param destinationEndpoint destination endpoint
     */
    void sendAppendEntries(@Nonnull AppendEntriesRpc rpc, @Nonnull NodeEndpoint destinationEndpoint);

    /**
     * Reply append entries result.
     *
     * @param result result
     * @param rpcMessage rpc message
     */
    void replyAppendEntries(@Nonnull AppendEntriesResult result, @Nonnull AppendEntriesRpcMessage rpcMessage);
     void close（）

```

 **目前没有区分的是：Scheduler与TaskExecutor在模型中起到的作用有什么区别？？？？？？？？？？？？**

## 核心组件

```java
Node
void start();
void stop() throws InterruptedException;
```

```java
 NodeImpl
 private static final Logger logger = LoggerFactory.getLogger(NodeImpl.class);
 private final NodeContext context;  
 private boolean started;
  //当前角色以及信息
 private volatile AbstractNodeRole role;
   @Override
    public synchronized void start() {
        // 判断是否启动
        if (started) {
            return;
        }
        //注册自己到EventBus
        context.eventBus().register(this);
        // 初始化连接器
        context.connector().initialize();
		
        // load term, votedFor from store and become follower
        // 启动时为Follower角色
        NodeStore store = context.store();
        changeToRole(new FollowerNodeRole(store.getTerm(), store.getVotedFor(), null, 0, 0, scheduleElectionTimeout()));
        started = true;
    } 
```

处理逻辑：

```java
nodeimpl.java
scheduleElectionTimeout（）==context.scheduler().scheduleElectionTimeout(this::electionTimeout);相当于把this::electionTimeout这个任务丢到io线程池去处理
   // 而这个真正的任务由主线程去执行
    void electionTimeout() {
        context.taskExecutor().submit(this::doProcessElectionTimeout, LOGGING_FUTURE_CALLBACK);
    }
```

# 日志条目序列化

日志索引偏移 =下一条日志的索引 =1 说明当前日志条目序列为空

重要的几个属性：
logIndexOffset：初始偏移值

nextlogIndex：表示下一条要加入的日志条目的索引。

基于文件的FIleEntrySequence：
日志条目文件 EntriesFile

日志条目索引文件 EntryIndexFile

等待写入的日志条目缓冲pendEntries

Entries和EntryIndexFile的实现都使用了R使用了RandomAccessFile，将RandomAccessfile方法抽象出来




