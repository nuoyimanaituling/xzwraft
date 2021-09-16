package raft.core.rpc.nio;


import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import raft.core.Protos;
import raft.core.log.entry.EntryFactory;
import raft.core.node.NodeId;
import raft.core.rpc.message.*;

import java.util.List;
import java.util.stream.Collectors;

public class Decoder extends ByteToMessageDecoder {
    // 反序列化

    private final EntryFactory entryFactory = new EntryFactory();

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        int availableBytes = in.readableBytes();
        // 先预读8字节
        if (availableBytes < 8) {return;}
        // 记录当前起始位置
        in.markReaderIndex();
        int messageType = in.readInt();
        int payloadLength = in.readInt();
        // 消息尚未完全可读，处于半包状态
        if (in.readableBytes() < payloadLength) {
            in.resetReaderIndex();
            return;
        }
        // 消息可读
        byte[] payload = new byte[payloadLength];
        in.readBytes(payload);
        // 根据消息类型反序列化
        switch (messageType) {
            case MessageConstants.MSG_TYPE_NODE_ID:
                out.add(new NodeId(new String(payload)));
                break;
            case MessageConstants.MSG_TYPE_REQUEST_VOTE_RPC:
                Protos.RequestVoteRpc protoRVRpc = Protos.RequestVoteRpc.parseFrom(payload);
                RequestVoteRpc rpc = new RequestVoteRpc();
                rpc.setTerm(protoRVRpc.getTerm());
                rpc.setCandidateId(new NodeId(protoRVRpc.getCandidateId()));
                rpc.setLastLogIndex(protoRVRpc.getLastLogIndex());
                rpc.setLastLogTerm(protoRVRpc.getLastLogTerm());
                out.add(rpc);
                break;
            case MessageConstants.MSG_TYPE_REQUEST_VOTE_RESULT:
                Protos.RequestVoteResult protoRVResult = Protos.RequestVoteResult.parseFrom(payload);
                out.add(new RequestVoteResult(protoRVResult.getTerm(), protoRVResult.getVoteGranted()));
                break;
            case MessageConstants.MSG_TYPE_APPEND_ENTRIES_RPC:
                Protos.AppendEntriesRpc protoAERpc = Protos.AppendEntriesRpc.parseFrom(payload);
                AppendEntriesRpc aeRpc = new AppendEntriesRpc();
                aeRpc.setMessageId(protoAERpc.getMessageId());
                aeRpc.setTerm(protoAERpc.getTerm());
                aeRpc.setLeaderId(new NodeId(protoAERpc.getLeaderId()));
                aeRpc.setLeaderCommit(protoAERpc.getLeaderCommit());
                aeRpc.setPrevLogIndex(protoAERpc.getPrevLogIndex());
                aeRpc.setPrevLogTerm(protoAERpc.getPrevLogTerm());
                aeRpc.setEntries(protoAERpc.getEntriesList().stream().map(e ->
                        entryFactory.create(e.getKind(), e.getIndex(), e.getTerm(), e.getCommand().toByteArray())
                ).collect(Collectors.toList()));
                out.add(aeRpc);
                break;
            case MessageConstants.MSG_TYPE_APPEND_ENTRIES_RESULT:
                Protos.AppendEntriesResult protoAEResult = Protos.AppendEntriesResult.parseFrom(payload);
                out.add(new AppendEntriesResult(protoAEResult.getRpcMessageId(), protoAEResult.getTerm(), protoAEResult.getSuccess()));
                break;
        }
    }

}
