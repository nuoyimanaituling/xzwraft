package xzwraft.core.node.task;

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

public class RemoveNodeTaskTest {

    private static TaskExecutor taskExecutor;

    @BeforeClass
    public static void beforeClass() {
        taskExecutor = new ListeningTaskExecutor(Executors.newSingleThreadExecutor());
    }

    @Test
    public void testNormal() throws InterruptedException, ExecutionException {
        WaitableGroupConfigChangeTaskContext taskContext = new WaitableGroupConfigChangeTaskContext();
        RemoveNodeTask task = new RemoveNodeTask(
                taskContext,
                NodeId.of("D")
        );
        Future<GroupConfigChangeTaskResult> future = taskExecutor.submit(task);
        taskContext.awaitLogAppended();
        task.onLogCommitted();
        Assert.assertEquals(GroupConfigChangeTaskResult.OK, future.get());
    }

    @Test(expected = IllegalStateException.class)
    public void testOnLogCommittedLogNotAppended() {
        RemoveNodeTask task = new RemoveNodeTask(
                new WaitableGroupConfigChangeTaskContext(),
                NodeId.of("D")
        );
        task.onLogCommitted();
    }

    @Test
    public void testIsTargetNode() {
        RemoveNodeTask task = new RemoveNodeTask(
                new WaitableGroupConfigChangeTaskContext(),
                NodeId.of("D")
        );
        Assert.assertTrue(task.isTargetNode(NodeId.of("D")));
        Assert.assertFalse(task.isTargetNode(NodeId.of("E")));
    }

    @AfterClass
    public static void afterClass() throws InterruptedException {
        taskExecutor.shutdown();
    }

}