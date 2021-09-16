package raft.core.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @Author xzw
 * @create 2021/9/14 10:52
 * 创建日志复制定时器
 */
public class LogReplicationTask {
    private static final Logger logger = LoggerFactory.getLogger(LogReplicationTask.class);
    private final ScheduledFuture<?> scheduledFuture;

    public LogReplicationTask(ScheduledFuture<?> scheduledFuture) {
        this.scheduledFuture = scheduledFuture;
    }

    public void cancel() {
        logger.debug("cancel log replication task");
        this.scheduledFuture.cancel(false);
    }

    @Override
    public String toString() {
        return "LogReplicationTask{delay=" + scheduledFuture.getDelay(TimeUnit.MILLISECONDS) + "}";
    }

}
