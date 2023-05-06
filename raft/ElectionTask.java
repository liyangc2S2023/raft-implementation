package raft;

import remote.RemoteObjectException;
import remote.StubFactory;

public class ElectionTask implements Runnable {

    private RaftPeer node;

    public ElectionTask(RaftPeer node) {
        this.node = node;
    }

    @Override
    public void run() {
        if (node.isHeartbeatReceived() || node.getCurrentRole() == RaftRole.LEADER) {
            node.setHeartbeat(false);
        } else {
            // become candidate
            node.toCandidate();
            // start leader election
            int votesReceived = 1;
            RaftLog lastLog = node.getLastLog();
            for (int raftPeerId = 0; raftPeerId < node.numOfPeers; raftPeerId++) {
                try {
                    if (raftPeerId == node.nodeId) continue;
                    String peerAddr = node.getPeerPort(raftPeerId);
                    RaftInterface peer = StubFactory.create(RaftInterface.class, peerAddr);
                    RequestVoteResp resp = peer.RequestVote(
                            node.getCurrentTerm(),
                            node.getVotedFor(),
                            lastLog.index,
                            lastLog.term);
                    node.increaseRpcCallCount();
                    if (resp != null) {
                        if (node.getCurrentRole() == RaftRole.CANDIDATE &&
                                node.getCurrentTerm() == resp.getTerm() &&
                                resp.isGranted())
                        {
                            votesReceived++;
                            if (votesReceived >= (node.numOfPeers + 1) / 2) {
                                node.toLeader();
                                break;
                            }
                        } else if (resp.getTerm() > node.getCurrentTerm()) {
                            node.toFollower(resp.getTerm());
                            break;
                        }
                    }
                } catch (RemoteObjectException e) {
//                    System.out.println("error in election taks: " + e.getMessage());
                }
            }
        }
    }
}
