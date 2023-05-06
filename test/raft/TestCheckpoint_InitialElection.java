package test.raft;

import test.util.*;
import java.util.Random;

/** Performs basic tests of the Raft election mechanism, specifically:
    <ul>
    <li>is a leader elected?
    <li>if no failure, is leadership stable?
    <li>after multiple timeout durations, is there still a leader?
    </ul>
*/
public class TestCheckpoint_InitialElection extends Test {
    /** Test notice. */
    public static final String notice =
        "\nTestCheckpoint_InitialElection: testing behavior of initial election\n";

    private static int RAFT_ELECTION_TIMEOUT = 1000;

    /** Prerequisites. */
    public static final Class[] prerequisites = new Class[] { TestCheckpoint_Setup.class };

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
        int term1, term2;
        int numPeers = 3;

        ctrl = new Controller(numPeers, port);

        /* is a leader elected? */
        System.out.print("\tchecking for leader ... ");
        ctrl.checkOneLeader();
        System.out.println("ok");

        System.out.print("\tchecking for term agreement ... ");
        term1 = ctrl.checkTerms();
        if(term1 < 1) {
            throw new TestFailed("term is "+ term1 +", but should be at least 1");
        }
        System.out.println("ok");

        try { Thread.sleep(2 * RAFT_ELECTION_TIMEOUT); } catch(InterruptedException e) {}
        term2 = ctrl.checkTerms();
        if(term1 != term2) {
            System.out.println("warning: term changed with no failures.");
        }

        System.out.print("\tchecking there's still a leader ... ");
        ctrl.checkOneLeader();
        System.out.println("ok");
    }
    
    /** Clean up all of the Raft threads and resources. */
    @Override
    protected void clean() {
        ctrl.cleanup();
    }
}

