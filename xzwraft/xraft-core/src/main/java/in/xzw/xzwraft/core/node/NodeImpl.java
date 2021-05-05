package in.xzw.xzwraft.core.node;

import com.google.common.base.Preconditions;
import com.google.common.eventbus.Subscribe;

import in.xzw.xzwraft.core.log.entry.EntryMeta;

import in.xzw.xzwraft.core.log.statemachine.StateMachine;
import in.xzw.xzwraft.core.node.role.*;
import in.xzw.xzwraft.core.node.store.NodeStore;

import in.xzw.xzwraft.core.rpc.Connector;
import in.xzw.xzwraft.core.rpc.message.*;
import in.xzw.xzwraft.core.schedule.ElectionTimeout;
import in.xzw.xzwraft.core.schedule.LogReplicationTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

import javax.annotation.concurrent.ThreadSafe;
import java.util.*;


/**
 * Node implementation.
 *
 * @see NodeContext
 */
@ThreadSafe
public class NodeImpl implements Node {
    @Override
    public void registerStateMachine(@Nonnull StateMachine stateMachine) {
        Preconditions.checkNotNull(stateMachine);
        context.log().setStateMachine(stateMachine);

    }

    private static final Logger logger = LoggerFactory.getLogger(NodeImpl.class);
    private final NodeContext context;
    private boolean started;
    private AbstractNodeRole role;

    public NodeImpl(NodeContext context) {
        this.context = context;
    }

    @Nonnull
    @Override
    public RoleNameAndLeaderId getRoleNameAndLeaderId() {
        return role.getNameAndLeaderId(context.selfId());
    }

    @Override
    public synchronized void start() {
            if (started){
                return;
            }
            context.eventBus().register(this);
            Connector connector =context.connector();
            context.connector().initialize();
            NodeStore store = context.store();

            Set<NodeEndpoint> lastGroup = context.log().getLastGroup();
             context.group().updateNodes(lastGroup);
            changeToRole(new FollowerNodeRole(store.getTerm(), store.getVotedFor(), null, scheduleElectionTimeout()));
            started = true;


    }

    private void changeToRole(AbstractNodeRole newRole) {

            logger.debug("node {}, role state changed -> {}", context.selfId(), newRole);

            // update store
            NodeStore store = context.store();
            store.setTerm(newRole.getTerm());
          if (newRole.getName() ==RoleName.FOLLOWER){
              // 更换
              store.setVotedFor(((FollowerNodeRole)newRole).getVotedFor());
          }
            role = newRole;
    }


    @Override
    public void appendLog(@Nonnull byte[] commandBytes) {

    }

    @Override
    public void enqueueReadIndex(@Nonnull String requestId) {

    }

    @Override
    public synchronized void stop() throws InterruptedException {

        if (!started) {
            throw new IllegalStateException("node not started");
        }
        context.scheduler().stop();
        context.log().close();
        context.connector().close();
        context.taskExecutor().shutdown();
        started =false;
    }

    void electionTimeout() {
        context.taskExecutor().submit(this::doProcessElectionTimeout);
    }

    private void doProcessElectionTimeout(){
        if (role.getName() ==RoleName.LEADER){
            logger.warn("node{},current role is leader,ignore electiontimeout",context.selfId());
            return;
        }
        // 对于follower节点来说是发起选举
        // 对于candidate节点来说是再次发起选举
        int newTerm =role.getTerm()+1;
        role.cancelTimeoutOrTask();
        logger.info("start election");

        changeToRole(new CandidateNodeRole(newTerm,scheduleElectionTimeout()));
        EntryMeta lastEntryMeta = context.log().getLastEntryMeta();
        RequestVoteRpc rpc =new RequestVoteRpc();
        rpc.setTerm(newTerm);
        // 设置自己的id
        rpc.setCandidateId(context.selfId());
        rpc.setLastLogIndex(lastEntryMeta.getIndex());
        rpc.setLastLogTerm(lastEntryMeta.getTerm());
        NodeGroup grouup =context.getGroup();
        System.out.println(grouup.toString());
        context.connector().sendRequestVote(rpc,context.getGroup().listEndpointExceptSelf());

//        if (role.getName() == RoleName.FOLLOWER) {
//            logger.info("现在在follower逻辑里面");
//            changeToRole(new FollowerNodeRole(role.getTerm(), null, null, scheduleElectionTimeout()));
//
//            EntryMeta lastEntryMeta = context.log().getLastEntryMeta();
//            RequestVoteRpc rpc = new RequestVoteRpc();
//            rpc.setTerm(role.getTerm());
//            rpc.setLastLogIndex(lastEntryMeta.getIndex());
//            rpc.setLastLogTerm(lastEntryMeta.getTerm());
//            context.connector().sendRequestVote(rpc, context.group().listEndpointOfMajorExceptSelf());
//        } else {
//            // candidate
//            startElection(newTerm);
//        }

    }


    private void startElection(int term) {
        logger.info("start election, term {}", term);
        changeToRole(new CandidateNodeRole(term, scheduleElectionTimeout()));

        // request vote
        EntryMeta lastEntryMeta = context.log().getLastEntryMeta();
        RequestVoteRpc rpc = new RequestVoteRpc();
        rpc.setTerm(term);
        rpc.setCandidateId(context.selfId());
        rpc.setLastLogIndex(lastEntryMeta.getIndex());
        rpc.setLastLogTerm(lastEntryMeta.getTerm());
        context.connector().sendRequestVote(rpc, context.group().listEndpointOfMajorExceptSelf());
    }






    private ElectionTimeout scheduleElectionTimeout() {
        return context.scheduler().scheduleElectionTimeout(this::electionTimeout);
    }

    @Subscribe
    public void onReceiveRequestVoteRpc(RequestVoteRpcMessage rpcMessage) {
        context.taskExecutor().submit(
                () -> {
                    context.connector().replyRequestVote(doProcessRequestVoteRpc(rpcMessage),rpcMessage);

                }
        );
    }


    private RequestVoteResult doProcessRequestVoteRpc(RequestVoteRpcMessage rpcMessage) {



        // 如果对方的term比自己小，则不投票并且返回自己的term给对象
        RequestVoteRpc rpc = rpcMessage.get();
        if (rpc.getTerm() < role.getTerm()) {
            logger.debug("term from rpc < current term, don't vote ({} < {})", rpc.getTerm(), role.getTerm());
            return new RequestVoteResult(role.getTerm(), false);
        }

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
    // 成为follower的人要一直请求发起选举，这样才有机会成为leader
    private void becomeFollower(int term, NodeId votedFor, NodeId leaderId, boolean scheduleElectionTimeout) {
        role.cancelTimeoutOrTask();
        if (leaderId != null && !leaderId.equals(role.getLeaderId(context.selfId()))) {
            logger.info("current leader is {}, term {}", leaderId, term);
        }
        ElectionTimeout electionTimeout = scheduleElectionTimeout ? scheduleElectionTimeout() : ElectionTimeout.NONE;
        changeToRole(new FollowerNodeRole(term, votedFor, leaderId, electionTimeout));
    }
    // 收到requestvote的响应
    @Subscribe
    public void onReceiveRequestVoteResult(RequestVoteResult result) {
        context.taskExecutor().submit(() -> doProcessRequestVoteResult(result));
    }

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
    private LogReplicationTask scheduleLogReplicationTask() {
        return context.scheduler().scheduleLogReplicationTask(this::replicateLog);
    }
    void replicateLog() {
        context.taskExecutor().submit(this::doReplicateLog);
    }

    private void doReplicateLog() {
        logger.debug("replicate log");
        for (GroupMember member : context.group().listReplicationTarget()) {
            doReplicateLog1(member,context.config().getMaxReplicationEntries());
        }
    }
    private void doReplicateLog1(GroupMember member,int maxEntries) {
//       AppendEntriesRpc rpc =new AppendEntriesRpc();
//       rpc.setTerm(role.getTerm());
//       rpc.setLeaderId(context.selfId());
//       rpc.setPrevLogIndex(0);
//       rpc.setPrevLogTerm(0);
//       rpc.setLeaderCommit(0);
//       context.connector().sendAppendEntries(rpc,member.getEndpoint());
        AppendEntriesRpc rpc = context.log().createAppendEntriesRpc(role.getTerm(), context.selfId(), member.getNextIndex(), maxEntries);
        context.connector().sendAppendEntries(rpc, member.getEndpoint());
    }

    @Subscribe
    // 收到来自leader节点的心跳信息后需要重置选举超时，并记录当前leader节点的id
    public void onReceiveAppendEntriesRpc(AppendEntriesRpcMessage rpcMessage) {
        context.taskExecutor().submit(() ->
                        context.connector().replyAppendEntries(doProcessAppendEntriesRpc(rpcMessage), rpcMessage
                        )
        );
    }

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

    private boolean appendEntries(AppendEntriesRpc rpc) {

        boolean result =context.log().appendEntriesFromLeader(rpc.getPrevLogIndex(),rpc.getPrevLogTerm(),rpc.getEntries());
        if (result){
            // 当追加成功后，follow节点需要根据leader节点的commitIndex决定是否推进本地的commitIndex
            context.log().advanceCommitIndex(Math.min(rpc.getLeaderCommit(),rpc.getLastEntryIndex()),rpc.getTerm());
        }
        return result;
    }

    @Subscribe
    public void onReceiveAppendEntriesResult(AppendEntriesResultMessage resultMessage) {
        context.taskExecutor().submit(() -> doProcessAppendEntriesResult(resultMessage));
    }




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
            // advance commit index if major of match index changed
            if (member.advanceReplicatingState(rpc.getLastEntryIndex())) {
                // 推进本地的commitIndex
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


        // leader节点暂时什么都不做


//        // dispatch to new node catch up task by node id
//        if (newNodeCatchUpTaskGroup.onReceiveAppendEntriesResult(resultMessage, context.log().getNextIndex())) {
//            return;
//        }
//
//        NodeId sourceNodeId = resultMessage.getSourceNodeId();
//        GroupMember member = context.group().getMember(sourceNodeId);
//        if (member == null) {
//            logger.info("unexpected append entries result from node {}, node maybe removed", sourceNodeId);
//            return;
//        }
//
//        AppendEntriesRpc rpc = resultMessage.getRpc();
//        if (result.isSuccess()) {
//            // peer
//            // advance commit index if major of match index changed
//            if (member.advanceReplicatingState(rpc.getLastEntryIndex())) {
//                List<GroupConfigEntry> groupConfigEntries = context.log().advanceCommitIndex(context.group().getMatchIndexOfMajor(), role.getTerm());
//                for (GroupConfigEntry entry : groupConfigEntries) {
//                    currentGroupConfigChangeTask.onLogCommitted(entry);
//                }
//            }
//
//            for (ReadIndexTask task : readIndexTaskMap.values()) {
//                if (task.updateMatchIndex(member.getId(), member.getMatchIndex())) {
//                    logger.debug("remove read index task {}", task);
//                    readIndexTaskMap.remove(task.getRequestId());
//                }
//            }
//
//            // node caught up
//            if (member.getNextIndex() >= context.log().getNextIndex()) {
//                member.stopReplicating();
//                return;
//            }
//        } else {
//
//            // backoff next index if failed to append entries
//            if (!member.backOffNextIndex()) {
//                logger.warn("cannot back off next index more, node {}", sourceNodeId);
//                member.stopReplicating();
//                return;
//            }
//        }
//
//        // replicate log to node immediately other than wait for next log replication
//        doReplicateLog(member, context.config().getMaxReplicationEntries());
    }















}
