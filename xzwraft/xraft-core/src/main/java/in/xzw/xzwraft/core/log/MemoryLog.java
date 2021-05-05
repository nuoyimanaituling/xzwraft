package in.xzw.xzwraft.core.log;

import com.google.common.eventbus.EventBus;

import in.xzw.xzwraft.core.log.sequence.GroupConfigEntryList;
import in.xzw.xzwraft.core.node.NodeEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.Set;


@NotThreadSafe
public class MemoryLog extends AbstractLog {

    private static final Logger logger = LoggerFactory.getLogger(MemoryLog.class);

//    private final StateMachineContextImpl stateMachineContext = new StateMachineContextImpl();
    public MemoryLog(){
        super(new EventBus());
    }

    public MemoryLog(EventBus eventBus, Set<NodeEndpoint> initialGroup) {
        super(eventBus);
        this.groupConfigEntryList = new GroupConfigEntryList(initialGroup);

    }
//    MemoryLog(EntrySequence entrySequence){
//      this.entrySequence =entrySequence;
//  }
}
