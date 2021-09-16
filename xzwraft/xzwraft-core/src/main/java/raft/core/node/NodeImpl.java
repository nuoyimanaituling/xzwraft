package raft.core.node;

import com.google.common.base.Preconditions;
import com.google.common.eventbus.DeadEvent;
import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.FutureCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raft.core.log.entry.EntryMeta;
import raft.core.node.role.*;
import raft.core.node.store.NodeStore;
import raft.core.rpc.message.*;
import raft.core.schedule.ElectionTimeout;
import raft.core.schedule.LogReplicationTask;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

/**
 * Node implementation.
 *
 * @see NodeContext
 */
@ThreadSafe
public class NodeImpl implements Node {

    private static final Logger logger = LoggerFactory.getLogger(NodeImpl.class);

    private static final FutureCallback<Object> LOGGING_FUTURE_CALLBACK = new FutureCallback<Object>() {
        @Override
        public void onSuccess(@Nullable Object result) {
        }

        @Override
        public void onFailure(@Nonnull Throwable t) {
            logger.warn("failure", t);
        }
    };

    private final NodeContext context;
    @GuardedBy("this")
    private boolean started;
    private volatile AbstractNodeRole role;

    /**
     * Create with context.
     *
     * @param context context
     */
    NodeImpl(NodeContext context) {
        this.context = context;
    }

    /**
     * Get context.
     *
     * @return context
     */
    NodeContext getContext() {
        return context;
    }


    @Override
    public synchronized void start() {
        if (started) {
            return;
        }
        context.eventBus().register(this);
        context.connector().initialize();

        // load term, votedFor from store and become follower
        //  启动时为follower角色
        NodeStore store = context.store();
//        changeToRole(new FollowerNodeRole(store.getTerm(), store.getVotedFor(), null, 0, 0, scheduleElectionTimeout()));
        changeToRole(new FollowerNodeRole(store.getTerm(), store.getVotedFor(), null,scheduleElectionTimeout()));
        started = true;
    }

    private ElectionTimeout scheduleElectionTimeout(){
        return context.scheduler().scheduleElectionTimeout(this::electionTimeout);
    }

    void electionTimeout(){
        context.taskExecutor().submit(this::doProcessElectionTimeout);
    }

    private void doProcessElectionTimeout() {
        if (role.getName() == RoleName.LEADER) {
            logger.warn("node {}, current role is leader, ignore election timeout", context.selfId());
            return;
        }

        // 对于follower节点来说是发起选举
        // 对于candidate节点来说是再次发起选举
        // 选举term +1
        int newTerm = role.getTerm() + 1;
        role.cancelTimeoutOrTask();
        logger.info("start election");
        // 变成candidate角色
        changeToRole(new CandidateNodeRole(newTerm,scheduleElectionTimeout()));

        // 发送RequestVote消息
        EntryMeta lastEntryMeta = context.log().getLastEntryMeta();
        RequestVoteRpc rpc =  new RequestVoteRpc();
        rpc.setTerm(newTerm);
        rpc.setCandidateId(context.selfId());
        rpc.setLastLogIndex(lastEntryMeta.getIndex());
        rpc.setLastLogTerm(lastEntryMeta.getTerm());
        context.connector().sendRequestVote(rpc,context.group().listEndpointExceptSelf());
//        if (context.group().isStandalone()) {
//            if (context.mode() == NodeMode.STANDBY) {
//                logger.info("starts with standby mode, skip election");
//            } else {
//
//                // become leader
//                logger.info("become leader, term {}", newTerm);
//                resetReplicatingStates();
//                changeToRole(new LeaderNodeRole(newTerm, scheduleLogReplicationTask()));
//                context.log().appendEntry(newTerm); // no-op log
//            }
//        } else {
//            if (role.getName() == RoleName.FOLLOWER) {
//                // pre-vote
//                changeToRole(new FollowerNodeRole(role.getTerm(), null, null, 1, 0, scheduleElectionTimeout()));
//
//                EntryMeta lastEntryMeta = context.log().getLastEntryMeta();
//                PreVoteRpc rpc = new PreVoteRpc();
//                rpc.setTerm(role.getTerm());
//                rpc.setLastLogIndex(lastEntryMeta.getIndex());
//                rpc.setLastLogTerm(lastEntryMeta.getTerm());
//                context.connector().sendPreVote(rpc, context.group().listEndpointOfMajorExceptSelf());
//            } else {
//                // candidate
//                startElection(newTerm);
//            }
//        }
    }


    /**
     * 收到RequestVote消息
     * @param rpcMessage
     */
    @Subscribe
    public void onReceiveRequestVoteRpc(RequestVoteRpcMessage rpcMessage) {
        context.taskExecutor().submit(
                () -> {
                    context.connector().replyRequestVote(doProcessRequestVoteRpc(rpcMessage)
                    ,context.findMember(rpcMessage.getSourceNodeId()).getEndpoint()
                    );
                }
        );
    }
    private RequestVoteResult doProcessRequestVoteRpc(RequestVoteRpcMessage rpcMessage){
        // reply current term if result's term is smaller than current one
        RequestVoteRpc rpc = rpcMessage.get();
        // 如果对方的term比自己小，则不投票并且返回自己的term给对象
        if (rpc.getTerm() < role.getTerm()) {
            logger.debug("term from rpc < current term, don't vote ({} < {})", rpc.getTerm(), role.getTerm());
            return new RequestVoteResult(role.getTerm(), false);
        }

        //如果对象的term比自己大，则切换为follower角色
        if (rpc.getTerm() > role.getTerm()) {
            // 判断投票
            boolean voteForCandidate = !context.log().isNewerThan(rpc.getLastLogIndex(),rpc.getLastLogTerm());
            becomeFollower(rpc.getTerm(), (voteForCandidate ? rpc.getCandidateId() : null), null, true);
            return new RequestVoteResult(rpc.getTerm(), voteForCandidate);
        }

        // 本地term与消息term一致
        assert rpc.getTerm() == role.getTerm();
        switch (role.getName()) {
            case FOLLOWER:
                FollowerNodeRole follower = (FollowerNodeRole) role;
                NodeId votedFor = follower.getVotedFor();
                // reply vote granted for
                // 以下两种情况下投票
                // 1. not voted and candidate's log is newer than self 自己尚未投票并且对方的日志比自己新
                // 2. voted for candidate 自己已经给对方投过票，相当于又经历了一轮投票
                if ((votedFor == null &&  !context.log().isNewerThan(rpc.getLastLogIndex(), rpc.getLastLogTerm())) ||
                        Objects.equals(votedFor, rpc.getCandidateId())) {
                    becomeFollower(role.getTerm(), rpc.getCandidateId(), null, true);
                    return new RequestVoteResult(rpc.getTerm(), true);
                }
                return new RequestVoteResult(role.getTerm(), false);
            case CANDIDATE: // 已经给自己投过票，所以不会给其他节点投票
            case LEADER:
                return new RequestVoteResult(role.getTerm(), false);
            default:
                throw new IllegalStateException("unexpected node role [" + role.getName() + "]");
        }
    }
    /**
     * 收到requestVote响应
     */
    @Subscribe
    public void onReceiveRequestVoteResult(RequestVoteResult result) {
        context.taskExecutor().submit(() -> doProcessRequestVoteResult(result));
    }


    private void doProcessRequestVoteResult(RequestVoteResult result) {

        // 如果对象的term比自己大，则退化为follower角色
        if (result.getTerm() > role.getTerm()) {
            becomeFollower(result.getTerm(), null, null, true);
            return;
        }
        // 如果自己不是Candidate角色，则忽略。
        if (role.getName() != RoleName.CANDIDATE) {
            logger.debug("receive request vote result and current role is not candidate, ignore");
            return;
        }

        // 如果对方的term比自己小，或者对象没有给自己投票。则忽略
        if (result.getTerm() < role.getTerm() || !result.isVoteGranted()) {
            return;
        }
        // 当前票数
        int currentVotesCount = ((CandidateNodeRole) role).getVotesCount() + 1;
        int countOfMajor = context.group().getCount();
        logger.debug("votes count {}, major node count {}", currentVotesCount, countOfMajor);
        // 代表这次选举超时已经完成一轮，取消选举超时定时器
        role.cancelTimeoutOrTask();
        // 得到过半票数
        if (currentVotesCount > countOfMajor / 2) {

            // become leader
            logger.info("become leader, term {}", role.getTerm());
            // 重置选举进度
            //resetReplicatingStates();
            changeToRole(new LeaderNodeRole(role.getTerm(), scheduleLogReplicationTask()));
            // 猜测添加一个no-op是迅速告诉其他节点的leader节点已经更换
//            context.log().appendEntry(role.getTerm()); // no-op log
//            context.connector().resetChannels(); // close all inbound channels
        } else {

            // update votes count
            changeToRole(new CandidateNodeRole(role.getTerm(), currentVotesCount, scheduleElectionTimeout()));
        }
    }

    private LogReplicationTask scheduleLogReplicationTask() {
        // replicateLog对应日志复制的入口方法
        return context.scheduler().scheduleLogReplicationTask(this::replicateLog);
    }

    void replicateLog(){
        context.taskExecutor().submit(this::doReplicateLog1);
    }
    private void doReplicateLog1(){
        logger.debug("replicate log");
        // 给日志复制对象节点发送AppendEntries消息
        for (GroupMember member: context.group().listReplicationTarget()) {
            doReplicateLog(member, -1);
        }
    }

    private void doReplicateLog(GroupMember member,int maxEntries){

//        AppendEntriesRpc rpc = new AppendEntriesRpc();
//        rpc.setTerm(role.getTerm());
//        rpc.setLeaderId(context.selfId());
//        rpc.setPrevLogIndex(0);
//        rpc.setPrevLogTerm(0);
//        rpc.setLeaderCommit(0);
        AppendEntriesRpc rpc = context.log().createAppendEntriesRpc(role.getTerm(),context.selfId(),member.getNextIndex(),maxEntries);
        context.connector().sendAppendEntries(rpc,member.getEndpoint());
    }









    /**
     * Become follower.
     *
     * @param term                    term
     * @param votedFor                voted for
     * @param leaderId                leader id
//     * @param lastHeartbeat           last heart beat
     * @param scheduleElectionTimeout schedule election timeout or not
     */
    private void becomeFollower(int term, NodeId votedFor, NodeId leaderId, boolean scheduleElectionTimeout) {
        role.cancelTimeoutOrTask();
        //如果有leaderId那么打印本次leaderId
        if (leaderId != null && !leaderId.equals(role.getLeaderId(context.selfId()))) {
            logger.info("current leader is {}, term {}", leaderId, term);
        }
        ElectionTimeout electionTimeout = scheduleElectionTimeout ? scheduleElectionTimeout() :scheduleElectionTimeout();
        changeToRole(new FollowerNodeRole(term, votedFor, leaderId, electionTimeout));
    }



    private void changeToRole(AbstractNodeRole newRole){
        logger.debug("node {},role state changed ->{}",context.selfId(),newRole);
        // 保存本地的term和votedFor
        NodeStore store = context.store();
        store.setTerm(newRole.getTerm());
        // 代表只有follow节点才会记录votedFor
        if (newRole.getName() == RoleName.FOLLOWER){
            store.setVotedFor(((FollowerNodeRole)newRole).getVotedFor());
        }
        role = newRole;
    }

    /**
     * 收到来自leader节点的心跳信息
     * @throws InterruptedException
     */
    @Subscribe
    public void onReceiveAppendEntriesRpc(AppendEntriesRpcMessage rpcMessage) {
        context.taskExecutor().submit(() ->
                        context.connector().replyAppendEntries(doProcessAppendEntriesRpc(rpcMessage), context.findMember(rpcMessage.getSourceNodeId()).getEndpoint())
        );
    }
    private AppendEntriesResult doProcessAppendEntriesRpc(AppendEntriesRpcMessage rpcMessage){
        AppendEntriesRpc rpc = rpcMessage.get();

        // 如果对方的term比自己小，则回复自己的term
        if (rpc.getTerm() < role.getTerm()) {
            return new AppendEntriesResult(rpc.getMessageId(), role.getTerm(), false);
        }

        // 如果对方的term比自己大，则退化为Follower角色
        if (rpc.getTerm() > role.getTerm()) {
            becomeFollower(rpc.getTerm(), null, rpc.getLeaderId(), true);
            return new AppendEntriesResult(rpc.getMessageId(), rpc.getTerm(), appendEntries(rpc));
        }
        // 当双方的term相同时
        assert rpc.getTerm() == role.getTerm();
        switch (role.getName()) {
            case FOLLOWER:

                // 说明双方日志一样，正常状态
                becomeFollower(rpc.getTerm(), ((FollowerNodeRole) role).getVotedFor(), rpc.getLeaderId(), true);
                return new AppendEntriesResult(rpc.getMessageId(), rpc.getTerm(), appendEntries(rpc));
            case CANDIDATE:

                // 说明已经有节点获取选举成功了，然后成为追随者，并重置选举超时器
                becomeFollower(rpc.getTerm(), null, rpc.getLeaderId(), true);
                return new AppendEntriesResult(rpc.getMessageId(), rpc.getTerm(), appendEntries(rpc));
                // 如果是leader的话，代表出现错误了
            case LEADER:
                logger.warn("receive append entries rpc from another leader {}, ignore", rpc.getLeaderId());
                return new AppendEntriesResult(rpc.getMessageId(), rpc.getTerm(), false);
            default:
                throw new IllegalStateException("unexpected node role [" + role.getName() + "]");
        }
    }
    private boolean appendEntries(AppendEntriesRpc rpc){
        boolean result = context.log().appendEntriesFromLeader(rpc.getPrevLogIndex(),rpc.getPrevLogTerm(),rpc.getEntries());
        // 增加日志成功，那么推进commitIndex
        if (result){
            // 获得leader发过来的lastEntry的index和term
            context.log().advanceCommitIndex(Math.min(rpc.getLeaderCommit(),rpc.getLastEntryIndex()),rpc.getTerm());
        }
        return result;
    }
    // leader节点收到发送信息的响应：
    @Subscribe
    public void onReceiveAppendEntriesResult(AppendEntriesResultMessage resultMessage) {
        context.taskExecutor().submit(() -> doProcessAppendEntriesResult(resultMessage), LOGGING_FUTURE_CALLBACK);
    }
    private void doProcessAppendEntriesResult(AppendEntriesResultMessage resultMessage) {
        AppendEntriesResult result = resultMessage.get();
        // 如果对方的term比自己大，则退化为follower角色
        if(result.getTerm() > role.getTerm()){
            becomeFollower(result.getTerm(),null,null,true);
            return;
        }
        // 检查自己的角色
        if(role.getName() != RoleName.LEADER){
            logger.warn("receive append entries result from node {} but current node is not leader, ignore", resultMessage.getSourceNodeId());
            return;
        }
        // 对于自身是leader情况的情况下 推进commitIndex
        NodeId sourceNodeId = resultMessage.getSourceNodeId();
        GroupMember member = context.group().getMember(sourceNodeId);
        if (member == null) {
            logger.info("unexpected append entries result from node {}, node maybe removed", sourceNodeId);
            return;
        }
        AppendEntriesRpc rpc = resultMessage.getRpc();
        if(result.isSuccess()){
            // 回复成功
            // 推进matchIndex和nextIndex
            // 在收到成功响应的分支中，member的advanceReplicatingState负责把
            // 节点的matchIndex更新为消息中的最后一条日志的索引，nextIndex更新为matchIndex+1
            // 如果matchIndex和nextIndex没有变化，则advanceReplicatingState返回false
            if(member.advanceReplicatingState(rpc.getLastEntryIndex())){
                // getMatchIndexOfMajor是服务器成员用于计算过半commitIndex的方法。
                context.log().advanceCommitIndex(context.group().getMatchIndexOfMajor(),role.getTerm());
            }
        } else{
            if (!member.backOffNextIndex()){
                logger.warn("cannot back off next index more , node {}",sourceNodeId);
            }
        }

    }



    @Override
    public synchronized void stop() throws InterruptedException {
        if (!started) {
            throw new IllegalStateException("node not started");
        }
        context.scheduler().stop();
//        context.log().close();
        context.connector().close();
        context.store().close();
        context.taskExecutor().shutdown();
//        context.groupConfigChangeTaskExecutor().shutdown();
        started = false;
    }

}
