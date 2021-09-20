package raft.kvstore.message;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import raft.kvstore.Protos;


import java.util.UUID;

/**
 * set的响应：
 * failure和redirect和success
 */
public class SetCommand {

    private final String requestId;
    private final String key;
    private final byte[] value;

    public SetCommand(String key, byte[] value) {
        this(UUID.randomUUID().toString(), key, value);
    }

    public SetCommand(String requestId, String key, byte[] value) {
        this.requestId = requestId;
        this.key = key;
        this.value = value;
    }


    /**
     * 从二进制数组中恢复setCommand
     * @param bytes
     * @return
     */
    public static SetCommand fromBytes(byte[] bytes) {
        try {
            Protos.SetCommand protoCommand = Protos.SetCommand.parseFrom(bytes);
            return new SetCommand(
                    protoCommand.getRequestId(),
                    protoCommand.getKey(),
                    protoCommand.getValue().toByteArray()
            );
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("failed to deserialize set command", e);
        }
    }


    /**
     * 转换为二进制数组
     * @return
     */
    public byte[] toBytes() {
        return Protos.SetCommand.newBuilder()
                .setRequestId(this.requestId)
                .setKey(this.key)
                .setValue(ByteString.copyFrom(this.value)).build().toByteArray();
    }

    /**
     * @return 获取请求id
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * @return 获取key
     */
    public String getKey() {
        return key;
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
        return "SetCommand{" +
                "key='" + key + '\'' +
                ", requestId='" + requestId + '\'' +
                '}';
    }

}
