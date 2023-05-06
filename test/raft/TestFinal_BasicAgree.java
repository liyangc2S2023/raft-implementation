package test.raft;

import remote.RemoteObjectException;
import test.util.*;
import java.util.Random;

/** Performs basic tests on commitment of entries to logs:
    -- does anyone commit anything before startCommit is called?
    -- if we call startCommit a few times, does each lead to a commitment?
 */
public class TestFinal_BasicAgree extends Test {
    /** Test notice. */
    public static final String notice =
        "\nTestFinal_BasicAgree: testing agreement and commitment with no failure\n";

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
        int numPeers = 5, numIters = 3;
        Controller.CommitOutcome co;

        ctrl = new Controller(numPeers, port);

        for(int i = 1; i <= numIters; i++) {
            System.out.print("\tchecking for no early commit ... ");
            co = ctrl.committedLogIndex(i);

            if(co.num > 0) {
                throw new TestFailed("Peers committed before start! (i = " + i + ", num = " + co.num + ")");
            }
            System.out.println("ok");
        
            System.out.print("\tchecking for correct commit and agreement ... ");
            int ind = ctrl.startCommit(i*100, numPeers);
            if(ind != i) {
                throw new TestFailed("Got index " + ind + " instead of " + i);
            }
            System.out.println("ok");
        }
    }
    
    /** Clean up all of the Raft threads and resources. */
    @Override
    protected void clean() {
        ctrl.cleanup();
    }
}

