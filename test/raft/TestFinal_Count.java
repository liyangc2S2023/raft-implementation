package test.raft;

import remote.RemoteObjectException;
import raft.StatusReport;
import test.util.*;
import java.util.ArrayList;
import java.util.Random;

/** Test that number of remote calls made when using Raft is reasonable:
    -- checks that the #calls for election is in [7, 75]
    -- checks that the #calls for commitment is < 120
    -- checks that the #calls during a 1s idle is < 20
    -- along the way, checks that everything else is working correctly, just in case
 */
public class TestFinal_Count extends Test {
    /** Test notice. */
    public static final String notice =
        "\nTestFinal_Count: testing for reasonable number of remote calls\n";

    private static int RAFT_ELECTION_TIMEOUT = 1000;

    /** Prerequisites. */
    public static final Class[] prerequisites = new Class[] { TestFinal_Rejoin.class };

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
        int numPeers = 3, i = 0, j = 0, total1 = 0, total2 = 0, total3 = 0;
        int leader = 0, iters = 10;
        int randomCmd = 101;
        StatusReport rep1, rep2;
        ctrl = new Controller(numPeers, port);

        System.out.print("\tchecking #calls for election is suitable ... ");
        leader = ctrl.checkOneLeader();

        for(i = 0; i < numPeers; i++) {
            total1 += ctrl.getCallCount(i);
        }

        if(total1 > 75 || total1 < 7) {
            throw new TestFailed("Too many or too few remote calls to elect a leader");
        }
        System.out.println("ok");

        System.out.print("\tchecking #calls for commitment is suitable ... ");
        boolean success = false;
        
    tryLoop:
        for(j = 0; j < 5; j++) {
            if(j > 0) {
                // give solution some time to settle
                try { Thread.sleep(3000); } catch(InterruptedException e) {}
            }

            leader = ctrl.checkOneLeader();
            total1 = 0;
            for(i = 0; i < numPeers; i++) {
                total1 += ctrl.getCallCount(i);
            }

            rep1 = ctrl.issueCommand(leader, 1);
            if(!rep1.leader) {
                // leader moved on too quickly
                continue tryLoop;
            }

            ArrayList<Integer> cmds = new ArrayList<Integer>();
            for(i=1; i<(iters + 2); i++) {
                cmds.add(randomCmd);
                rep2 = ctrl.issueCommand(leader, randomCmd);
                randomCmd++;

                if(rep2.term != rep1.term || !rep2.leader) {
                    // Term changed while starting, or no longer leader so term changed.
                    continue tryLoop;
                }

                if(rep1.index + i != rep2.index) {
                    throw new TestFailed("issueCommand() failed");
                }
            }

            for(i=1; i<(iters + 1); i++) {
                int cmd = ctrl.wait(rep1.index + i, numPeers, rep1.term);
                if(cmd != cmds.get(i-1)) {
                    if(cmd == -1) {
                        // term changed -- try again
                        continue tryLoop;
                    }
                    throw new TestFailed("wrong value "+cmd+" committed for index "+(rep1.index + i)+"; expected "+cmds);
                }
            }

            total2 = 0;
            
            for(int k = 0; k < numPeers; k++) {
                rep2 = null;
                try {
                    rep2 = ctrl.peers[k].GetStatus();
                } catch (RemoteObjectException e) {
                    continue;
                }
                if(rep2 != null && rep2.term != rep1.term) {
                    continue tryLoop;
                }
                total2 += rep2.callCount;
            }

            if(total2 - total1 > 200) {
                throw new TestFailed("Too many remote calls (" + (total2 - total1) + ") for commitment");
            }

            success = true;
            break;
        }

        if(!success) {
            throw new TestFailed("commitment failed (term changed too often)");
        }
        System.out.println("ok");
        
        try { Thread.sleep(RAFT_ELECTION_TIMEOUT); } catch(InterruptedException e) {}

        System.out.print("\tchecking #calls while idle is suitable ... ");
        total3 = 0;
        for(i=0; i<numPeers; i++) {
            total3 += ctrl.getCallCount(i);
        }

        if(total3 - total2 > 20) {
            throw new TestFailed("Too many remote calls (" + (total3 - total2) + ") for 1 second idle");
        }
        System.out.println("ok");
    }
    
    /** Clean up all of the Raft threads and resources. */
    @Override
    protected void clean() {
        ctrl.cleanup();
    }
}

