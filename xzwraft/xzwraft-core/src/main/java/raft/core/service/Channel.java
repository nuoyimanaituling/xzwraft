package raft.core.service;

public interface Channel {

    Object send(Object payload);

}
