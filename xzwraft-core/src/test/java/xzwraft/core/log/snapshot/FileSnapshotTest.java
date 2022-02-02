package xzwraft.core.log.snapshot;

import com.google.common.collect.ImmutableSet;
import xzwraft.core.node.NodeEndpoint;
import xzwraft.core.support.ByteArraySeekableFile;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class FileSnapshotTest {

    @Test
    public void test() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        FileSnapshotWriter writer = new FileSnapshotWriter(output, 1, 2, ImmutableSet.of(
                new NodeEndpoint("A", "localhost", 2333),
                new NodeEndpoint("B", "localhost", 2334)
        ));
        byte[] data = "test".getBytes();
        writer.write(data);
        writer.close();

        FileSnapshot snapshot = new FileSnapshot(new ByteArraySeekableFile(output.toByteArray()));
        Assert.assertEquals(1, snapshot.getLastIncludedIndex());
        Assert.assertEquals(2, snapshot.getLastIncludedTerm());
        Assert.assertEquals(2, snapshot.getLastConfig().size());
        Assert.assertEquals(4, snapshot.getDataSize());
        SnapshotChunk chunk = snapshot.readData(0, 10);
        Assert.assertArrayEquals(data, chunk.toByteArray());
        Assert.assertTrue(chunk.isLastChunk());
    }

}