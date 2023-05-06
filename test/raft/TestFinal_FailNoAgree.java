package test.raft;

import remote.RemoteObjectException;
import raft.StatusReport;
import test.util.*;
import java.util.Random;

/** Test non-agreement among Raft peers with disconnection, specifically:
    -- do we get any agreement when a majority disconnects?
    -- make sure logs are made consistent after everyone reconnects
    -- after reconnection, can we continue to get agreement?
 */
public class TestFinal_FailNoAgree extends Test {
    /** Test notice. */
    public static final String notice =
        "\nTestFinal_FailNoAgree: testing no agreement if too many followers disconnect\n";

    private static int RAFT_ELECTION_TIMEOUT = 1000;

    /** Prerequisites. */
    public static final Class[] prerequisites = new Class[] { TestFinal_FailAgree.class };

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
        int numPeers = 5, leader1 = 0, leader2 = 0;
        StatusReport rep1, rep2;
        Controller.CommitOutcome co;
        ctrl = new Controller(numPeers, port);


        ctrl.startCommit(10, numPeers);
        leader1 = ctrl.checkOneLeader();

        System.out.print("\tchecking for no agreement when majority of followers fail ... ");
        ctrl.disconnect((leader1 + 1) % numPeers);
        ctrl.disconnect((leader1 + 2) % numPeers);
        ctrl.disconnect((leader1 + 3) % numPeers);

        rep1 = ctrl.issueCommand(leader1, 20);
        if(!rep1.leader) {
            throw new TestFailed("Leader rejected a client command");
        }
        if(rep1.index != 2) {
            throw new TestFailed("Got index " + rep1.index + " instead of 2");
        }

        try { Thread.sleep(2 * RAFT_ELECTION_TIMEOUT); } catch(InterruptedException e) {}

        co = ctrl.committedLogIndex(rep1.index);
        
        if(co.num > 0) {
            throw new TestFailed(co.num + " peers committed without a majority");
        }
        System.out.println("ok");

        //repair
        ctrl.connect((leader1 + 1) % numPeers);
        ctrl.connect((leader1 + 2) % numPeers);
        ctrl.connect((leader1 + 3) % numPeers);

        System.out.print("\tchecking for consistent logs after everyone reconnects ... ");
        leader2 = ctrl.checkOneLeader();
        rep2 = ctrl.issueCommand(leader2, 30);
        if(!rep2.leader) {
            throw new TestFailed("Leader rejected a client command");
        }

        // reconnected peers may still have the 2nd entry or they may have deleted it
        if(rep2.index < 2 || rep2.index > 3) {
            throw new TestFailed("Unexpected index " + rep2.index + ", should be 2 or 3");
        }
        System.out.println("ok");

        System.out.print("\tchecking that more commits can be done after recovery ... ");
        ctrl.startCommit(100, numPeers);
        System.out.println("ok");
    }
    
    /** Clean up all of the Raft threads and resources. */
    @Override
    protected void clean() {
        ctrl.cleanup();
    }
}

