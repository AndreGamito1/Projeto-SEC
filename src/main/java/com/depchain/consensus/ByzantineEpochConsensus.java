package com.depchain.consensus;


import com.depchain.utils.*;
import com.depchain.networking.*;
/**
 * Interface for Byzantine Epoch Consensus.
 */
interface ByzantineEpochConsensusInterface {
    void init(EpochState epochState);
}

/**
 * Implementation of Byzantine Epoch Consensus protocol.
 */
public class ByzantineEpochConsensus implements ByzantineEpochConsensusInterface {
    private final String self;
    private final boolean isLeader;
    private final int N; 
    private final String leader;
    
    // Components
    private final AuthenticatedPerfectLinks[] authenticatedLinks;
    private final ConditionalCollect conditionalCollect;
    
    // State variables
    private long valts;
    private String val;
    private TimestampValue[] writeSet;
    private String[] written;
    private String[] accepted;
    
    /**
     * Constructor for ByzantineEpochConsensus.
     * 
     * @param self The identifier of this process
     * @param leader The identifier of the leader process for this epoch
     * @param n Total number of processes
     * @param f Maximum number of process failures
     * @param epochTimestamp The timestamp for this epoch
     * @param authenticatedLinks Array of authenticated perfect links to all processes
     */
    public ByzantineEpochConsensus(
            String self,
            boolean isLeader,
            int n,
            int f,
            int epochTimestamp,
            AuthenticatedPerfectLinks[] authenticatedLinks
            ) {
        
        this.self = self;
        this.N = n;
        this.authenticatedLinks = authenticatedLinks;
        this.isLeader = isLeader;
        this.leader = "leader";
        
        this.conditionalCollect = new ConditionalCollect(self,leader,n,f, authenticatedLinks);
        
        // Initialize arrays
        this.written = new String[N];
        this.accepted = new String[N];
        
        // Initialize arrays with null values
        for (int i = 0; i < N; i++) {
            written[i] = null;  
            accepted[i] = null; 
        }
        
        Logger.log(Logger.EPOCH_CONSENSUS, "ByzantineEpochConsensus initialized. Self: " + 
                  self + ", Leader: " + leader + ", Epoch timestamp: " + epochTimestamp);
    }
    
    /**
     * Initializes the epoch consensus with the given epoch state.
     * 
     * @param epochState The epoch state containing value, timestamp, and writeset
     */
    @Override
    public void init(EpochState epochState) {
        this.valts = epochState.getTimestamp();
        this.val = epochState.getValue();
        this.writeSet = epochState.getWriteSet();
        
        for (int i = 0; i < N; i++) {
            written[i] = null; 
            accepted[i] = null; 
        }
        
        Logger.log(Logger.EPOCH_CONSENSUS, 
                  "ByzantineEpochConsensus initialized with epoch state. valts: " + 
                  valts + ", val: " + val);
    }

    /**
     * Handles the Propose event - called when a value is proposed (leader only).
     * 
     * @param value The value being proposed
     */
    public void propose(String value) {
        if (!self.equals(leader)) {
            Logger.log(Logger.EPOCH_CONSENSUS, "Ignoring propose event on non-leader process: " + self);
            return;
        }
        
        if (val == null) {
            val = value;
            Logger.log(Logger.EPOCH_CONSENSUS, "Leader " + self + " setting val to: " + value);
        }
        
        // Send READ message to all processes
        for (AuthenticatedPerfectLinks link : authenticatedLinks) {
            Message readMessage = new Message("READ", "CMD_READ");
            link.alp2pSend(link.getDestinationEntity(), readMessage);
            Logger.log(Logger.EPOCH_CONSENSUS, 
                      "Leader sent READ message to " + link.getDestinationEntity());
        }
                
        // Create a STATE message with the leader's current state
        String writesetStr = serializeWriteset(writeSet);
        String payload = "STATE," + valts + "," + (val == null ? "null" : val) + "," + writesetStr;
        Message stateMessage = new Message(payload, "STATE");
        
        // Trigger conditional collect to start collecting STATE messages
        conditionalCollect.input(stateMessage);
        
        Logger.log(Logger.EPOCH_CONSENSUS, "Leader triggered conditional collect for STATE messages");
    }

    /**
     * Process a state message and forward it to the leader if needed.
     *
     * @param message The message containing the state
     */
    public void processState(Message message) {
        String originalPayload = message.getPayload();
        System.out.println("Processing state message: " + originalPayload);
        
        String[] parts = originalPayload.split("\\|", 2);
        
        if (parts.length < 2) {
            System.out.println("Invalid message format: " + originalPayload);
            return;
        }
        
        String sender = parts[0];
        String stateData = parts[1]; 
        
        Message stateMessage = new Message(stateData, message.getCommand());
        
        if (this.isLeader) {
            System.out.println("Leader processing state message from " + sender + ": " + stateData);
            conditionalCollect.receiveState(sender, stateMessage);
        } else {
            System.out.println("Not a leader, ignoring state from " + sender);
        }
    }
          
    /**
     * Serializes the writeset for transmission.
     * 
     * @param writeSet The writeset to serialize
     * @return A string representation of the writeset
     */
    private String serializeWriteset(TimestampValue[] writeSet) {
        StringBuilder sb = new StringBuilder("[");
        if (writeSet == null) {
            return " ";
        }
        for (int i = 0; i < writeSet.length; i++) {
            if (writeSet[i] == null) {
                sb.append("null");
            } else {
                sb.append("(")
                  .append(writeSet[i].getTimestamp())
                  .append(",")
                  .append(writeSet[i].getValue())
                  .append(")");
            }
            
            if (i < writeSet.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
    