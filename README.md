

# Note

this implementation benefit from 

[xraft]: https://github.com/nuoyimanaituling/xzwraft

and i simplify his implementation  to more understand.

# 领导者选举

由论文得出的需要持久化的数据：

所有服务器上的持久性状态 (在响应RPC请求之前 已经更新到了稳定的存储设备)，注意这是每台服务器上都应该

持久化保存的。

currentTerm：当前任期
votedFor：投过票给谁

log[] :日志条目，第一个日志的id为1



# 所有服务器需遵守的规则：





所有服务器上的易失性状态

| 参数        | 解释                                                         |
| ----------- | ------------------------------------------------------------ |
| commitIndex | 已知已提交的最高的日志条目的索引（初始值为0，单调递增）      |
| lastApplied | 已经被应用到状态机的最高的日志条目的索引（初始值为0，单调递增） |

commitIndex:由sequence负责维护

lastApplied：由应用状态机（stateMachine）负责维护（即具体执行到哪一步）





领导者（服务器）上的易失性状态 (选举后已经重新初始化)

| 参数         | 解释                                                         |
| ------------ | ------------------------------------------------------------ |
| nextIndex[]  | 对于每一台服务器，发送到该服务器的下一个日志条目的索引（初始值为领导者最后的日志条目的索引+1） |
| matchIndex[] | 对于每一台服务器，已知的已经复制到该服务器的最高日志条目的索引（初始值为0，单调递增） |

所有服务器：

- 如果`commitIndex > lastApplied`，那么就 lastApplied 加一，并把`log[lastApplied]`应用到状态机中（5.3 节）（**这里我们把它定义为两步提交，先增加lastapplied，然后应用到状态机中执行，领导人来决定什么时候把日志条目应用到状态机中是安全的；这种日志条目被称为已提交。Raft 算法保证所有已提交的日志条目都是持久化的并且最终会被所有可用的状态机执行。**）
- 如果接收到的 RPC 请求或响应中，任期号`T > currentTerm`，那么就令 currentTerm 等于 T，并切换状态为跟随者（5.1 节）

跟随者（5.2 节）：

- 响应来自候选人和领导者的请求
- 如果在超过选举超时时间的情况之前没有收到**当前领导人**（即该领导人的任期需与这个跟随者的当前任期相同）的心跳/附加日志，或者是给某个候选人投了票，就自己变成候选人。（**设计算法角色的时候，在一开始就是跟随者，等到成员上线的时候，能够达到条件，然后再开始选举（成为候选者），而一开始在考虑的时候并没有考虑到这个结果**）

候选人（5.2 节）：

- 在转变成候选人后就立即开始选举过程
  - 自增当前的任期号（currentTerm）
  - 给自己投票
  - 重置选举超时计时器
  - 发送请求投票的 RPC 给其他所有服务器
- 如果接收到大多数服务器的选票，那么就变成领导人
- 如果接收到来自新的领导人的附加日志 RPC，转变成跟随者
- 如果选举过程超时，再次发起一轮选举

领导人：

- 一旦成为领导人：发送空的附加日志 RPC（心跳）给其他所有的服务器；在一定的空余时间之后不停的重复发送，以阻止跟随者超时（**防止频繁发生选举**）（5.2 节）
- 如果接收到来自客户端的请求：附加条目到本地日志中，在条目被应用到状态机后响应客户端（5.3 节）
- 如果对于一个跟随者，最后日志条目的索引值大于等于 nextIndex，那么：发送从 nextIndex 开始的所有日志条目：
  - 如果成功：更新相应跟随者的 nextIndex 和 matchIndex
  - 如果因为日志不一致而失败，减少 nextIndex 重试
- 如果存在一个满足`N > commitIndex`的 N，并且大多数的`matchIndex[i] ≥ N`成立，并且`log[N].term == currentTerm`成立，那么令 commitIndex 等于这个 N （5.3 和 5.4 节）



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
	正常情况下：matchIndex =nextIndex-1
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

# 日志条目序列化（Entry+Sequence）

日志索引偏移 =下一条日志的索引 =1 说明当前日志条目序列为空

重要的几个属性：
logIndexOffset：初始偏移值

nextlogIndex：表示下一条要加入的日志条目的索引。

基于文件的FIleEntrySequence：
日志条目文件 EntriesFile

日志条目索引文件 EntryIndexFile

等待写入的日志条目缓冲pendEntries

Entries和EntryIndexFile的实现都使用了R使用了RandomAccessFile，将RandomAccessfile方法抽象出来



# 核心代码：

```java
private void doProcessAppendEntriesResult(AppendEntriesResultMessage resultMessage) {
    AppendEntriesResult result = resultMessage.get();

    // step down if result's term is larger than current term
    if (result.getTerm() > role.getTerm()) {
        becomeFollower(result.getTerm(), null, null, true);
        return;
    }

    // check role 不是leader的话打印警告信息
    if (role.getName() != RoleName.LEADER) {
        logger.warn("receive append entries result from node {} but current node is not leader, ignore", resultMessage.getSourceNodeId());
        return;
    }


    NodeId sourceNodeId = resultMessage.getSourceNodeId();
    GroupMember member = context.group().getMember(sourceNodeId);
    if (member == null) {
        logger.info("unexpected append entries result from node {}, node maybe removed", sourceNodeId);
        return;
    }
    AppendEntriesRpc rpc =resultMessage.getRpc();

    if (result.isSuccess()) {
        // peer
        // advance commit index if major of match index changed(推进每个服务器本身的nextlog与matchIndex)
        if (member.advanceReplicatingState(rpc.getLastEntryIndex())) {
            // 推进leader服务器本地的commitIndex
            context.log().advanceCommitIndex(context.group().getMatchIndexOfMajor(),role.getTerm());

        }
        else {
            // 对于result不成功的响应，leader服务器需要回退nextIndex
            if (!member.backOffNextIndex()) {
                logger.warn("cannot back off next index more, node {}", sourceNodeId);
                member.stopReplicating();
                return;
            }
        }
    }
```

```java
private AppendEntriesResult doProcessAppendEntriesRpc(AppendEntriesRpcMessage rpcMessage) {
    AppendEntriesRpc rpc = rpcMessage.get();

    // reply current term if term in rpc is smaller than current term
    if (rpc.getTerm() < role.getTerm()) {
        return new AppendEntriesResult(rpc.getMessageId(),role.getTerm(), false);
    }

    // if term in rpc is larger than current term, step down and append entries
    if (rpc.getTerm() > role.getTerm()) {
        becomeFollower(rpc.getTerm(), null, rpc.getLeaderId(), true);
        return new AppendEntriesResult( rpc.getMessageId(),rpc.getTerm(), appendEntries(rpc));
    }

    assert rpc.getTerm() == role.getTerm();
    switch (role.getName()) {
        case FOLLOWER:

            // reset election timeout and append entries
            becomeFollower(rpc.getTerm(), ((FollowerNodeRole) role).getVotedFor(), rpc.getLeaderId(), true);
            // 增加entries到存储当中
            return new AppendEntriesResult(rpc.getMessageId(),rpc.getTerm(), appendEntries(rpc));
        case CANDIDATE:

            // more than one candidate but another node won the election
            // 如果作为candidate收到主节点发来的消息，那么自己选择退化为follower，重置选举超时
            becomeFollower(rpc.getTerm(), null, rpc.getLeaderId(),  true);
            return new AppendEntriesResult(rpc.getMessageId(),rpc.getTerm(), appendEntries(rpc));
        case LEADER:
            logger.warn("receive append entries rpc from another leader {}, ignore", rpc.getLeaderId());
            return new AppendEntriesResult(rpc.getMessageId(),rpc.getTerm(), false);
        default:
            throw new IllegalStateException("unexpected node role [" + role.getName() + "]");
    }

}
```

```java
private RequestVoteResult doProcessRequestVoteRpc(RequestVoteRpcMessage rpcMessage) {



        // 如果对方的term比自己小，则不投票并且返回自己的term给对象
        RequestVoteRpc rpc = rpcMessage.get();
        if (rpc.getTerm() < role.getTerm()) {
            logger.debug("term from rpc < current term, don't vote ({} < {})", rpc.getTerm(), role.getTerm());
            return new RequestVoteResult(role.getTerm(), false);
        }
      // 检查是否自己投票
        boolean voteForCandidate =!context.log().isNewerThan(rpc.getLastLogIndex(),rpc.getLastLogTerm());

        // 如果对象的term比自己大，则切换为follower
        if (rpc.getTerm() > role.getTerm()) {
            becomeFollower(rpc.getTerm(), (voteForCandidate ? rpc.getCandidateId() : null), null,true);
            return new RequestVoteResult(rpc.getTerm(), voteForCandidate);
        }

//        assert rpc.getTerm() == role.getTerm();
        // 当对方的2term与自己的一致时
        switch (role.getName()) {
            // 如果自己是follower
            case FOLLOWER:
                FollowerNodeRole follower = (FollowerNodeRole) role;
                NodeId votedFor = follower.getVotedFor();
                // reply vote granted for
                // 1. not voted and candidate's log is newer than self
                // 2. voted for candidate
                if ((votedFor == null && !context.log().isNewerThan(rpc.getLastLogIndex(), rpc.getLastLogTerm())) ||
                        Objects.equals(votedFor, rpc.getCandidateId())) {
                    becomeFollower(role.getTerm(), rpc.getCandidateId(), null, true);
                    return new RequestVoteResult(rpc.getTerm(), true);
                }
                return new RequestVoteResult(role.getTerm(), false);
            case CANDIDATE: // 自己是候选者的话，已经给自己投过票了,所以不会再投票了
            case LEADER:
                return new RequestVoteResult(role.getTerm(), false);
            default:
                throw new IllegalStateException("unexpected node role [" + role.getName() + "]");
        }
    }
```

```java
 private void doProcessRequestVoteResult(RequestVoteResult result) {

        // 如果对象的term比自己打，则退化为follower角色
        if (result.getTerm() > role.getTerm()) {
            becomeFollower(result.getTerm(), null, null, true);
            return;
        }
        // 如果自己不是candidate角色，就忽略
        // check role
        if (role.getName() != RoleName.CANDIDATE) {
            logger.debug("receive request vote result and current role is not candidate, ignore");
            return;
        }

        // do nothing if not vote granted
        if (result.getTerm()<role.getTerm() || !result.isVoteGranted()) {
            return;
        }
        // 否则，则可以判定result.isVoteGranted为true，则可以认为给自己加上一票

        int currentVotesCount = ((CandidateNodeRole) role).getVotesCount() + 1;
        // 获取票数
        int countOfMajor = context.group().getCount();
        logger.debug("votes count {}, major node count {}", currentVotesCount, countOfMajor);
        role.cancelTimeoutOrTask();
        if (currentVotesCount > countOfMajor / 2) {
            // 计算票数如果超过一半票数，那么自己成为leader
            // become leader
            logger.info("become leader, term {}", role.getTerm());
//            resetReplicatingStates(); 只是简单的把nextIndex与matchIndex重置为0
            changeToRole(new LeaderNodeRole(role.getTerm(), scheduleLogReplicationTask()));
            context.log().appendEntry(role.getTerm()); //发送 no-op log，然后让系统趋于稳定
            context.connector().resetChannels(); // close all inbound channels
        } else {

            // update votes count
            changeToRole(new CandidateNodeRole(role.getTerm(), currentVotesCount, scheduleElectionTimeout()));
        }
    }
```