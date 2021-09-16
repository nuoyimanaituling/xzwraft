package raft.core.node;

/**
 * @Author xzw
 * @create 2021/9/14 10:24
 */
public class ReplicatingState {

    private int nextIndex;
    private int matchIndex;

    ReplicatingState(int nextIndex) {
        this(nextIndex, 0);
    }

    ReplicatingState(int nextIndex, int matchIndex) {
        this.nextIndex = nextIndex;
        this.matchIndex = matchIndex;
    }

    boolean advance(int lastEntryIndex) {
        // changed
        // 说明 matchIndex和nextIndex可以改变了
        boolean result = (matchIndex != lastEntryIndex || nextIndex != (lastEntryIndex + 1));

        matchIndex = lastEntryIndex;
        nextIndex = lastEntryIndex + 1;
        return result;
    }

    boolean backOffNextIndex() {
        if (nextIndex > 1) {
            nextIndex--;
            return true;
        }
        return false;
    }

    /**
     * Get next index.
     *
     * @return next index
     */
    int getNextIndex() {
        return nextIndex;
    }

    /**
     * Get match index.
     *
     * @return match index
     */
    int getMatchIndex() {
        return matchIndex;
    }
}
