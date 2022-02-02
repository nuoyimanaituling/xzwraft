package xzwraft.core.log.snapshot;

import xzwraft.core.node.NodeEndpoint;

import javax.annotation.Nonnull;
import java.io.InputStream;
import java.util.Set;

// TODO add doc
public interface Snapshot {

    int getLastIncludedIndex();

    int getLastIncludedTerm();

    @Nonnull
    Set<NodeEndpoint> getLastConfig();

    long getDataSize();

    @Nonnull
    SnapshotChunk readData(int offset, int length);

    @Nonnull
    InputStream getDataStream();

    void close();

}
