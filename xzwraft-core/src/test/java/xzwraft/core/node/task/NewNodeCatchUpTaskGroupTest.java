package xzwraft.core.node.task;

import xzwraft.core.node.config.NodeConfig;
import xzwraft.core.node.NodeEndpoint;
import xzwraft.core.node.NodeId;
import xzwraft.core.rpc.message.AppendEntriesResult;
import xzwraft.core.rpc.message.AppendEntriesResultMessage;
import xzwraft.core.rpc.message.AppendEntriesRpc;
import xzwraft.core.support.SingleThreadTaskExecutor;
import xzwraft.core.support.TaskExecutor;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class NewNodeCatchUpTaskGroupTest {

    @Test
    public void testAdd() {
        NewNodeCatchUpTaskGroup group = new NewNodeCatchUpTaskGroup();
        NewNodeCatchUpTask task = new NewNodeCatchUpTask(
                new WaitableNewNodeCatchUpTaskContext(),
                new NodeEndpoint("D", "localhost", 2336),
                new NodeConfig()
        );
        Assert.assertTrue(group.add(task));
    }

    // task for same node exists
    @Test
    public void testAdd2() {
        NewNodeCatchUpTaskGroup group = new NewNodeCatchUpTaskGroup();
        NewNodeCatchUpTask task = new NewNodeCatchUpTask(
                new WaitableNewNodeCatchUpTaskContext(),
                new NodeEndpoint("D", "localhost", 2336),
                new NodeConfig()
        );
        Assert.assertTrue(group.add(task));
        Assert.assertFalse(group.add(task));
    }

    @Test
    public void testOnReceiveAppendEntriesResultNotFound() {
        NewNodeCatchUpTaskGroup group = new NewNodeCatchUpTaskGroup();
        Assert.assertFalse(group.onReceiveAppendEntriesResult(
                new AppendEntriesResultMessage(
                        new AppendEntriesResult("", 1, true),
                        NodeId.of("A"),
                        new AppendEntriesRpc()
                ),
                10
        ));
    }

    @Test
    public void testOnReceiveAppendEntriesResult() throws InterruptedException, ExecutionException {
        NewNodeCatchUpTaskGroup group = new NewNodeCatchUpTaskGroup();
        WaitableNewNodeCatchUpTaskContext taskContext = new WaitableNewNodeCatchUpTaskContext();
        NewNodeCatchUpTask task = new NewNodeCatchUpTask(
                taskContext,
                new NodeEndpoint("D", "localhost", 2336),
                new NodeConfig()
        );
        group.add(task);
        TaskExecutor taskExecutor = new SingleThreadTaskExecutor();
        Future<NewNodeCatchUpTaskResult> future = taskExecutor.submit(task);
        taskContext.awaitReplicateLog();
        AppendEntriesRpc rpc = new AppendEntriesRpc();
        rpc.setPrevLogIndex(1);
        Assert.assertTrue(group.onReceiveAppendEntriesResult(
                new AppendEntriesResultMessage(
                        new AppendEntriesResult("", 1, true),
                        NodeId.of("D"),
                        rpc
                ),
                2
        ));
        future.get();
        taskExecutor.shutdown();
    }

    @Test
    public void testRemove() {
        NewNodeCatchUpTaskGroup group = new NewNodeCatchUpTaskGroup();
        NewNodeCatchUpTask task = new NewNodeCatchUpTask(
                new WaitableNewNodeCatchUpTaskContext(),
                new NodeEndpoint("D", "localhost", 2336),
                new NodeConfig()
        );
        group.add(task);
        Assert.assertTrue(group.remove(task));
    }

}