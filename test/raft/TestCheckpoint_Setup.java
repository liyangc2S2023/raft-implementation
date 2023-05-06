package test.raft;

import remote.RemoteObjectException;
import test.util.*;
import java.util.Random;

/** Performs basic tests on initial setup and use of remote interfaces */
public class TestCheckpoint_Setup extends Test {
    /** Test notice. */
    public static final String notice =
        "\nTestCheckpoint_Setup: testing initial setup of Raft remote interfaces\n";

    private static int RAFT_ELECTION_TIMEOUT = 1000;

    /** Prerequisites. */
//    public static final Class[] prerequisites = new Class[] { TestFinal_Connection.class };

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
        int numPeers = 1;

        System.out.print("\tchecking controller creation ... ");
        ctrl = new Controller(numPeers, port);
        System.out.println("ok");

        System.out.print("\tchecking controller can get peer status ... ");
        try {
            ctrl.peers[0].GetStatus();
        } catch(RemoteObjectException r) {
            throw new TestFailed("remote error getting status from Raft peer: "+r.getMessage());
        }
        System.out.println("ok");
    }
    
    /** Clean up all of the Raft threads and resources. */
    @Override
    protected void clean() {
        ctrl.cleanup();
    }
}

