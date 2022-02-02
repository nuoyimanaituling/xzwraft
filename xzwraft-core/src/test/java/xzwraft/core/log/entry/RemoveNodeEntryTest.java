package xzwraft.core.log.entry;

import xzwraft.core.node.NodeEndpoint;
import xzwraft.core.node.NodeId;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class RemoveNodeEntryTest {

    @Test
    public void getResultNodeEndpoints() {
        RemoveNodeEntry entry = new RemoveNodeEntry(1, 1,
                Collections.singleton(new NodeEndpoint("A", "localhost", 2333)), NodeId.of("A"));
        Assert.assertTrue(entry.getResultNodeEndpoints().isEmpty());
    }

}