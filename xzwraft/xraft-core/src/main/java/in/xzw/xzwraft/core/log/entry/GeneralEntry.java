package in.xzw.xzwraft.core.log.entry;

public class GeneralEntry extends AbstractEntry {

    private final byte[] commandBytes;

    public GeneralEntry( int index, int term, byte[] commandBytes) {
        super(KIND_GENERAL, index, term);
        this.commandBytes = commandBytes;
    }

    @Override
    public String toString() {
        return "GeneralEntry{" +
                "index=" + index +
                ", term=" + term +
                '}';
    }

    @Override
    public byte[] getCommandBytes() {
        return this.commandBytes;
    }

}
