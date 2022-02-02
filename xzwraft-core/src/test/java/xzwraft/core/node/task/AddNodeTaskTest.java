package xzwraft.core.node.task;

import xzwraft.core.node.NodeEndpoint;
import xzwraft.core.node.NodeId;
import xzwraft.core.support.ListeningTaskExecutor;
import xzwraft.core.support.TaskExecutor;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.*;

public class AddNodeTaskTest {

    private static TaskExecutor taskExecutor;

    @BeforeClass
    public static void beforeClass() {
        taskExecutor = new ListeningTaskExecutor(Executors.newSingleThreadExecutor());
    }

    @Test
    public void testNormal() throws InterruptedException, ExecutionException {
        WaitableGroupConfigChangeTaskContext taskContext = new WaitableGroupConfigChangeTaskContext();
        AddNodeTask task = new AddNodeTask(
                taskContext,
                new NodeEndpoint("D", "localhost", 2336),
                2, 1
        );
        Future<GroupConfigChangeTaskResult> future = taskExecutor.submit(task);
        taskContext.awaitLogAppended();
        task.onLogCommitted();
        assertEquals(GroupConfigChangeTaskResult.OK, future.get());
    }

    @Test(expected = IllegalStateException.class)
    public void testOnLogCommittedLogNotAppended() {
        AddNodeTask task = new AddNodeTask(
                new WaitableGroupConfigChangeTaskContext(),
                new NodeEndpoint("D", "localhost", 2336),
                2, 1
        );
        task.onLogCommitted();
    }

    @Test
    public void testIsTargetNode() {
        AddNodeTask task = new AddNodeTask(
                new WaitableGroupConfigChangeTaskContext(),
                new NodeEndpoint("D", "localhost", 2336),
                2, 1
        );
        Assert.assertTrue(task.isTargetNode(NodeId.of("D")));
        Assert.assertFalse(task.isTargetNode(NodeId.of("E")));
    }

    @AfterClass
    public static void afterClass() throws InterruptedException {
        taskExecutor.shutdown();
    }

}