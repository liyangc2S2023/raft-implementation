package test.raft;

import test.util.*;
import java.util.Random;

/** Performs tests of the Raft re-election mechanism with network failure:
    <ul>
    <li>is a leader elected?
    <li>if leader disconnects, is a new one elected?
    <li>if the old leader reconnects, does it affect the new leader?
    <li>if majority peers disconnect, is there no leader?
    <li>if a peer reconnect to exceed majority, is there a leader?
    <li>if more reconnections, is there still a leader?
    </ul>
*/
public class TestCheckpoint_ReElection extends Test {
    /** Test notice. */
    public static final String notice =
        "\nTestCheckpoint_ReElection: testing behavior of election after network failure\n";

    private static int RAFT_ELECTION_TIMEOUT = 1000;

    /** Prerequisites. */
    public static final Class[] prerequisites = new Class[] { TestCheckpoint_InitialElection.class };

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
        int leader1, leader2;
        int numPeers = 3;

        ctrl = new Controller(numPeers, port);

        /* is a leader elected? */
        System.out.print("\tchecking for leader ... ");
        leader1 = ctrl.checkOneLeader();
        System.out.println("ok");

        /* if the leader disconnects, a new one should be elected. */
        System.out.print("\tchecking for new leader after disconnecting previous leader ... ");
        try { Thread.sleep(RAFT_ELECTION_TIMEOUT); } catch(InterruptedException e) {}
        ctrl.disconnect(leader1);
        ctrl.checkOneLeader();
        System.out.println("ok");

        /* if the old leader rejoins, that shouldn't disturb the current leader. */
        System.out.print("\tchecking for leader after previous leader reconnects ... ");
        try { Thread.sleep(RAFT_ELECTION_TIMEOUT); } catch(InterruptedException e) {}
        ctrl.connect(leader1);
        leader2 = ctrl.checkOneLeader();
        System.out.println("ok");

        /* if there's no quorum, no leader should be elected. */
        System.out.print("\tchecking for no leader after majority disconnects ... ");
        try { Thread.sleep(RAFT_ELECTION_TIMEOUT); } catch(InterruptedException e) {}
        ctrl.disconnect(leader2);
        ctrl.disconnect((leader2 + 1) % numPeers);
        ctrl.checkNoLeader();
        System.out.println("ok");

        /* if a quorum arises, it should elect a leader. */
        System.out.print("\tchecking for leader after follower reconnection ... ");
        ctrl.connect((leader2 + 1) % numPeers);
        ctrl.checkOneLeader();
        System.out.println("ok");

        /* re-join of previous leader shouldn't disturb the newly elected leader */
        System.out.print("\tchecking for leader after previous leader reconnection ... ");
        ctrl.connect(leader2);
        ctrl.checkOneLeader();
        System.out.println("ok");
    }
    
    /** Clean up all of the Raft threads and resources. */
    @Override
    protected void clean() {
        ctrl.cleanup();
    }
}

