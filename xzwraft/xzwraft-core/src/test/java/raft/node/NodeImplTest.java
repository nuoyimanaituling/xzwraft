package raft.node;

import org.junit.Assert;
import org.junit.Test;
import raft.core.node.*;
import raft.core.node.role.FollowerNodeRole;
import raft.core.node.store.MemoryNodeStore;
import raft.core.rpc.MockConnector;
import raft.core.rpc.message.AppendEntriesResult;
import raft.core.rpc.message.AppendEntriesRpc;
import raft.core.rpc.message.AppendEntriesRpcMessage;
import raft.core.schedule.NullScheduler;
import raft.core.support.DirectTaskExecutor;

import java.util.Arrays;

/**
 * @Author xzw
 * @create 2021/9/19 9:47
 */
public class NodeImplTest {

    @Test
    public void testOnReceiveAppendEntriesRpcFollower() {
        NodeImpl node = (NodeImpl) newNodeBuilder(
                NodeId.of("A"),
                new NodeEndpoint("A", "localhost", 2333),
                new NodeEndpoint("B", "localhost", 2334),
                new NodeEndpoint("C", "localhost", 2335))
                .setStore(new MemoryNodeStore(1, null))
                .build();
        node.start();
        AppendEntriesRpc rpc = new AppendEntriesRpc();
        rpc.setTerm(1);
        rpc.setLeaderId(NodeId.of("B"));
        node.onReceiveAppendEntriesRpc(new AppendEntriesRpcMessage(rpc, NodeId.of("B"), null));
        MockConnector connector = (MockConnector) node.getContext().connector();
        AppendEntriesResult result = (AppendEntriesResult) connector.getResult();
        Assert.assertEquals(1, result.getTerm());
        Assert.assertTrue(result.isSuccess());
        FollowerNodeRole role = (FollowerNodeRole) node.getRole();
        Assert.assertEquals(1,role.getTerm());
        Assert.assertEquals(NodeId.of("B"),role.getLeaderId());
//        RoleState state = node.getRoleState();
//        Assert.assertEquals(RoleName.FOLLOWER, state.getRoleName());
//        Assert.assertEquals(1, state.getTerm());
//        Assert.assertEquals(NodeId.of("B"), state.getLeaderId());
    }

    private NodeBuilder newNodeBuilder(NodeId selfId, NodeEndpoint... endpoints) {
        return new NodeBuilder(Arrays.asList(endpoints), selfId)
                .setScheduler(new NullScheduler())
                .setConnector(new MockConnector())
                .setTaskExecutor(new DirectTaskExecutor(true));
    }



}
