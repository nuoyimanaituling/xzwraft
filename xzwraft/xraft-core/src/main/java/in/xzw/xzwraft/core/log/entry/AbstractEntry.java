package in.xzw.xzwraft.core.log.entry;

abstract class AbstractEntry implements  Entry {
    private final int kind;
    protected final int index;
    protected final int term;

    public int getKind() {
        return this.kind;
    }

    public int getIndex() {
        return index;
    }

    public int getTerm() {
        return term;
}

    @Override
    public EntryMeta getMeta() {
        return new EntryMeta(kind,index,term);
    }

    public AbstractEntry(int kind, int index, int term) {
        this.kind = kind;
        this.index = index;
        this.term = term;
    }

}
