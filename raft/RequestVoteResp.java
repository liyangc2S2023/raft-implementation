package raft;

import java.io.Serializable;

public class RequestVoteResp implements Serializable {
    private int term;
    private boolean granted;

    public RequestVoteResp(int term, boolean granted) {
        this.term = term;
        this.granted = granted;
    }

    public int getTerm() {
        return term;
    }

    public boolean isGranted() {
        return granted;
    }
}
