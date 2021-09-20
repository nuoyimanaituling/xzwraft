package raft.kvstore.message;

/**
 * 对于get请求的响应
 */
public class GetCommandResponse {

    private final boolean found;
    private final byte[] value;

    public GetCommandResponse(byte[] value) {
        this(value != null, value);
    }

    public GetCommandResponse(boolean found, byte[] value) {
        this.found = found;
        this.value = value;
    }
    // 是否存在
    public boolean isFound() {
        return found;
    }

    /**
     * 获取value
     * @return
     */
    public byte[] getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "GetCommandResponse{found=" + found + '}';
    }

}
