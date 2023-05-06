package test.raft;

import remote.RemoteObjectException;
import test.util.*;
import java.util.Random;

/** Test reconnection of leader who kept getting requests while disconnected from others:
    -- find the initial leader, partition them, and send them some new commands
    -- send some new commands to the new leader, then disconnect them too
    -- then reconnect old leader to see if conflicts can be resolved with new submissions
    -- after everyone is reconnected, submit one more thing to commit
 */
public class TestFinal_Rejoin extends Test {
    /** Test notice. */
    public static final String notice =
        "\nTestFinal_Rejoin: testing rejoin of partitioned leader\n";

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
        int numPeers = 3, leader1, leader2;
        ctrl = new Controller(numPeers, port);

        // find the leader, partition everyone else, send leader some commands
        leader1 = ctrl.checkOneLeader();
        ctrl.disconnect((leader1 + 1) % numPeers);
        ctrl.disconnect((leader1 + 2) % numPeers);
        ctrl.issueCommand(leader1, 102);
        ctrl.issueCommand(leader1, 103);
        ctrl.issueCommand(leader1, 104);

        // disconnect the leader, reconnect others, give them a little time to elect a leader
        ctrl.disconnect(leader1);
        ctrl.connect((leader1 + 1) % numPeers);
        ctrl.connect((leader1 + 2) % numPeers);
        try { Thread.sleep(RAFT_ELECTION_TIMEOUT); } catch(InterruptedException e) {}
        
        System.out.print("\tchecking commitment after original leader failed ... ");
        // new leader commits for index 2
        ctrl.startCommit(103, numPeers-1);
        System.out.println("ok");
    
        System.out.print("\tchecking log consistency after new leader fails and partitioned leader reconnects ... ");
        // new leader network failure
        leader2 = ctrl.checkOneLeader();
        ctrl.disconnect(leader2);

        // old leader connected again
        ctrl.connect(leader1);
        ctrl.startCommit(104, numPeers-1);
        System.out.println("ok");

        System.out.print("\tchecking log consistency after everyone rejoins ... ");
        // all together now
        ctrl.connect(leader2);
        ctrl.startCommit(105, numPeers);
        System.out.println("ok");
    }
    
    /** Clean up all of the Raft threads and resources. */
    @Override
    protected void clean() {
        ctrl.cleanup();
    }
}

