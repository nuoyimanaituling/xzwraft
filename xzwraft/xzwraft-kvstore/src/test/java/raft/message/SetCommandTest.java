package raft.message;

import org.junit.Assert;
import org.junit.Test;
import raft.kvstore.message.SetCommand;

public class SetCommandTest {

    @Test
    public void test() {
        SetCommand command = new SetCommand("x", "1".getBytes());
        byte[] commandBytes = command.toBytes();
        SetCommand command2 = SetCommand.fromBytes(commandBytes);
        Assert.assertEquals(command.getKey(), command2.getKey());
        Assert.assertArrayEquals(command.getValue(), command2.getValue());
    }

}