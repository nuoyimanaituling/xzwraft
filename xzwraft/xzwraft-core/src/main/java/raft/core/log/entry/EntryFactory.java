package raft.core.log.entry;

import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class EntryFactory {

    public Entry create(int kind, int index, int term, byte[] commandBytes) {

            switch (kind) {
                case Entry.KIND_NO_OP:
                    return new NoOpEntry(index, term);
                case Entry.KIND_GENERAL:
                    return new GeneralEntry(index, term, commandBytes);
                default:
                    throw new IllegalArgumentException("unexpected entry kind " + kind);
            }
    }
}
