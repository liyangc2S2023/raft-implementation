package raft;

import java.io.Serializable;

public class RaftLog implements Serializable {
    int msg;
    int term;
    int index;

    public RaftLog(int msg, int term, int index) {
        this.msg = msg;
        this.term = term;
        this.index = index;
    }
}