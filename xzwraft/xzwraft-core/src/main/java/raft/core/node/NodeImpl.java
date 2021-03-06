package raft.core.node;

import com.google.common.base.Preconditions;
import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.FutureCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raft.core.log.entry.EntryMeta;
import raft.core.log.statemachine.StateMachine;
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

    private StateMachine stateMachine;

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
    public NodeContext getContext() {
        return context;
    }


    @Override
    public  void registerStateMachine(@Nonnull StateMachine stateMachine) {
        Preconditions.checkNotNull(stateMachine);
        this.stateMachine = stateMachine;
    }

    @Override
    public synchronized void start() {
        if (started) {
            return;
        }
        context.eventBus().register(this);
        context.connector().initialize();
        // load term, votedFor from store and become follower
        //  ????????????follower??????
        NodeStore store = context.store();
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

        // ??????follower???????????????????????????
        // ??????candidate?????????????????????????????????
        // ??????term +1
        int newTerm = role.getTerm() + 1;
        role.cancelTimeoutOrTask();
        logger.info("start election");
        // ??????candidate??????
        changeToRole(new CandidateNodeRole(newTerm,scheduleElectionTimeout()));

        // ??????RequestVote??????
        EntryMeta lastEntryMeta = context.log().getLastEntryMeta();
        RequestVoteRpc rpc =  new RequestVoteRpc();
        rpc.setTerm(newTerm);
        rpc.setCandidateId(context.selfId());
        rpc.setLastLogIndex(lastEntryMeta.getIndex());
        rpc.setLastLogTerm(lastEntryMeta.getTerm());
        context.connector().sendRequestVote(rpc,context.group().listEndpointExceptSelf());

    }


    /**
     * ??????RequestVote??????
     * @param rpcMessage
     */
//    @Subscribe
//    public void onReceiveRequestVoteRpc(RequestVoteRpcMessage rpcMessage) {
//        context.taskExecutor().submit(
//                () -> {
//                    context.connector().replyRequestVote(doProcessRequestVoteRpc(rpcMessage)
//                    , context.findMember(rpcMessage.getSourceNodeId()).getEndpoint()
//                    );
//                }
//        );
//    }
    @Subscribe
    public void onReceiveRequestVoteRpc(RequestVoteRpcMessage rpcMessage) {
        context.taskExecutor().submit(
                () -> {
                    RequestVoteResult result = doProcessRequestVoteRpc(rpcMessage);
                    if (result != null) {
                        context.connector().replyRequestVote(result, rpcMessage);
                    }
                },
                LOGGING_FUTURE_CALLBACK
        );
    }




    private RequestVoteResult doProcessRequestVoteRpc(RequestVoteRpcMessage rpcMessage){
        // reply current term if result's term is smaller than current one
        RequestVoteRpc rpc = rpcMessage.get();
        // ???????????????term????????????????????????????????????????????????term?????????
        if (rpc.getTerm() < role.getTerm()) {
            logger.debug("term from rpc < current term, don't vote ({} < {})", rpc.getTerm(), role.getTerm());
            return new RequestVoteResult(role.getTerm(), false);
        }

        //???????????????term???????????????????????????follower??????
        if (rpc.getTerm() > role.getTerm()) {
            // ????????????
            boolean voteForCandidate = !context.log().isNewerThan(rpc.getLastLogIndex(),rpc.getLastLogTerm());
            becomeFollower(rpc.getTerm(), (voteForCandidate ? rpc.getCandidateId() : null), null, true);
            return new RequestVoteResult(rpc.getTerm(), voteForCandidate);
        }

        // ??????term?????????term??????
        assert rpc.getTerm() == role.getTerm();
        switch (role.getName()) {
            case FOLLOWER:
                FollowerNodeRole follower = (FollowerNodeRole) role;
                NodeId votedFor = follower.getVotedFor();
                // reply vote granted for
                // ???????????????????????????
                // 1. not voted and candidate's log is newer than self ???????????????????????????????????????????????????
                // 2. voted for candidate ??????????????????????????????????????????????????????????????????
                if ((votedFor == null &&  !context.log().isNewerThan(rpc.getLastLogIndex(), rpc.getLastLogTerm())) ||
                        Objects.equals(votedFor, rpc.getCandidateId())) {
                    becomeFollower(role.getTerm(), rpc.getCandidateId(), null, true);
                    return new RequestVoteResult(rpc.getTerm(), true);
                }
                return new RequestVoteResult(role.getTerm(), false);
            case CANDIDATE: // ????????????????????????????????????????????????????????????
            case LEADER:
                return new RequestVoteResult(role.getTerm(), false);
            default:
                throw new IllegalStateException("unexpected node role [" + role.getName() + "]");
        }
    }
    /**
     * ??????requestVote??????
     */
    @Subscribe
    public void onReceiveRequestVoteResult(RequestVoteResult result) {
        context.taskExecutor().submit(() -> doProcessRequestVoteResult(result));
    }


    private void doProcessRequestVoteResult(RequestVoteResult result) {

        // ???????????????term???????????????????????????follower??????
        if (result.getTerm() > role.getTerm()) {
            becomeFollower(result.getTerm(), null, null, true);
            return;
        }
        // ??????????????????Candidate?????????????????????
        if (role.getName() != RoleName.CANDIDATE) {
            logger.debug("receive request vote result and current role is not candidate, ignore");
            return;
        }

        // ???????????????term????????????????????????????????????????????????????????????
        if (!result.isVoteGranted()) {
            return;
        }
        // ????????????
        int currentVotesCount = ((CandidateNodeRole) role).getVotesCount() + 1;
        int countOfMajor = context.group().getCount();
        logger.debug("votes count {}, major node count {}", currentVotesCount, countOfMajor);
        // ????????????????????????????????????????????????????????????????????????
        role.cancelTimeoutOrTask();
        // ??????????????????
        if (currentVotesCount > countOfMajor / 2) {
            // become leader
            logger.info("become leader, term {}", role.getTerm());
            // ??????????????????
            resetReplicatingStates();
            changeToRole(new LeaderNodeRole(role.getTerm(), scheduleLogReplicationTask()));
            // ??????????????????no-op??????????????????????????????leader??????????????????
            context.log().appendEntry(role.getTerm()); // no-op log
//            context.connector().resetChannels(); // close all inbound channels
        } else {

            // update votes count
            changeToRole(new CandidateNodeRole(role.getTerm(), currentVotesCount, scheduleElectionTimeout()));
        }
    }


    /**
     * Reset replicating states.
     */
    private void resetReplicatingStates() {
        context.group().resetReplicatingStates(context.log().getNextIndex());
    }

    private LogReplicationTask scheduleLogReplicationTask() {
        // replicateLog?????????????????????????????????
        return context.scheduler().scheduleLogReplicationTask(this::replicateLog);
    }

    void replicateLog(){
        context.taskExecutor().submit(this::doReplicateLog1);
    }
    private void doReplicateLog1(){
        logger.debug("replicate log");
        // ?????????????????????????????????AppendEntries??????
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
        //?????????leaderId??????????????????leaderId
        if (leaderId != null && !leaderId.equals(role.getLeaderId(context.selfId()))) {
            logger.info("current leader is {}, term {}", leaderId, term);
        }
        ElectionTimeout electionTimeout = scheduleElectionTimeout ? scheduleElectionTimeout() :scheduleElectionTimeout();
        changeToRole(new FollowerNodeRole(term, votedFor, leaderId, electionTimeout));
    }

    private void changeToRole(AbstractNodeRole newRole){
        logger.debug("node {},role state changed ->{}",context.selfId(),newRole);
        // ???????????????term???votedFor
        NodeStore store = context.store();
        store.setTerm(newRole.getTerm());
        // ????????????follow??????????????????votedFor
        if (newRole.getName() == RoleName.FOLLOWER){
            store.setVotedFor(((FollowerNodeRole)newRole).getVotedFor());
        }
        role = newRole;
    }

    /**
     * ????????????leader?????????????????????
     * @throws InterruptedException
     */
//    @Subscribe
//    public void onReceiveAppendEntriesRpc(AppendEntriesRpcMessage rpcMessage) {
//        context.taskExecutor().submit(() ->
//                        context.connector().replyAppendEntries(doProcessAppendEntriesRpc(rpcMessage), context.findMember(rpcMessage.getSourceNodeId()).getEndpoint())
//        );
//    }
    @Subscribe
    public void onReceiveAppendEntriesRpc(AppendEntriesRpcMessage rpcMessage) {
        context.taskExecutor().submit(() ->
                        context.connector().replyAppendEntries(doProcessAppendEntriesRpc(rpcMessage), rpcMessage),
                LOGGING_FUTURE_CALLBACK
        );
    }


    private AppendEntriesResult doProcessAppendEntriesRpc(AppendEntriesRpcMessage rpcMessage){
        AppendEntriesRpc rpc = rpcMessage.get();

        // ???????????????term?????????????????????????????????term
        if (rpc.getTerm() < role.getTerm()) {
            return new AppendEntriesResult(rpc.getMessageId(), role.getTerm(), false);
        }

        // ???????????????term???????????????????????????Follower??????
        if (rpc.getTerm() > role.getTerm()) {
            becomeFollower(rpc.getTerm(), null, rpc.getLeaderId(), true);
            return new AppendEntriesResult(rpc.getMessageId(), rpc.getTerm(), appendEntries(rpc));
        }
        // ????????????term?????????
        assert rpc.getTerm() == role.getTerm();
        switch (role.getName()) {
            case FOLLOWER:

                // ???????????????????????????????????????
                becomeFollower(rpc.getTerm(), ((FollowerNodeRole) role).getVotedFor(), rpc.getLeaderId(), true);
                return new AppendEntriesResult(rpc.getMessageId(), rpc.getTerm(), appendEntries(rpc));
            case CANDIDATE:
                // ?????????????????????????????????????????????????????????????????????????????????????????????
                becomeFollower(rpc.getTerm(), null, rpc.getLeaderId(), true);
                return new AppendEntriesResult(rpc.getMessageId(), rpc.getTerm(), appendEntries(rpc));
                // ?????????leader??????????????????????????????
            case LEADER:
                logger.warn("receive append entries rpc from another leader {}, ignore", rpc.getLeaderId());
                return new AppendEntriesResult(rpc.getMessageId(), rpc.getTerm(), false);
            default:
                throw new IllegalStateException("unexpected node role [" + role.getName() + "]");
        }
    }
    private boolean appendEntries(AppendEntriesRpc rpc) {
        boolean result = context.log().appendEntriesFromLeader(rpc.getPrevLogIndex(),rpc.getPrevLogTerm(),rpc.getEntries());
        // ?????????????????????????????????commitIndex
        if (result){

            // ??????leader????????????lastEntry???index???term
            context.log().advanceCommitIndex(Math.min(rpc.getLeaderCommit(),rpc.getLastEntryIndex()),rpc.getTerm());
        }
        return result;
    }
    // leader????????????????????????????????????
    @Subscribe
    public void onReceiveAppendEntriesResult(AppendEntriesResultMessage resultMessage) {
        context.taskExecutor().submit(() -> doProcessAppendEntriesResult(resultMessage), LOGGING_FUTURE_CALLBACK);
    }
    private void doProcessAppendEntriesResult(AppendEntriesResultMessage resultMessage) {
        AppendEntriesResult result = resultMessage.get();
        // ???????????????term???????????????????????????follower??????
        if(result.getTerm() > role.getTerm()){
            becomeFollower(result.getTerm(),null,null,true);
            return;
        }
        // ?????????????????????
        if(role.getName() != RoleName.LEADER){
            logger.warn("receive append entries result from node {} but current node is not leader, ignore", resultMessage.getSourceNodeId());
            return;
        }
        // ???????????????leader?????????????????? ??????commitIndex
        NodeId sourceNodeId = resultMessage.getSourceNodeId();
        GroupMember member = context.group().getMember(sourceNodeId);
        if (member == null) {
            logger.info("unexpected append entries result from node {}, node maybe removed", sourceNodeId);
            return;
        }
        AppendEntriesRpc rpc = resultMessage.getRpc();
        if(result.isSuccess()){
            // ????????????
            // ??????matchIndex???nextIndex
            // ????????????????????????????????????member???advanceReplicatingState?????????
            // ?????????matchIndex???????????????????????????????????????????????????nextIndex?????????matchIndex+1
            // ??????matchIndex???nextIndex??????????????????advanceReplicatingState??????false
            if(member.advanceReplicatingState(rpc.getLastEntryIndex())){
                // getMatchIndexOfMajor????????????????????????????????????commitIndex????????????
                context.log().advanceCommitIndex(context.group().getMatchIndexOfMajor(),role.getTerm());
            }
        } else{
            if (!member.backOffNextIndex()){
                logger.warn("cannot back off next index more , node {}",sourceNodeId);
            }
        }

    }

    public AbstractNodeRole getRole(){
        return role;
    }



    @Override
    public void appendLog(@Nonnull byte[] commandBytes) {
        Preconditions.checkNotNull(commandBytes);
        ensureLeader();
        context.taskExecutor().submit(() -> {
            context.log().appendEntry(role.getTerm(), commandBytes);
            doReplicateLog1();
        }, LOGGING_FUTURE_CALLBACK);
    }


    private void ensureLeader() {
        RoleNameAndLeaderId result = role.getNameAndLeaderId(context.selfId());
        if (result.getRoleName() == RoleName.LEADER) {
            return;
        }
        NodeEndpoint endpoint = result.getLeaderId() != null ? context.group().findMember(result.getLeaderId()).getEndpoint() : null;
        throw new NotLeaderException(result.getRoleName(), endpoint);
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


    @Override
    @Nonnull
    public RoleNameAndLeaderId getRoleNameAndLeaderId() {
        return role.getNameAndLeaderId(context.selfId());
    }

}
