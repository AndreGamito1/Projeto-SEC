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
    private static final int QUORUM_SIZE = 1; // Majority of 4 members + leader
    
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
        boolean completed = false;
        
        EpochState(int epoch) {
            this.epoch = epoch;
        }
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
            String[] parts = payload.split("\\|");
            int epoch = Integer.parseInt(parts[0]);
            String lastValue = parts.length > 1 ? parts[1] : "";
            
            EpochState state = epochs.get(epoch);
            if (state != null && !state.aborted) {
                state.memberValues.put(memberName, lastValue);
                state.readReplies++;
                Logger.log(Logger.LEADER_ERRORS, "Received READ_REPLY from " + memberName + 
                          " for epoch " + epoch + " with value: " + lastValue + 
                          " (total: " + state.readReplies + ")");
            }
        } catch (Exception e) {
            Logger.log(Logger.LEADER_ERRORS, "Error handling READ_REPLY from " + memberName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Gets the current blockchain.
     * 
     * @return List of strings in the blockchain
     */
    public List<String> getBlockchain() {
        return new ArrayList<>(blockchain);
    }
    
    /**
     * Gets the current epoch number.
     * 
     * @return The current epoch
     */
    public int getCurrentEpoch() {
        return currentEpoch.get();
    }
}