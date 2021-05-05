package in.xzw.xzwraft.kvstore.message;



public class GetCommand {


    public GetCommand(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    @Override
    public String toString() {
        return "GetCommand{" +
                "key='" + key + '\''+
                '}';
    }

    private final String key;



}
