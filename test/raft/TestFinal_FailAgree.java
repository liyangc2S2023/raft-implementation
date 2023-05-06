package test.raft;

import remote.RemoteObjectException;
import test.util.*;
import java.util.Random;

/** Test agreement among Raft peers with disconnection, specifically:
    -- do a basic commit and check for leader
    -- can we still get agreement with a disconnected peer?
    -- after peer reconnects, can we continue to get agreement?
 */
public class TestFinal_FailAgree extends Test {
    /** Test notice. */
    public static final String notice =
        "\nTestFinal_FailAgree: testing agreement despite follower disconnection\n";

    private static int RAFT_ELECTION_TIMEOUT = 1000;

    /** Prerequisites. */
    public static final Class[] prerequisites = new Class[] { 
        TestCheckpoint_ReElection.class,
        TestFinal_BasicAgree.class 
    };

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
        int numPeers = 5;
        ctrl = new Controller(numPeers, port);

        System.out.print("\tchecking initial commit ... ");
        ctrl.startCommit(101, numPeers);
        // simulate a node failure
        int failedNode = (ctrl.checkOneLeader() + 1) % numPeers;
        ctrl.disconnect(failedNode);
        System.out.println("ok");

        // agree despite one disconnected peer?
        System.out.print("\tchecking agreement with one failed Raft peer ... ");
        ctrl.startCommit(102, numPeers - 1);
        ctrl.startCommit(103, numPeers - 1);
        try { Thread.sleep(RAFT_ELECTION_TIMEOUT); } catch(InterruptedException e) {}
        ctrl.startCommit(104, numPeers - 1);
        ctrl.startCommit(105, numPeers - 1);
        System.out.println("ok");

        //reconnect failed peer
        ctrl.connect(failedNode);

        System.out.print("\tchecking agreement with reconnected peer ... ");
        ctrl.startCommit(106, numPeers);
        try { Thread.sleep(RAFT_ELECTION_TIMEOUT); } catch(InterruptedException e) {}
        ctrl.startCommit(107, numPeers);
        System.out.println("ok");
    }
    
    /** Clean up all of the Raft threads and resources. */
    @Override
    protected void clean() {
        ctrl.cleanup();
    }
}

