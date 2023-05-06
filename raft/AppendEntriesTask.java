package raft;

import remote.RemoteObjectException;
import remote.StubFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AppendEntriesTask extends Thread {
    private RaftPeer node;
    private int followerId;
    private volatile boolean shutdown = false;

    public AppendEntriesTask(RaftPeer node, int followerId) {
        this.node = node;
        this.followerId = followerId;
    }

    @Override
    public void run() {
        while (!shutdown && node.getCurrentRole() == RaftRole.LEADER) {
            try {
                int prevLogIndex = node.getNextIndex()[followerId] - 1;
                int prevLogTerm = prevLogIndex == 0 ? 0 : node.getLogs().get(prevLogIndex - 1).term;
                String peerAddr = node.getPeerPort(followerId);
                RaftInterface peer = StubFactory.create(RaftInterface.class, peerAddr);
                AppendEntriesResp resp = peer.AppendEntries(
                        node.getCurrentTerm(),
                        node.nodeId,
                        prevLogIndex,
                        prevLogTerm,
                        node.getEntriesToAppend(prevLogIndex),
                        node.getCommitIndex()
                );
                node.callCount++;
                if (resp != null) {
                    if (resp.getTerm() > node.getCurrentTerm()) {
                        node.toFollower(resp.getTerm());
                    } else {
                        node.updateNextIndexAndMatchIndex(followerId, resp.getAck(), resp.isSuccess());
                        if (resp.isSuccess()) {
                            shutdown();
                        }
                    }
                }
            } catch (RemoteObjectException e) {

            }
        }
        node.updateCommitIndex();
    }

    public void shutdown() {
        this.shutdown = true;
    }
}
