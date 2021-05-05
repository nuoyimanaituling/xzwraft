package in.xzw.xzwraft.core.log.entry;

// 定义logentry
public interface Entry {

    int KIND_NO_OP =0;
    int KIND_GENERAL =1;
   //获取元数据信息 index，term，kind
    EntryMeta getMeta();

    byte[] getCommandBytes();

    int getKind();

    int getIndex();

    int getTerm();

}
