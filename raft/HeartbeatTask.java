package raft;

import remote.RemoteObjectException;
import remote.StubFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class HeartbeatTask implements Runnable {
    private RaftPeer node;

    public HeartbeatTask(RaftPeer node) {
        this.node = node;
    }

    @Override
    public void run() {
        if (node.getCurrentRole() != RaftRole.LEADER) return;
        for (int raftPeerId = 0; raftPeerId < node.numOfPeers; raftPeerId++) {
            try {
                if (raftPeerId == node.nodeId) continue;
                int prevLogIndex = node.getNextIndex()[raftPeerId] - 1;
                int prevLogTerm = prevLogIndex == 0 ? 0 : node.getLogs().get(prevLogIndex - 1).term;
                String peerAddr = node.getPeerPort(raftPeerId);
                RaftInterface peer = StubFactory.create(RaftInterface.class, peerAddr);
                AppendEntriesResp resp = peer.AppendEntries(
                        node.getCurrentTerm(),
                        node.nodeId,
                        prevLogIndex,
                        prevLogTerm,
                        new ArrayList<>(),
                        node.getCommitIndex()
                );
                node.callCount++;
                if (resp != null) {
                    if (resp.getTerm() > node.getCurrentTerm()) {
                        node.toFollower(resp.getTerm());
                    } else {
                        if (!resp.isSuccess()) {
                            AppendEntriesTask th = new AppendEntriesTask(node, raftPeerId);
                            th.start();
                        }
                    }
                }
            } catch (RemoteObjectException e) {
//                System.out.println("error is heartbeat task" + e.getMessage());
            }
        }
    }
}
