package in.xzw.xzwraft.core.log.entry;

public class EntryFactory {

    public Entry create(int kind, int index, int term, byte[] commandBytes) {
//        try {
            switch (kind) {
                case Entry.KIND_NO_OP:
                    return new NoOpEntry(index, term);
                case Entry.KIND_GENERAL:
                    return new GeneralEntry(index,term, commandBytes);
                default:
                    throw new IllegalArgumentException("unexpected entry kind " + kind);
//            }
//        } catch (InvalidProtocolBufferException e) {
//            throw new IllegalStateException("failed to parse command", e);
        }
    }

}
