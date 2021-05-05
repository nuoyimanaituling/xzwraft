package in.xzw.xzwraft.kvstore.server;

import in.xzw.xzwraft.core.log.statemachine.AbstractSingleThreadStateMachine;
import in.xzw.xzwraft.core.node.Node;
import in.xzw.xzwraft.core.node.role.RoleName;
import in.xzw.xzwraft.core.node.role.RoleNameAndLeaderId;
import in.xzw.xzwraft.kvstore.message.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Service {

    private static final Logger logger = LoggerFactory.getLogger(Service.class);
    private final Node node;
    private final ConcurrentMap<String, CommandRequest<?>> pendingCommands = new ConcurrentHashMap<>();
    private Map<String, byte[]> map = new HashMap<>();

    public Service(Node node) {
        this.node = node;
        this.node.registerStateMachine(new StateMachineImpl());
    }


    public void get(CommandRequest<GetCommand> commandRequest) {
        String key = commandRequest.getCommand().getKey();
        logger.debug("get {}", key);
        byte[] value = this.map.get(key);
        // TODO view from node state machine
        commandRequest.reply(new GetCommandResponse(value));
    }
    public void set(CommandRequest<SetCommand> commandRequest) {
        Redirect redirect = checkLeadership();
        if (redirect != null) {
            logger.info("reply {}", redirect);
            commandRequest.reply(redirect);
            return;
        }

        SetCommand command = commandRequest.getCommand();
        logger.debug("set {}", command.getKey());
        this.pendingCommands.put(command.getRequestId(), commandRequest);
        commandRequest.addCloseListener(() -> pendingCommands.remove(command.getRequestId()));
        this.node.appendLog(command.toBytes());
    }




    private Redirect checkLeadership() {
        RoleNameAndLeaderId state = node.getRoleNameAndLeaderId();
        if (state.getRoleName() != RoleName.LEADER) {
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

        @Override
        protected void onReadIndexReached(String requestId) {
//            CommandRequest<?> commandRequest = pendingCommands.remove(requestId);
//            if (commandRequest != null) {
//                GetCommand command = (GetCommand) commandRequest.getCommand();
//                commandRequest.reply(new GetCommandResponse(map.get(command.getKey())));
//            }
        }

        @Override
        protected void doApplySnapshot(@Nonnull InputStream input) throws IOException {

        }

        public boolean shouldGenerateSnapshot(int firstLogIndex, int lastApplied) {
            return lastApplied - firstLogIndex > 1;
        }

//        @Override
//        public void generateSnapshot(@Nonnull OutputStream output) throws IOException {
////            toSnapshot(map, output);
//        }

    }




}
