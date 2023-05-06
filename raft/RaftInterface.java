package raft;

import java.util.ArrayList;
import java.util.List;

import remote.RemoteObjectException;

/**
 * RaftInterface -- this is the "service interface" that is implemented by each Raft peer using the
 * remote library from Lab 1.  it supports five remote methods that you must define and implement.
 * these methods are described as follows:
 *
 * 1) RequestVote -- this is one of the remote calls defined in the Raft paper, and it should be
 *    supported as such.  you will need to include whatever argument types are needed per the Raft
 *    algorithm, and you can package the return values however you like.
 *
 * 2) AppendEntries -- this is one of the remote calls defined in the Raft paper, and it should be
 *    supported as such and defined in a similar manner to RequestVote above.
 *
 * 3) GetCommittedCmd -- this is a remote call that is used by the Controller in the test code. it
 *    allows the Controller to check the value of a commmitted log entry at a given index. the 
 *    type of the function is given below, and it must be implemented as given, otherwise the test
 *    code will not function correctly.  more detail about this method is available elsewhere in this
 *    starter code.
 *
 * 4) GetStatus -- this is a remote call that is used by the Controller to collect status information
 *    about the Raft peer.  the StatusReport object type that it returns is provided, and it must be 
 *    implemented as given, or the Controller and test code will not function correctly.
 *
 * 5) NewCommand -- this is a remote call that is used by the Controller to emulate submission of 
 *    a new command value by a Raft client.  upon receipt, it will initiate processing of the command
 *    and reply back to the Controller with a StatusReport object as above. it must be
 *    implemented as given, or the test code will not function correctly.
 *
 * Note that all of these methods must throw a `RemoteObjectException`, since that is required for
 * the remote library use.
 */
public interface RaftInterface {
    // TODO: complete the argument list and return types
    public RequestVoteResp RequestVote(int candidateTerm, int candidateId, int candidateLastLogIndex, int candidateLastLogTerm) throws RemoteObjectException;
    public AppendEntriesResp AppendEntries(
            int leaderTerm,
            int leaderId,
            int prevLogIndex,
            int prevLogTerm,
            List<RaftLog> entries,
            int leaderCommit) throws RemoteObjectException;
    
    public int GetCommittedCmd(int index) throws RemoteObjectException;
    public StatusReport GetStatus() throws RemoteObjectException;
    public StatusReport NewCommand(int command) throws RemoteObjectException;
}

