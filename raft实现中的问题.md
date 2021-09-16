## 问题：选主的话选谁，选哪个副本成为leader

## 问题：数据一致性保证

1：使用日志写入，而不是直接修改，保证到follower服务器的同步请求有序，且能够重新计算当前状态

2：写入时，过半写入才算成功，Quorum机制

## 问题：服务器节点如何判断选主

候选者通过比较来自其他节点的投票请求中的日志索引和自己本地的日志索引来确定。如果自己本地日志索引大

则不投票。

## 日志消息

RequestVote：一般由Candidate节点发出

AppendEntries：增加条目，一般由leased节点发出。

选举超时：一般每个节点选举超时时间是一个区间的随机值，则每个节点成为候选者的时间点就被错开了，这样

就提高了Leader节点的概率。

## 日志条目：已追加但是尚未持久化和已经持久化

Raft算法中的节点会维护一个已经持久化的日志条目索引，即commitIndex，小于等于commitIndex的日志条目被认为是已经持久化的，否则就是尚未持久化的数据。

## 复制进度

leader节点负责记录各个节点的 nextIndex（下一个需要复制日志条目的索引）和matchIndex（已匹配日志的索引）

选出leader节点之后，leader节点会重置各节点的nextIndex和matchIndex,也就是把matchIndex置为0，nextIndex设置为leader节点接下来的日志条目的索引，然后通过和各节点之间发送AppendEntries消息来更新nextIndex和matchIndex。

复制过程：Raft算法采用乐观策略，认为follower节点和leader节点日志不会相差太大。把nextIndex设置成最大值，matchIndex设置为0，然后不断回退nextIndex，以找到匹配的日志索引，这时matchIndex会从0跳到一个比较大的值，然后类似于进度条一样批量同步日志并更新进度。

## 问题：如何实现定时发送消息

## 问题：文件IO，性能优化的地方

## 日志

Raft日志类型：

(1) 普通日志条目：即上层服务产生的日志，日志的负载是上层服务操作的内容

(2) NO-OP日志条目：即选举产生的新leader节点增加的第一条空日志。不需要在上层服务的状态机中应用。

日志接口实现优化：

选举开始时发送的RequestVote消息中，只需要用最后一条日志的term和index来组装RequestVote消息，此时可以调用日志组件，获取最后一条日志的元信息，避免访问日志的负载。

###  日志接口：

现阶段完成的三种日志接口：

| 方法                    | 功能                              | 场景                                                  |
| ----------------------- | --------------------------------- | ----------------------------------------------------- |
| getLastEntryMeta        | 获取最后一条日志条目的term，index | 选举开始时，发送消息                                  |
| createAppendEntriesRpc  | 创建日志复制消息                  | leader向follower发送日志复制消息                      |
| appendEntry             | 增加日志条目                      | 上层服务操作或者当前节点成为leader后的第一条no-op日志 |
| appendEntriesFromLeader | 追加从leader过来的日志条目        | 收到来自leader服务器的日志复制请求                    |
| advanceCommitIndex      | 推进commitIndex                   | 收到来自leader服务器的日志复制请求                    |

总结:上层服务操作，leader写日志，并开启日志复制

## 问题：所以文件的建立和日志文件的建立时机是不是一样的

