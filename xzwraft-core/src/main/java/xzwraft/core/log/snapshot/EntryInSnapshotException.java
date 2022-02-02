package xzwraft.core.log.snapshot;

import xzwraft.core.log.LogException;

public class EntryInSnapshotException extends LogException {

    private final int index;

    public EntryInSnapshotException(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

}
