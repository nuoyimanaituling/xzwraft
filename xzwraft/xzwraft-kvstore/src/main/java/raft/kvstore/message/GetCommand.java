package raft.kvstore.message;

/**
 * 命令：get
 * @Author xzw
 * @create 2021/9/19 10:40
 */
public class GetCommand {

    private final String key;

    public GetCommand(String key){
        this.key = key;
    }
    public String getKey(){
        return key;
    }

    @Override
    public String toString(){
        return "GetCommand{" + "key='" + key +'\'' + '}';
    }
}
