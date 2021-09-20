package raft.kvstore.message;

/**
 *
 * 通用异常码：
 * 100：通用异常
 * 101：超时
 * @author xzw
 */
public class Failure {

    private final int errorCode;
    private final String message;

    public Failure(int errorCode, String message) {
        this.errorCode = errorCode;
        this.message = message;
    }

    /**
     * 获取错误代码
     * @return
     */
    public int getErrorCode() {
        return errorCode;
    }

    /**
     * 获取错误描述
     * @return
     */
    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "Failure{" +
                "errorCode=" + errorCode +
                ", message='" + message + '\'' +
                '}';
    }

}
