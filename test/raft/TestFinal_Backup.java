package test.raft;

import remote.RemoteObjectException;
import test.util.*;
import java.util.Random;

/** Test resolution of logs populated with lots of uncommitted entries:
    -- find the initial leader, partition them with a follower, and send them lots of new commands
    -- then send lots of new commands to the larger partition, then disconnect that leader with a follower
       and send lots more commands to the disconnected leader
    -- then reconnect the original leader and send them lots of new commands
    -- reconnect everyone, submit one more thing to commit, ensure consistency of logs
 */
public class TestFinal_Backup extends Test {
    /** Test notice. */
    public static final String notice =
        "\nTestFinal_Backup: testing leader backs up quickly over incorrect follower logs\n";

    private static int RAFT_ELECTION_TIMEOUT = 1000;

    /** Prerequisites. */
    public static final Class[] prerequisites = new Class[] { TestFinal_FailNoAgree.class };

    /** Port number to start sequence of Raft listeners, each incremented by 1. */
    private int port;
    /** Controller of Raft peers for testing. */
    private Controller ctrl;

    /** Initializes the test. */
    @Override
    protected void initialize() throws TestFailed {
        Random rng = new Random(System.nanoTime());
        port = rng.nextInt(10000) + 7000;
    }

    /** Performs the test. */
    @Override
    protected void perform() throws TestFailed {
        int numPeers = 5, leader1 = 0, leader2 = 0, i = 0, other = 0;
        ctrl = new Controller(numPeers, port);
        
        ctrl.startCommit(109, numPeers);
        
        System.out.print("\tchecking commitment among separate partitions ... ");
        // disconnect everyone except leader and one follower
        leader1 = ctrl.checkOneLeader();
        ctrl.disconnect((leader1 + 2) % numPeers);
        ctrl.disconnect((leader1 + 3) % numPeers);
        ctrl.disconnect((leader1 + 4) % numPeers);

        // submit lots of commands that won't commit
        for(i = 0; i < 25; i ++) {
            ctrl.issueCommand(leader1, 110+i);
        }
        
        try { Thread.sleep(RAFT_ELECTION_TIMEOUT / 2); } catch(InterruptedException e) {}
    
        // "swap" connectivity to the other partition
        ctrl.disconnect(leader1);
        ctrl.disconnect((leader1 + 1) % numPeers);
        ctrl.connect((leader1 + 2) % numPeers);
        ctrl.connect((leader1 + 3) % numPeers);
        ctrl.connect((leader1 + 4) % numPeers);

        // lots of successful commands to new group.
        for(i = 0; i < 25; i ++) {
            ctrl.startCommit(160 + i, numPeers / 2 + 1);
        }
        System.out.println("ok");

        System.out.print("\tchecking consistency after small partition recovers above majority ... ");
        // now another partitioned leader and one follower
        leader2 = ctrl.checkOneLeader();
        other = (leader1 + 2) % numPeers;
        if(leader2 == other) {
            other = (leader2 + 1) % numPeers;
        }

        ctrl.disconnect(other);

        // lots more commands that wont get committed
        for(i = 0; i < 25; i ++) {
            ctrl.issueCommand(leader2, 210+i);
        }
        
        try { Thread.sleep(RAFT_ELECTION_TIMEOUT / 2); } catch(InterruptedException e) {}
        
        // bring original leader back to life
        for(i = 0; i < numPeers; i++) {
            ctrl.disconnect(i);
        }

        ctrl.connect(leader1);
        ctrl.connect((leader1 + 1) % numPeers);
        ctrl.connect(other);

        // lots of successful commands
        for(i = 0; i < 25; i ++) {
            ctrl.startCommit(260 + i, 3);
        }
        System.out.println("ok");

        System.out.print("\tchecking overall consistency after everyone reconnects ... ");
        // now everyone
        for(i = 0; i < numPeers; i++) {
            ctrl.connect(i);
        }
        ctrl.startCommit(500, numPeers);
        System.out.println("ok");
    }
    
    /** Clean up all of the Raft threads and resources. */
    @Override
    protected void clean() {
        ctrl.cleanup();
    }
}

