package in.xzw.xzwraft.core.schedule;

import javax.annotation.Nonnull;

/**
 * Scheduler.
 */
// TODO optimize
public interface Scheduler {

    /**
     * Schedule Log replication task.
     *
     * @param task task
     * @return Log replication task
     */
    @Nonnull
    LogReplicationTask scheduleLogReplicationTask(@Nonnull Runnable task);

    /**
     * Schedule election timeout.
     *
     * @param task task
     * @return election timeout
     */
    @Nonnull
    ElectionTimeout scheduleElectionTimeout(@Nonnull Runnable task);

    /**
     * Stop scheduler.
     *
     * @throws InterruptedException if interrupted
     */
    void stop() throws InterruptedException;

}
