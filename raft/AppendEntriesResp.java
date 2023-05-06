package raft;

import java.io.Serializable;

public class AppendEntriesResp implements Serializable {
    private int term;
    private boolean success;
    private int ack;

    public AppendEntriesResp(int term, boolean success, int ack) {
        this.term = term;
        this.success = success;
        this.ack = ack;
    }

    public int getTerm() {
        return term;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getAck() {
        return ack;
    }
}
