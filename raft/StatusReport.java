package raft;

import java.io.Serializable;

/**
 * StatusReport object is sent to Controller in response to command and status requests
 */
public class StatusReport implements Serializable {

    public int index;
    public int term;
    public boolean leader;
    public int callCount;
    
    public StatusReport(int index, int term, boolean leader, int callCount) {
        this.index = index;
        this.term = term;
        this.leader = leader;
        this.callCount = callCount;
    }
}

