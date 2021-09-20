package raft.kvstore.server;

import com.sun.org.apache.regexp.internal.RE;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raft.core.log.entry.Entry;
import raft.core.log.statemachine.AbstractSingleThreadStateMachine;
import raft.core.log.statemachine.StateMachine;
import raft.core.node.Node;
import raft.core.node.role.RoleName;
import raft.core.node.role.RoleNameAndLeaderId;
import raft.kvstore.message.*;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @Author xzw
 * @create 2021/9/19 11:57
 */
public class Service  {

    private static final Logger logger = LoggerFactory.getLogger(Service.class);
    private final Node node;
    /**
     * 请求id与连接的映射，即请求id与CommandRequest的映射类
     */
    private final ConcurrentMap<String, CommandRequest<?>> pendingCommands = new ConcurrentHashMap<>();

    /**
     * kv服务的数据
     */
    private Map<String, byte[]> map = new HashMap<>();

    public Service(Node node) {
        this.node = node;
        this.node.registerStateMachine(new StateMachineImpl());
    }


    public void set(CommandRequest<SetCommand> commandRequest){
        // 如果当前节点不是leader节点那么就返回redirect
        Redirect redirect = checkLeadership();
        if (redirect != null){
            commandRequest.reply(redirect);
        }
        SetCommand command = commandRequest.getCommand();
        logger.debug("set {}",command.getKey());
        // 记录请求ID和CommandRequest的映射
        this.pendingCommands.put(command.getRequestId(),commandRequest);
        // 客户端连接关闭时从映射中移除
        commandRequest.addCloseListener(()->pendingCommands.remove(command.getRequestId()));
        // 追加日志
        this.node.appendLog(command.toBytes());

    }


    public void get(CommandRequest<GetCommand> commandRequest) {
        Redirect redirect = checkLeadership();
        if (redirect != null) {
            logger.info("reply {}", redirect);
            commandRequest.reply(redirect);
            return;
        }
        GetCommand command = commandRequest.getCommand();
        logger.debug("get {}", command.getKey());

        byte[] value = this.map.get(command.getKey());
        commandRequest.reply(new GetCommandResponse(value));
    }
    private Redirect checkLeadership(){
        RoleNameAndLeaderId state = node.getRoleNameAndLeaderId();
        if (state.getRoleName() != RoleName.LEADER){
            return new Redirect(state.getLeaderId());
        }
        return null;
    }





    private class StateMachineImpl extends AbstractSingleThreadStateMachine {

        @Override
        protected void applyCommand(@Nonnull byte[] commandBytes) {
            SetCommand command = SetCommand.fromBytes(commandBytes);
            map.put(command.getKey(), command.getValue());
            CommandRequest<?> commandRequest = pendingCommands.remove(command.getRequestId());
            if (commandRequest != null) {
                commandRequest.reply(Success.INSTANCE);
            }
        }

    }


}
