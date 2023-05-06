package test.raft;

import test.util.*;
import raft.*;
import remote.*;
import java.util.ArrayList;
import java.util.HashMap;

public class Controller {

    private int numPeers;           /* Number of Raft peers in this network. */
    private int basePort;           /* starting service port number, incremented by 1 for each peer */
    private RaftControlThread thr[];/* Threads controlling Raft peers. */
    public RaftInterface[] peers;   /* set of Raft peer interfaces to interact for testing purposes */

    /* Creates a configuration to be used by a tester */
    public Controller(int n, int prt) throws TestFailed {
        numPeers = n;
        basePort = prt;
        thr = new RaftControlThread[numPeers];
        peers = new RaftInterface[numPeers];

        for(int i = 0; i < numPeers; i++) {
            /* Create a new Raft peer. */
            int portI = basePort + i;
            thr[i] = new RaftControlThread(portI, i, numPeers);
            thr[i].start();
            try {
                peers[i] = StubFactory.create(RaftInterface.class, "127.0.0.1:"+Integer.toString(portI));  // stub for checking raft peer status
            } catch(Throwable t) {
                throw new TestFailed("Cannot create Controller stub for Raft peer:", t);
            }
        }
        
        try { Thread.sleep(5000); } catch(InterruptedException e) {};
    }

    public void connect(int i) {
        thr[i].setActive(true);
        try { Thread.sleep(100); } catch(InterruptedException e) {};
    }

    public void disconnect(int i) {
        thr[i].setActive(false);
        try { Thread.sleep(100); } catch(InterruptedException e) {};
    }

    /**
     * CommitOutcome is an object that we collect to evaluate success of replication
     *
     * @param num -- number of peers counting a committed log entry
     * @param cmd -- the specific command that they are checking in the log
     */
    public class CommitOutcome {
        public int num;
        public int cmd;
        
        public CommitOutcome(int n, int c) {
            num = n;
            cmd = c;
        }
        
        public String toString() {
            return "(num:" + num + ", cmd:" + cmd + ")";
        }
    }

    public CommitOutcome committedLogIndex(int index) throws TestFailed {
        int count = 0, cmd = -1;
        int logcmd = 0;

        for(int i = 0; i < numPeers; i++) {
            try {
                logcmd = peers[i].GetCommittedCmd(index);
            } catch (RemoteObjectException e) {}
            if (logcmd > 0) {
                int cmd1 = logcmd;
                if(count > 0 && cmd != cmd1) {
                    throw new TestFailed("Committed values do not match: index " + index + " " + cmd + " " + cmd1);
                }
                count++;
                cmd = cmd1;
            }
        }
        return new CommitOutcome(count, cmd);
    }

    /* Check that there's exactly one leader. */
    public int checkOneLeader() throws TestFailed {
        int lastTermWithLeader = -1;

        for(int iter = 0; iter < 10; iter++) {
            try { Thread.sleep(500); } catch(InterruptedException e) {}
            HashMap<Integer, ArrayList<Integer>> leaders = new HashMap<Integer, ArrayList<Integer>>();
            boolean skip = false;
            for(int i = 0; i < numPeers; i++) {
                StatusReport status = null;
                try {
                    status = peers[i].GetStatus();
                } catch (RemoteObjectException e) {
                    continue;
                }
                if(status != null && status.leader) {
                    if (leaders.containsKey(status.term)) {
                        leaders.get(status.term).add(i);
                    } else {
                        ArrayList<Integer> arr = new ArrayList<Integer>();
                        arr.add(i);
                        leaders.put(status.term, arr);
                    }
                }
            }

            lastTermWithLeader = -1;
            for(Integer term : leaders.keySet()) {
                ArrayList<Integer> leadersPerTerm = leaders.get(term);
                if(leadersPerTerm.size() > 1) {
                    throw new TestFailed("Term " + term + " has " + leadersPerTerm.size() + " (>1) leaders");
                }

                if(term > lastTermWithLeader) {
                    lastTermWithLeader = term;
                }
            }

            if(leaders.size() != 0) {
                ArrayList<Integer> leadersPerTerm = leaders.get(lastTermWithLeader);
                return leadersPerTerm.get(0);
            }
        }
        
        throw new TestFailed("No leader found!");
    }

    /* Check that everyone agrees on the term. */
    public int checkTerms() throws TestFailed {
        int term = -1;
        for(int i = 0; i < numPeers; i++) {
            StatusReport status = null;
            try {
                status = peers[i].GetStatus();
            } catch (RemoteObjectException e) {
                continue;
            }
            if(status != null) {
                if(term == -1) {
                    term = status.term;
                } else if(term != status.term) {
                    throw new TestFailed("Peers disagree about term!");
                }
            }
        }
        return term;
    }

    /* Check that there's no leader. */
    public void checkNoLeader() throws TestFailed {
        for(int i = 0; i < numPeers; i++) {
            StatusReport status = null;
            try {
                status = peers[i].GetStatus();
            } catch (RemoteObjectException e) {
                continue;
            }
            if(status != null && status.leader) {
                throw new TestFailed("Expected no leader, but "+ i + " claims to be leader");
            }
        }
    }

    public StatusReport issueCommand(int id, int cmd) {
        StatusReport reply = null;
        try {
            reply = peers[id].NewCommand(cmd);
        } catch(RemoteObjectException e) {
            return null;
        }
        return reply;
    }

    public int startCommit(int cmd, int expectedPeers) throws TestFailed {
        long t0 = System.currentTimeMillis();

        while(System.currentTimeMillis() - t0 < 20000 ) { /* Wait a while before giving up. */
            int index = -1;
            StatusReport reply;
            for(int i = 0; i < numPeers; i++) {
                reply = null;
                try {
                    reply = peers[i].NewCommand(cmd);
                } catch(RemoteObjectException e) {
                    continue;
                }
                if(reply.leader) {
                    index = reply.index;
                    break;
                }
            }

            if(index != -1) {
                long t1 = System.currentTimeMillis();
                while(System.currentTimeMillis() - t1 < 3000 ) { /* Wait for a bit before giving up. */
                    CommitOutcome co = committedLogIndex(index);
                    if(co.cmd == cmd && co.num >= expectedPeers) {
                        /* this is exactly what we wanted! */
                        return index;
                    }
                    try { Thread.sleep(200); } catch (InterruptedException e) {}
                }
            } else {
                try { Thread.sleep(250); } catch (InterruptedException e) {}
            }
        }

        throw new TestFailed("startCommit(" + cmd + ") failed to reach agreement");
    }

    public int wait(int index, int n, int startTerm) throws TestFailed {
        long t0 = 10; /* 10 miliseconds. */

        for(int iter = 0; iter < 30; iter++) {
            CommitOutcome reply = committedLogIndex(index);
            if(reply.num >= n) {
                break;
            }
            try { Thread.sleep(t0); } catch (InterruptedException e) {}
            if(t0 < 1000) { /* Less than a second. */
                t0 *= 2;
            }

            if(startTerm > -1) {
                for(int j = 0; j < this.numPeers; j++) {
                    StatusReport status = null;
                    try {
                        status = peers[j].GetStatus();
                    } catch (RemoteObjectException e) {
                        continue;
                    }
                    if(status != null && status.term > startTerm) {
                        /* someone moved on, can't guarantee we'll "win" */
                        return -1;
                    }
                }
            }
        }
        CommitOutcome reply = committedLogIndex(index);
        if(reply.num < n) {
            throw new TestFailed("only " + reply.num + " decided for index " + index + "; wanted " + n);
        }
        return reply.cmd;
    }

    public void cleanup() {
        for(int i = 0; i < numPeers; i++)
            thr[i].terminate();
        try { Thread.sleep(100); } catch(InterruptedException e) {};
    }

    public int getCallCount(int id) {
        StatusReport reply = null;
        try {
            reply = peers[id].GetStatus();
        } catch(RemoteObjectException e) {
            return 0;
        }
        return reply.callCount;
    }

    class RaftControlThread extends Thread {
        private RaftPeer rf;
        private int idx;
        private boolean active;
        private boolean alive;

        /**
         * Constructor for RaftControlThread, used by Controller
         * 
         * @param prt   peer's service port number
         * @param ix    peer's index among peers
         * @param num   number of raft peers in the system
         */
        public RaftControlThread(int prt, int ix, int num) {
            idx = ix;
            rf = new RaftPeer(prt, ix, num);
            this.active = true;
            this.alive = true;
        }

        public void setActive(boolean act) {
            this.active = act;
        }

        public void terminate() {   
            this.alive = false;
        }

        /**
         * run method to control the RafPeer for testing
         * - runs until terminate is called
         * - switches between active and inactive using setActive method
         */
        public void run() {
            while(this.alive) {
                rf.Activate();
                while(this.alive && this.active) {
                    try { Thread.sleep(20); } catch(InterruptedException e) {};
                }

                rf.Deactivate();
                while(this.alive && !this.active) {
                    try { Thread.sleep(20); } catch(InterruptedException e) {};
                }
            }
        }
    }
}

