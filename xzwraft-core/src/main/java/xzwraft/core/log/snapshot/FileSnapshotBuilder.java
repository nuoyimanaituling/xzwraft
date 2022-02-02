package xzwraft.core.log.snapshot;

import xzwraft.core.log.LogDir;
import xzwraft.core.log.LogException;
import xzwraft.core.rpc.message.InstallSnapshotRpc;

import java.io.IOException;

public class FileSnapshotBuilder extends AbstractSnapshotBuilder<FileSnapshot> {

    private final LogDir logDir;
    private FileSnapshotWriter writer;

    public FileSnapshotBuilder(InstallSnapshotRpc firstRpc, LogDir logDir) {
        super(firstRpc);
        this.logDir = logDir;

        try {
            writer = new FileSnapshotWriter(logDir.getSnapshotFile(), firstRpc.getLastIndex(), firstRpc.getLastTerm(), firstRpc.getLastConfig());
            writer.write(firstRpc.getData());
        } catch (IOException e) {
            throw new LogException("failed to write snapshot data to file", e);
        }
    }

    @Override
    protected void doWrite(byte[] data) throws IOException {
        writer.write(data);
    }

    @Override
    public FileSnapshot build() {
        close();
        return new FileSnapshot(logDir);
    }

    @Override
    public void close() {
        try {
            writer.close();
        } catch (IOException e) {
            throw new LogException("failed to close writer", e);
        }
    }

}
