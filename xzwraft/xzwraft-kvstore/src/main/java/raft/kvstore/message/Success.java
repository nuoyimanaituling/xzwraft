package raft.kvstore.message;

/**
 * set 命令请求的成功响应
 */
public class Success {

    public static final Success INSTANCE = new Success();
    private Success(){}

}
