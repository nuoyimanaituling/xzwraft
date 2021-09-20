package raft.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raft.core.node.NodeId;

import java.util.*;

/**
 * ServerRouter是客户端内部负责处理kv服务端的重定向和选择leader节点的路由器类
 * ServerRouter选择Leader服务器原则：
 * 1：如果没有节点，则抛出没有服务节点的错误
 * 2：如果有手动设置的leader节点id，则返回手动设置的leader节点id与其他leader节点id的列表
 * 3：如果没有手动设置的leader节点id，那么则返回任意的节点id列表
 */
public class ServerRouter {

    private static Logger logger = LoggerFactory.getLogger(ServerRouter.class);
    private final Map<NodeId, Channel> availableServers = new HashMap<>();
    private NodeId leaderId;

    /**
     * 发送消息
     * @param payload
     * @return
     */
    public Object send(Object payload) {
        for (NodeId nodeId : getCandidateNodeIds()) {
            try {
                Object result = doSend(nodeId, payload);
                this.leaderId = nodeId;
                return result;
            } catch (RedirectException e) {
                // 收到重定向请求，修改leader节点id
                logger.debug("not a leader server, redirect to server {}", e.getLeaderId());
                this.leaderId = e.getLeaderId();
                return doSend(e.getLeaderId(), payload);
            } catch (Exception e) {
                // 连接失败，尝试下一个节点
                logger.debug("failed to process with server " + nodeId + ", cause " + e.getMessage());
            }
        }
        throw new NoAvailableServerException("no available server");
    }


    /**
     * 获取候选节点id列表
     * @return
     */
    private Collection<NodeId> getCandidateNodeIds() {
        if (availableServers.isEmpty()) {
            throw new NoAvailableServerException("no available server");
        }

        if (leaderId != null) {
            List<NodeId> nodeIds = new ArrayList<>();
            nodeIds.add(leaderId);
            for (NodeId nodeId : availableServers.keySet()) {
                if (!nodeId.equals(leaderId)) {
                    nodeIds.add(nodeId);
                }
            }
            return nodeIds;
        }

        return availableServers.keySet();
    }

    private Object doSend(NodeId id, Object payload) {
        Channel channel = this.availableServers.get(id);
        if (channel == null) {
            throw new IllegalStateException("no such channel to server " + id);
        }
        logger.debug("send request to server {}", id);
        return channel.send(payload);
    }

    public void add(NodeId id, Channel channel) {
        this.availableServers.put(id, channel);
    }

    public NodeId getLeaderId() {
        return leaderId;
    }

    public void setLeaderId(NodeId leaderId) {
        if (!availableServers.containsKey(leaderId)) {
            throw new IllegalStateException("no such server [" + leaderId + "] in list");
        }
        this.leaderId = leaderId;
    }

}
