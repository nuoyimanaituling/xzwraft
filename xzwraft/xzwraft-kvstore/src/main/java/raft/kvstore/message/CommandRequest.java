package raft.kvstore.message;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;

/**
 * 一个请求对应的一个连接，CommandRequest类是一个包装了命令和客户端连接的封装类
 * @author xzw
 */
public class CommandRequest<T> {

    private final T command;
    private final Channel channel;

    public CommandRequest(T command, Channel channel) {
        this.command = command;
        this.channel = channel;
    }

    /**
     * 响应结果
     * @param response
     */
    public void reply(Object response) {
        this.channel.writeAndFlush(response);
    }

    /**
     * 关闭时的连接器
     * @param runnable
     */
    public void addCloseListener(Runnable runnable) {
        this.channel.closeFuture().addListener((ChannelFutureListener) future -> runnable.run());
    }

    /**
     * 获取命令
     * @return
     */
    public T getCommand() {
        return command;
    }

}
