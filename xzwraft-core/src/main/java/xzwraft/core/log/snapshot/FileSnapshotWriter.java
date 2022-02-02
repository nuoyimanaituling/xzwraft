package xzwraft.core.log.snapshot;

import xzwraft.core.Protos;
import xzwraft.core.node.NodeEndpoint;

import java.io.*;
import java.util.Set;
import java.util.stream.Collectors;

public class FileSnapshotWriter implements AutoCloseable {

    private final DataOutputStream output;

    public FileSnapshotWriter(File file, int lastIncludedIndex, int lastIncludedTerm, Set<NodeEndpoint> lastConfig) throws IOException {
        this(new DataOutputStream(new FileOutputStream(file)), lastIncludedIndex, lastIncludedTerm, lastConfig);
    }

    FileSnapshotWriter(OutputStream output, int lastIncludedIndex, int lastIncludedTerm, Set<NodeEndpoint> lastConfig) throws IOException {
        this.output = new DataOutputStream(output);
        byte[] headerBytes = Protos.SnapshotHeader.newBuilder()
                .setLastIndex(lastIncludedIndex)
                .setLastTerm(lastIncludedTerm)
                .addAllLastConfig(
                        lastConfig.stream()
                                .map(e -> Protos.NodeEndpoint.newBuilder()
                                        .setId(e.getId().getValue())
                                        .setHost(e.getHost())
                                        .setPort(e.getPort())
                                        .build())
                                .collect(Collectors.toList()))
                .build().toByteArray();
        this.output.writeInt(headerBytes.length);
        this.output.write(headerBytes);

    }

    public OutputStream getOutput() {
        return output;
    }

    public void write(byte[] data) throws IOException {
        output.write(data);
    }

    @Override
    public void close() throws IOException {
        output.close();
    }

}
