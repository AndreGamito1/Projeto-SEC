package com.example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Implements Byzantine Read/Write Epoch Consensus algorithm
 * to append strings to a blockchain
 */
public class EpochConsensus {
    private Leader leader;
    private List<String> blockchain = new ArrayList<>();
    private Map<Integer, EpochState> epochs = new ConcurrentHashMap<>();
    private AtomicInteger currentEpoch = new AtomicInteger(0);
    private static final int QUORUM_SIZE = 2; // Majority of 4 members + leader
    
    // Constants for commands used in messages
    private static final String CMD_PROPOSE = "EPOCH_PROPOSE";
    private static final String CMD_ABORT = "EPOCH_ABORT";
    private static final String CMD_DECIDE = "EPOCH_DECIDE";
    private static final String CMD_ACK = "EPOCH_ACK";
    private static final String CMD_READ = "EPOCH_READ";
    private static final String CMD_READ_REPLY = "EPOCH_READ_REPLY";
    
    /**
     * Constructor for EpochConsensus.
     * 
     * @param leader The leader instance to use for communication
     */
    public EpochConsensus(Leader leader) {
        this.leader = leader;
        blockchain.add("Genesis");
        // Register message handlers for authenticated messages
        leader.registerMessageHandler(CMD_ACK, this::handleAck);
        leader.registerMessageHandler(CMD_READ_REPLY, this::handleReadReply);
    }
    
    /**
     * Represents the state of an epoch consensus instance
     */
    private class EpochState {
        int epoch;
        String proposedValue;
        boolean aborted = false;
        int ackCount = 0;
        int readReplies = 0;
        Map<String, String> memberValues = new HashMap<>();
        Map<String, List<String>> memberWriteSets = new HashMap<>();
        boolean completed = false;
        
        EpochState(int epoch) {
            this.epoch = epoch;
        }
    }

    /**
     * Gets the current epoch number.
     * 
     * @return The current epoch
     */
    public int getCurrentEpoch() {
        return currentEpoch.get();
    }
    
    /**
     * Initiates the consensus process to append a new value to the blockchain.
     * 
     * @param value The string to append to the blockchain
     * @return true if consensus was successful, false otherwise
     * @throws Exception If communication fails
     */
    public synchronized boolean appendToBlockchain(String value) throws Exception {
        int epoch = currentEpoch.get();
        Logger.log(Logger.LEADER_ERRORS, "Starting epoch " + epoch + " to append: " + value);
        
        // Create epoch state
        EpochState state = new EpochState(epoch);
        epochs.put(epoch, state);
        
        // First, perform a read phase to get the latest values from members
        performReadPhase(epoch);
        
        // Wait for read replies
        long startTime = System.currentTimeMillis();
        while (state.readReplies < QUORUM_SIZE && System.currentTimeMillis() - startTime < 5000) {
            Thread.sleep(100);
            System.out.println("Current read replies: " + state.readReplies + "/" + QUORUM_SIZE);
        }
        
        // If we didn't get enough read replies, abort
        if (state.readReplies < QUORUM_SIZE) {
            System.out.println("Read replies: " + state.readReplies);
            abortEpoch(epoch, "Not enough read replies");
            return false;
        }
        
        // Set the proposed value (with the epoch as metadata)
        state.proposedValue = value;
        
        // Broadcast propose message to all members
        String proposePayload = epoch + "|" + value;
        leader.broadcastToMembers(proposePayload, CMD_PROPOSE);
        Logger.log(Logger.LEADER_ERRORS, "Proposed value for epoch " + epoch + ": " + value);
        
        // Wait for acknowledgments (with timeout)
        startTime = System.currentTimeMillis();
        while (state.ackCount < QUORUM_SIZE && !state.aborted && System.currentTimeMillis() - startTime < 5000) {
            Thread.sleep(100);
            System.out.println("Current ack count: " + state.ackCount + "/" + QUORUM_SIZE);
        }
        
        // If we got enough acks and the epoch wasn't aborted, decide the value
        if (state.ackCount >= QUORUM_SIZE && !state.aborted) {
            // Broadcast decide message
            leader.broadcastToMembers(epoch + "|" + value, CMD_DECIDE);
            Logger.log(Logger.LEADER_ERRORS, "Decided value for epoch " + epoch + ": " + value);
            
            // Add to local blockchain
            blockchain.add(value);
            state.completed = true;
            
            // Move to next epoch
            currentEpoch.incrementAndGet();
            
            return true;
        } else {
            // If not enough acks or aborted, formally abort the epoch
            if (!state.aborted) {
                abortEpoch(epoch, "Not enough acknowledgments");
            }
            return false;
        }
    }
    
/**
 * Gets the current blockchain by performing a Byzantine read phase.
 * This collects blockchain information from all members.
 * 
 * @return List of strings in the blockchain
 * @throws Exception If communication fails
 */
public List<String> getBlockchain() throws Exception {
    // Create a temporary epoch state for the read operation
    int readEpoch = -1; // Using -1 to distinguish from regular consensus epochs
    EpochState readState = new EpochState(readEpoch);
    epochs.put(readEpoch, readState);
    
    // Perform a read phase
    performReadPhase(readEpoch);
    
    // Wait for read replies (max 5 seconds)
    long startTime = System.currentTimeMillis();
    while (readState.readReplies < QUORUM_SIZE && System.currentTimeMillis() - startTime < 5000) {
        Thread.sleep(100);
        System.out.println("Waiting for blockchain read replies: " + readState.readReplies + "/" + QUORUM_SIZE);
    }
    
    // If we didn't get enough replies, return error message
    if (readState.readReplies < QUORUM_SIZE) {
        throw new Exception("Failed to get quorum for blockchain read operation");
    }
    
    
    // Process all the writeSets received from members
    List<String> reconstructedBlockchain = new ArrayList<>();
    boolean genesisFound = false;
    
    // Add "Genesis" if any member has it
    for (Map.Entry<String, List<String>> entry : readState.memberWriteSets.entrySet()) {
        List<String> writeSet = entry.getValue();
        if (!writeSet.isEmpty() && writeSet.get(0).equals("Genesis")) {
            reconstructedBlockchain.add("Genesis");
            genesisFound = true;
            break;
        }
    }
    
    // Merge blocks from all members (excluding Genesis which we've already handled)
    // This is a simplified approach - in a real Byzantine environment, you'd 
    // need to do more sophisticated verification and conflict resolution
    for (Map.Entry<String, List<String>> entry : readState.memberWriteSets.entrySet()) {
        List<String> writeSet = entry.getValue();
        for (int i = genesisFound ? 1 : 0; i < writeSet.size(); i++) {
            String block = writeSet.get(i);
            if (!reconstructedBlockchain.contains(block)) {
                // Verify this block with other members before adding
                int confirmations = 0;
                for (List<String> otherWriteSet : readState.memberWriteSets.values()) {
                    if (otherWriteSet.contains(block)) {
                        confirmations++;
                    }
                }
                
                // If a quorum of members has this block, add it
                if (confirmations >= QUORUM_SIZE) {
                    reconstructedBlockchain.add(block);
                }
            }
        }
    }
    
    // Clean up the temporary epoch state
    epochs.remove(readEpoch);
    
    // If we got no valid blockchain from members, fall back to local data
    if (reconstructedBlockchain.isEmpty()) {
        return new ArrayList<>(blockchain);
    }
    
    // Update our local blockchain with the reconstructed one
    blockchain = new ArrayList<>(reconstructedBlockchain);
    
    return reconstructedBlockchain;
}

    /**
     * Performs the read phase of the consensus algorithm.
     * 
     * @param epoch The current epoch number
     * @throws Exception If sending fails
     */
    private void performReadPhase(int epoch) throws Exception {
        leader.broadcastToMembers(String.valueOf(epoch), CMD_READ);
        Logger.log(Logger.LEADER_ERRORS, "Initiated read phase for epoch " + epoch);
    }
    
    /**
     * Aborts the current epoch consensus.
     * 
     * @param epoch The epoch to abort
     * @param reason The reason for aborting
     * @throws Exception If sending fails
     */
    private void abortEpoch(int epoch, String reason) throws Exception {
        EpochState state = epochs.get(epoch);
        if (state != null && !state.aborted) {
            state.aborted = true;
            leader.broadcastToMembers(epoch + "|" + reason, CMD_ABORT);
            Logger.log(Logger.LEADER_ERRORS, "Aborted epoch " + epoch + ": " + reason);
        }
    }
    
    /**
     * Handles acknowledgment messages from members.
     * 
     * @param memberName The name of the member
     * @param message The authenticated message
     */
    private void handleAck(String memberName, AuthenticatedMessage message) {
        String payload = message.getPayload();
        System.out.println("Received ACK from " + memberName + ": " + payload);
        try {
            String[] parts = payload.split("\\|");
            int epoch = Integer.parseInt(parts[0]);
            String response = parts[1]; // "ACK" or "NACK"
            
            EpochState state = epochs.get(epoch);
            if (state != null && !state.aborted) {
                if ("ACK".equals(response)) {
                    state.ackCount++;
                    Logger.log(Logger.LEADER_ERRORS, "Received ACK from " + memberName + 
                              " for epoch " + epoch + " (total: " + state.ackCount + ")");
                } else {
                    // If a member rejects, abort the epoch
                    abortEpoch(epoch, "Rejected by " + memberName);
                }
            }
        } catch (Exception e) {
            Logger.log(Logger.LEADER_ERRORS, "Error handling ACK from " + memberName + ": " + e.getMessage());
        }
    }
    
    /**
     * Handles read reply messages from members.
     * 
     * @param memberName The name of the member
     * @param message The authenticated message
     */
    private void handleReadReply(String memberName, AuthenticatedMessage message) {
        String payload = message.getPayload();
        System.out.println("Received READ_REPLY from " + memberName + ": " + payload);
        try {
            String[] parts = payload.split("\\|", 3);  // Split into at most 3 parts
            int epoch = Integer.parseInt(parts[0]);
            String lastValue = parts.length > 1 ? parts[1] : "";
            String writeSetStr = parts.length > 2 ? parts[2] : "";
            
            // Parse the writeSet
            List<String> writeSet = new ArrayList<>();
            if (!writeSetStr.isEmpty()) {
                String[] blocks = writeSetStr.split("\\|");
                for (String block : blocks) {
                    if (!block.trim().isEmpty()) {
                        writeSet.add(block);
                    }
                }
            }
            
            EpochState state = epochs.get(epoch);
            if (state != null && !state.aborted) {
                state.memberValues.put(memberName, lastValue);
                state.memberWriteSets.put(memberName, writeSet);
                state.readReplies++;
                
                Logger.log(Logger.LEADER_ERRORS, "Received READ_REPLY from " + memberName + 
                          " for epoch " + epoch + " with lastValue: " + lastValue + 
                          " and writeSet size: " + writeSet.size() +
                          " (total replies: " + state.readReplies + ")");
            }
        } catch (Exception e) {
            Logger.log(Logger.LEADER_ERRORS, "Error handling READ_REPLY from " + memberName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
        

}