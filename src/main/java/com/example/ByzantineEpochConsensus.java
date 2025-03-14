package com.example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.example.EpochState;

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
    // Instance identifier
    private final String self;
    private final String leader;
    private final int N; // Total number of processes
    private final int f; // Max number of process failures
    private final long epochTimestamp;
    
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
            String leader,
            int n,
            int f,
            int epochTimestamp,
            AuthenticatedPerfectLinks[] authenticatedLinks) {
        
        this.self = self;
        this.leader = leader;
        this.N = n;
        this.f = f;
        this.epochTimestamp = epochTimestamp;
        this.authenticatedLinks = authenticatedLinks;
        
        // Initialize conditional collect with sound predicate
        this.conditionalCollect = new ConditionalCollect(
            self,
            leader,
            n,
            f,
            this::soundPredicate,  // Using method reference for sound predicate
            authenticatedLinks,
            this::onMessagesCollected  // Callback for collected messages
        );
        
        // Initialize arrays
        this.written = new String[N];
        this.accepted = new String[N];
        
        // Initialize arrays with null values
        for (int i = 0; i < N; i++) {
            written[i] = null;  // ⊥ in pseudocode
            accepted[i] = null; // ⊥ in pseudocode
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
        // Initialize state from epoch state
        this.valts = epochState.getTimestamp();
        this.val = epochState.getValue();
        this.writeSet = epochState.getWriteSet();
        
        // Initialize arrays
        for (int i = 0; i < N; i++) {
            written[i] = null;  // ⊥ in pseudocode
            accepted[i] = null; // ⊥ in pseudocode
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
        // Only the leader should process propose events
        if (!self.equals(leader)) {
            Logger.log(Logger.EPOCH_CONSENSUS, "Ignoring propose event on non-leader process: " + self);
            return;
        }
        
        // If val is null (⊥ in pseudocode), set it to the proposed value
        if (val == null) {
            val = value;
            Logger.log(Logger.EPOCH_CONSENSUS, "Leader " + self + " setting val to: " + value);
        }
        
        // Send READ message to all processes
        for (AuthenticatedPerfectLinks link : authenticatedLinks) {
            // Create READ message
            Message readMessage = new Message("READ", "CMD_READ");
            link.alp2pSend(link.getDestinationEntity(), readMessage);
            Logger.log(Logger.EPOCH_CONSENSUS, 
                      "Leader sent READ message to " + link.getDestinationEntity());
        }
        
        // Trigger conditional collect to start collecting STATE messages
        
        // Create a STATE message with the leader's current state
        String writesetStr = serializeWriteset(writeSet);
        String payload = "STATE," + valts + "," + (val == null ? "null" : val) + "," + writesetStr;
        Message stateMessage = new Message(payload, "STATE");
        
        // Trigger conditional collect to start collecting STATE messages
        conditionalCollect.input(stateMessage);
        
        Logger.log(Logger.EPOCH_CONSENSUS, "Leader triggered conditional collect for STATE messages");
        
        // Additional propose logic would go here in the future
    }

    public void processState(Message message){
        System.out.println("Processing state message");
        System.out.println(message);
        conditionalCollect.input(message);
        conditionalCollect.checkCollectionCondition();
    }
    
    /**
     * Handles a READ message from the leader by sending back a STATE response.
     * 
     * @param sender The sender of the READ message
     */
    private void handleReadMessage(String sender) {
        // Check that the sender is the leader
        if (!sender.equals(leader)) {
            return;
        }
        
        // Create a STATE message with current state information
        // Format: [STATE, valts, val, writeset]
        
        // Serialize the writeset
        String writesetStr = serializeWriteset(writeSet);
        
        // Create a payload containing the state information
        String payload = "STATE," + valts + "," + (val == null ? "null" : val) + "," + writesetStr;
        
        // Send STATE message back to the leader
        for (AuthenticatedPerfectLinks link : authenticatedLinks) {
            if (link.getDestinationEntity().equals(leader)) {
                Message stateMessage = new Message(payload, "STATE");
                link.alp2pSend(leader, stateMessage);
                Logger.log(Logger.EPOCH_CONSENSUS, 
                          "Process " + self + " sent STATE message to leader");
                break;
            }
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
    
    /**
     * Validates the message.
     * 
     * @param messages Array of messages to check
     * @return true if the predicate is satisfied
     */
    private boolean soundPredicate(Message[] messages) {
        // Implementation of sound predicate goes here
        // For now, this is a placeholder
        
        // In a real implementation, this would check if the messages satisfy
        // the conditions for Byzantine consensus
        return true;
    }
    
    /**
     * Callback method for when messages are collected by conditional collect.
     * 
     * @param messages The collected messages
     */
    private void onMessagesCollected(Message[] messages) {
        // Handle collected messages
        Logger.log(Logger.EPOCH_CONSENSUS, "Messages collected in ByzantineEpochConsensus");
        
        // Process the collected messages here
        // This would implement the Byzantine consensus logic
    }
}
    