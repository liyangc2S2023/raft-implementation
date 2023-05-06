package raft;

import remote.RemoteObjectException;
import remote.Service;
import remote.StubFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * RaftPeer class represents a Raft peer node that interacts with others using remote calls
 *  -- each RaftPeer supports a Service interface to accept incoming remote calls per RaftInterface
 *  -- each RaftPeer holds a list of stub interfaces to request remote calls per RaftInferface
 *  -- all remote calls are implemented using the underlying remote object library
 */
public class RaftPeer implements RaftInterface {
    // Constructor params
    int port;
    int nodeId;
    int numOfPeers;
    Service<RaftInterface> service;

    private boolean receivedHeartbeat;

    // Persistent state
    private int currentTerm;
    private Integer votedFor;
    private final List<RaftLog> logs;

    // Volatile state
    private int commitIndex;
    private int lastApplied;

    // Volatile state for leaders
    private int[] nextIndex;
    private int[] matchIndex;
    private RaftRole currentRole;

    private ScheduledFuture electionTimer;
    private ScheduledFuture heartbeatTimer;
    private ScheduledExecutorService electionScheduler;
    private ScheduledExecutorService heartbeatScheduler;
    private int electionInterval;
    private int replicationOkCount;
    int callCount;
    boolean isNodeActivated;
    private List<AppendEntriesTask> runningTasks;
    private boolean debug = false;
    /**
     * Constructor for RaftPeer
     * 
     * @param port    peer's service port number
     * @param id     peer's id/index among peers
     * @param num    number of peers in the system
     *
     * port numbers are assigned sequentially to peers from id = 0 to id = num-1, so any peer
     * can determine the port numbers of other peers from the give parameters
     */
    public RaftPeer(int port, int id, int num) {
        //        
        // when a new Raft peer is created, its initial state should be populated into suitable object
        // member variables, and its remote Service and StubFactory components should be created,
        // but the Service should not be started (the Controller will do that when ready).
        // 
        // the remote Service should be bound to the given port number.  each stub created by the
        // StubFactory will be used to interact with a different Raft peer, and different port numbers
        // are used for each Raft peer.  the Controller assigns these port numbers sequentially, starting
        // from peer with `id = 0` and ending with `id = num-1`, so any peer who knows its own
        // `id`, `port`, and `num` can determine the port number used by any other peer.
        this.port = port;
        this.nodeId = id;
        this.numOfPeers = num;
        this.logs = new ArrayList<>();
        this.receivedHeartbeat = false;
        this.callCount = 0;
        this.currentTerm = 0;
        this.commitIndex = 0;
        this.votedFor = null;
        this.currentRole = RaftRole.FOLLOWER;
        service = new Service<>(RaftInterface.class, this, port);
        electionScheduler = Executors.newSingleThreadScheduledExecutor();
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
        this.nextIndex = new int[num];
        this.matchIndex = new int[num];
        this.electionInterval = getElectionTimeout(200, 400);
        this.isNodeActivated = false;
        this.replicationOkCount = 0;
        this.runningTasks = new ArrayList<>();
    }

    @Override
    public RequestVoteResp RequestVote(int candidateTerm,
                                       int candidateId,
                                       int candidateLastLogIndex,
                                       int candidateLastLogTerm)
            throws RemoteObjectException
    {
        if (candidateTerm > currentTerm) {
            toFollower(candidateTerm);
        }
        int lastTerm = getLastLog().term;
        boolean requestApproved = false;
        boolean isLogOk = (candidateLastLogTerm > lastTerm) ||
                (candidateLastLogTerm == lastTerm && candidateLastLogIndex >= logs.size());
        if (candidateTerm == currentTerm && isLogOk && (votedFor == null || votedFor == candidateId)) {
            synchronized (this) {
                votedFor = candidateId;
                requestApproved = true;
                listenForHeartbeat();
            }
        }
        return new RequestVoteResp(currentTerm, requestApproved);
    }

    @Override
    public AppendEntriesResp AppendEntries(
            int leaderTerm,
            int leaderId,
            int prevLogIndex,
            int prevLogTerm,
            List<RaftLog> entries,
            int leaderCommit) throws RemoteObjectException
    {
        setHeartbeat(true);
        if (leaderTerm >= currentTerm) {
            toFollower(leaderTerm);
        }
        boolean isLogMatch = (logs.size() >= prevLogIndex) &&
                (prevLogIndex == 0 || logs.get(prevLogIndex - 1).term == prevLogTerm);
        if (leaderTerm == currentTerm && isLogMatch) {
            // append entries
            // 3. If an existing entry conflicts with a new one (same index but different terms),
            // delete the existing entry and all that follow it (§5.3)
            synchronized (this) {
                if (!entries.isEmpty()) {
                    int lastLogIndex = getLastLog().index;
                    while (lastLogIndex != prevLogIndex) {
                        logs.remove(logs.size() - 1);
                        lastLogIndex--;
                    }
                }
                // 4. Append any new entries not already in the log
                logs.addAll(entries);
                // 5. If leaderCommit > commitIndex, set commitIndex = min(leaderCommit, index of last new entry)
                if (leaderCommit > commitIndex) {
                    commitIndex = Math.min(leaderCommit, getLastLog().index);
                }
                return new AppendEntriesResp(currentTerm,true, logs.size());
            }
        } else {
            return new AppendEntriesResp(currentTerm,false, 0);
        }
    }

    @Override
    public int GetCommittedCmd(int index) throws RemoteObjectException {
//        System.out.println("received index: " + index + getPersistentState());
        for (RaftLog entry: logs) {
            if (entry.index == index && entry.index <= commitIndex) {
                return entry.msg;
            }
        }
        return 0;
    }

    @Override
    public StatusReport GetStatus() throws RemoteObjectException {
        if (!isNodeActivated) {
            throw new RemoteObjectException("node deactivated");
        }
        return new StatusReport(getLastLog().index, currentTerm, currentRole == RaftRole.LEADER, callCount);
    }

    @Override
    public StatusReport NewCommand(int command) throws RemoteObjectException {
        if (currentRole == RaftRole.LEADER) {
            addNewCommandToLog(command);
            for (int peerId = 0; peerId < numOfPeers; peerId++) {
                if (peerId == nodeId || getLastLog().index < nextIndex[peerId]) continue;
                AppendEntriesTask th = new AppendEntriesTask(this, peerId);
                th.start();
                runningTasks.add(th);
            }
        }
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        for (AppendEntriesTask t: runningTasks) {
            t.shutdown();
        }
        return GetStatus();
    }

    synchronized RaftLog addNewCommandToLog(int command) {
        RaftLog entry = new RaftLog(command, currentTerm, logs.size() + 1);
        logs.add(entry);
        nextIndex[nodeId]++;
        replicationOkCount = 0;
        return entry;
    }

    synchronized List<RaftLog> getEntriesToAppend(int prevLogIndex) {
        List<RaftLog> entriesToAppend = new ArrayList<>();
        for (RaftLog entry: logs) {
            if (entry.index > prevLogIndex) {
                entriesToAppend.add(entry);
            }
        }
        return entriesToAppend;
    }

    synchronized void updateCommitIndex() {
        for (int N = getLastLog().index; N > commitIndex; N--) {
            int count = 1;
            for (int j = 0; j < numOfPeers; j++) {
                if (matchIndex[j] >= N) {
                    count++;
                }
            }
            if (count >= (numOfPeers + 1) / 2 && (N>0 && logs.get(N - 1).term == currentTerm)) {
                commitIndex = Math.max(commitIndex, N);
                matchIndex[nodeId] = commitIndex;
                break;
            }
        }
    }


    /* Activate -- this method operates on your Raft peer struct and initiates functionality
     * to allow the Raft peer to interact with others.  before the peer is activated, it can 
     * have internal algorithm state, but it cannot make remote calls using its stubs or receive
     * remote calls using its underlying remote.Service interface.  in essence, when not activated, 
     * the Raft peer is "sleeping" from the perspective of any other Raft peer.
     *
     * this method is used exclusively by the Controller whenever it needs to "wake up" the Raft
     * peer and allow it to start interacting with other Raft peers.  this is used to emulate
     * connecting a new peer to the network or recovery of a previously failed peer.
     *
     * when this method is called, the Raft peer should do whatever is necessary to enable its 
     * remote.Service interface to support remote calls from other Raft peers as soon as the method 
     * returns (i.e., if it takes time for the remote.Service to start, this method should not 
     * return until that happens).  the method should not otherwise block the Controller, so it may 
     * be useful to spawn go routines from this method to handle the on-going operation of the Raft
     * peer until the remote.Service stops.
     *
     * given a RaftPeer object `rf`, the Controller will call this method as `rf.Activate()`,
     * so you should define this method accordingly. NOTE: this is _not_ a remote call using the 
     * remote Service interface of the Raft peer.  it uses direct method calls from the Controller, 
     * and is used purely for the purposes of the test code.  you should not use this method for
     * any messaging between Raft peers.
     *
     * TODO: implement the `Activate` method
     */
    public void Activate() {
        try {
            this.service.start();
            this.isNodeActivated = true;
            listenForHeartbeat();
            if (currentRole == RaftRole.LEADER) {
                sendHeartbeat();
            }
        } catch (RemoteObjectException e) {
            e.printStackTrace();
        }
    }

    /* Deactivate -- this method performs the "inverse" operation to `Activate`, namely to emulate
     * disconnection / failure of the Raft peer.  when called, the Raft peer should effectively "go
     * to sleep", meaning it should stop its underlying remote.Service interface, including shutting
     * down the listening socket, causing any further remote calls to this Raft peer to fail due to 
     * connection error.  when deactivated, a Raft peer should not make or receive any remote calls,
     * and any execution of the Raft protocol should effectively pause.  however, local state should
     * be maintained, meaning if a Raft node was the LEADER when it was deactivated, it should still
     * believe it is the leader when it reactivates.
     *
     * given a RaftPeer object `rf`, the Controller will call this method as `rf.Deactivate()`, 
     * so you should define this method accordingly. Similar notes / details apply here as
     * with `Activate`.
     *
     * TODO: implement the `Deactivate` method
     */
    public void Deactivate() {
        this.service.stop();
        this.isNodeActivated = false;
        if (electionTimer != null) {
            electionTimer.cancel(true);
        }
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel(true);
        }
    }

    private void listenForHeartbeat() {
        if (electionTimer != null) electionTimer.cancel(true);
        electionTimer = electionScheduler.scheduleAtFixedRate(
                new ElectionTask(this),
                electionInterval,
                electionInterval,
                TimeUnit.MILLISECONDS);
    }

    private void sendHeartbeat() {
        if (heartbeatTimer != null) heartbeatTimer.cancel(true);
        this.heartbeatTimer = this.heartbeatScheduler.scheduleAtFixedRate(
                new HeartbeatTask(this),
                0,
                100,
                TimeUnit.MILLISECONDS);
    }

    public synchronized void updateNextIndexAndMatchIndex(
            int followerId,
            int replicatedLogSize,
            boolean isReplicationOk)
    {
        if (isReplicationOk) {
            nextIndex[followerId] = replicatedLogSize + 1;
            matchIndex[followerId] = replicatedLogSize;
        } else {
            if (nextIndex[followerId] > 1) nextIndex[followerId]--;
        }
    }

    /* TODO: implement remote method calls from other Raft peers:
     *
     * RequestVote -- as described in the Raft paper, called by other Raft peers
     *
     * AppendEntries -- as described in the Raft paper, called by other Raft peers
     *
     * GetCommittedCmd -- called (only) by the Controller.  this method provides an input argument
     * `index`.  if the Raft peer has a log entry at the given `index`, and that log entry has been
     * committed (per the Raft algorithm), then the command stored in the log entry should be returned
     * to the Controller.  otherwise, the Raft peer should return the value 0, which is not a valid
     * command number and indicates that no committed log entry exists at that index
     *
     * GetStatus -- called (only) by the Controller.  this method takes no arguments and is essentially
     * a "getter" for the state of the Raft peer, including the Raft peer's current term, current last
     * log index, role in the Raft algorithm, and total number of remote calls handled since starting.
     * the method returns a `StatusReport` object as provided.
     *
     * NewCommand -- called (only) by the Controller.  this method emulates submission of a new command
     * by a Raft client to this Raft peer, which should be handled and processed according to the rules
     * of the Raft algorithm.  once handled, the Raft peer should return a `StatusReport` object with
     * the updated status after the new command was handled.
     */



    /* general notes:
     *
     * - you are welcome to use additional helper to handle various aspects of the Raft algorithm logic
     *   within the scope of a single Raft peer.  you should not need to create any additional remote
     *   calls between Raft peers or the Controller.  if there is a desire to create additional remote
     *   calls, please talk with the course staff before doing so.
     *
     * - please make sure to read the Raft paper (https://raft.github.io/raft.pdf) before attempting
     *   any coding for this lab.  you will most likely need to refer to it many times during your
     *   implementation and testing tasks, so please consult the paper for algorithm details. 
     *
     * - each Raft peer will accept a lot of remote calls from other Raft peers and the Controller,
     *   so use of locks / mutexes can be very important.  you are expected to use locks correctly to
     *   ensure correct and safe operation of your implemetation.
     */
    public int getElectionTimeout(int lo, int hi){
        Random random = new Random();
        return random.nextInt(hi - lo) + lo;
    }

    public String getPeerPort(int peerId) {
        if (peerId < 0 || peerId >= numOfPeers) {
            throw new RuntimeException(String.format("peer id %d does not exist", peerId));
        }
        String ip = "127.0.0.1:";
        int peerPort = peerId > nodeId ? port + (peerId - nodeId) : port - (nodeId - peerId);
        return ip + peerPort;
    }

    public void toFollower(int newTerm) {
        if (debug) {
            System.out.println("BECOME FOLLOWER: " + getPersistentState());
        }
        synchronized (this) {
            currentTerm = newTerm;
            votedFor = null;
            currentRole = RaftRole.FOLLOWER;
            listenForHeartbeat();
            if (heartbeatTimer != null) {
                heartbeatTimer.cancel(true);
            }
        }
    }

    public void toCandidate() {
        if (debug) {
            System.out.println("BECOME CANDIDATE: " + getPersistentState());
        }
        synchronized (this) {
            currentRole = RaftRole.CANDIDATE;
            currentTerm++;
            votedFor = nodeId;
            listenForHeartbeat();
            if (heartbeatTimer != null) {
                heartbeatTimer.cancel(true);
            }
        }
    }

    public void toLeader() {
        if (debug) {
            System.out.println("BECOME LEADER: " + getPersistentState());
        }
        synchronized (this) {
            currentRole = RaftRole.LEADER;
            Arrays.fill(nextIndex, getLastLog().index + 1);
            Arrays.fill(matchIndex, 0);
            sendHeartbeat();
            this.electionTimer.cancel(true);
        }
    }

    public RaftLog getLastLog() {
        synchronized (this) {
            if (logs.size() == 0) {
                return new RaftLog(0, 0, 0);
            }
            return logs.get(logs.size() - 1);
        }
    }

    public void setHeartbeat(boolean isHeartbeatReceived) {
        synchronized (this) {
            receivedHeartbeat = isHeartbeatReceived;
        }
    }

    public void increaseRpcCallCount() {
        synchronized (this) {
            callCount++;
        }
    }

    public boolean isHeartbeatReceived() {
        return receivedHeartbeat;
    }

    public RaftRole getCurrentRole() {
        return currentRole;
    }

    public int getCurrentTerm() {
        return currentTerm;
    }

    public Integer getVotedFor() {
        return votedFor;
    }

    public List<RaftLog> getLogs() {
        return logs;
    }

    public int getCommitIndex() {
        return commitIndex;
    }

    public int[] getNextIndex() {
        return nextIndex;
    }

    public int[] getMatchIndex() {
        return matchIndex;
    }

    public int getReplicationOkCount() {
        return replicationOkCount;
    }

    private String getPersistentState() {
        return String.format("\nNode id: %d\n" +
                        "\t currentTerm: %d\n" +
                        "\t currentRole: %s\n" +
                        "\t votedFor: %d\n" +
                        "\t logs: %s\n" +
                        "\t commitIndex: %d\n" +
                        "\t nextIndex: %s\n" +
                        "\t matchIndex: %s",
                nodeId, currentTerm, currentRole,
                votedFor, getListContent(logs), commitIndex,
                Arrays.toString(nextIndex), Arrays.toString(matchIndex));
    }

    private String getListContent(List<RaftLog> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < list.size(); i++) {
            RaftLog log = list.get(i);
            sb
                    .append("{msg: ").append(log.msg)
                    .append(", term: ").append(log.term)
                    .append(", index: ").append(log.index)
                    .append("}");
            if (i < list.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }


}

