package com.depchain.consensus;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import com.depchain.blockchain.Block;
import com.depchain.utils.*;

/**
 * Byzantine implementation of the Conditional Collect primitive
 * Can exhibit various Byzantine behaviors based on configuration
 * Completely overrides all methods from superclass without using any superclass methods
 */
public class ByzantineConditionalCollect extends ConditionalCollect {
    
    public enum ByzantineBehavior {
        NORMAL,     // Non-Byzantine behavior (follows protocol)
        YES_MAN,    // Always votes yes on leader's value
        NO_MAN,     // Always aborts when receiving proposals
        RANDOM,     // Randomly says yes or no to proposals
        OMIT        // Sometimes omits votes entirely
    }
    
    private final ByzantineBehavior behavior;
    private final MemberManager memberManager;
    private final ByzantineEpochConsensus epochConsensus;
    private Map<String, EpochState> collected = new HashMap<>();
    private Map<String, String> writeAcks = new HashMap<>();
    private Map<String, String> acceptAcks = new HashMap<>();
    private boolean isCollected = false;
    private boolean writeAcked = false;
    private boolean acceptAcked = false;
    private final String name;
    private final Random random = new Random();
    
    /**
     * Creates a new ByzantineConditionalCollect instance with specified Byzantine behavior
     * 
     * @param memberManager The manager to handle communications with members
     * @param epochConsensus The Byzantine epoch consensus instance
     * @param behaviorString The Byzantine behavior to exhibit as a string
     */
    public ByzantineConditionalCollect(MemberManager memberManager, ByzantineEpochConsensus epochConsensus, String behaviorString) {
        super(memberManager, epochConsensus);
        this.memberManager = memberManager;
        this.epochConsensus = epochConsensus;
        
        // Convert string to enum
        ByzantineBehavior parsedBehavior;
        try {
            parsedBehavior = ByzantineBehavior.valueOf(behaviorString.toUpperCase());
        } catch (IllegalArgumentException e) {
            Logger.log(Logger.CONDITIONAL_COLLECT, "Invalid behavior string: " + behaviorString + ". Defaulting to NORMAL behavior.");
            parsedBehavior = ByzantineBehavior.NORMAL;
        }
        
        this.behavior = parsedBehavior;
        this.name = memberManager.getName();
        Logger.log(Logger.CONDITIONAL_COLLECT, "Created ByzantineConditionalCollect with behavior: " + behavior);
    }
    
    /**
     * Sets the collected states based on the provided payload
     */
    @Override
    public void setCollected(String payload) {
        if (behavior == ByzantineBehavior.NO_MAN) {
            // NoMan immediately aborts when receiving a collected message
            Logger.log(Logger.CONDITIONAL_COLLECT, "NO_MAN behavior: aborting on collected message");
            abort();
            return;
        }
        
        // For other behaviors, process normally but prepare for Byzantine actions later
        Logger.log(Logger.CONDITIONAL_COLLECT, "Processing collected message with behavior: " + behavior);
        collected = parseCollectedPayload(payload);
        isCollected = true;
        
        // Random behavior might randomly abort here
        if (behavior == ByzantineBehavior.RANDOM && random.nextBoolean()) {
            Logger.log(Logger.CONDITIONAL_COLLECT, "RANDOM behavior: decided to abort on collected message");
            abort();
        }
    }
    
    /**
     * Appends state from the provided payload to the collected states
     */
    @Override
    public void appendState(String statePayload) {
        switch (behavior) {
            case NORMAL:
            case YES_MAN:
                Map<String, EpochState> state = parseCollectedPayload(statePayload);
                collected.putAll(state);
                if (checkQuorum(collected)) {
                    isCollected = true;
                }
                break;
                
            case NO_MAN:
                // NoMan doesn't append anything, effectively ignoring the state
                Logger.log(Logger.CONDITIONAL_COLLECT, "NO_MAN behavior: ignoring append state");
                break;
                
            case OMIT:
                // Omit behavior sometimes ignores the state
                if (random.nextDouble() < 0.5) {
                    Logger.log(Logger.CONDITIONAL_COLLECT, "OMIT behavior: ignoring append state");
                } else {
                    Map<String, EpochState> stateOmit = parseCollectedPayload(statePayload);
                    collected.putAll(stateOmit);
                    if (checkQuorum(collected)) {
                        isCollected = true;
                    }
                }
                break;
                
            case RANDOM:
                // Random behavior might manipulate the state or process normally
                if (random.nextBoolean()) {
                    Map<String, EpochState> stateRandom = parseCollectedPayload(statePayload);
                    collected.putAll(stateRandom);
                    if (checkQuorum(collected)) {
                        isCollected = true;
                    }
                } else {
                    Logger.log(Logger.CONDITIONAL_COLLECT, "RANDOM behavior: manipulating state");
                    // Potentially corrupt the state by adding a fake entry
                    collected.put("fake-member-" + random.nextInt(100), new EpochState(0, "fake-value"));
                }
                break;
        }
    }
    
    /**
     * Checks if a quorum exists in the collected states
     */
    @Override
    public boolean checkQuorum(Map<String, EpochState> collected) {
        Logger.log(Logger.CONDITIONAL_COLLECT, "Checking quorum with " + collected.size() + " entries");
        
        // If we don't have enough entries, quorum can't be reached
        if (collected.size() < (memberManager.getQuorumSize())) {  // -1 because the leader is not included in the count
            Logger.log(Logger.CONDITIONAL_COLLECT, "Not enough entries for quorum: " + collected.size() + " < " + memberManager.getQuorumSize());
            return false;
        }
        
        // Count occurrences of each value
        Map<String, Integer> valueCounts = new HashMap<>();
        
        // Group by value and count
        for (Map.Entry<String, EpochState> entry : collected.entrySet()) {
            EpochState state = entry.getValue();
            
            // Handle null states
            if (state == null) {
                valueCounts.put("NULL_VALUE", valueCounts.getOrDefault("NULL_VALUE", 0) + 1);
                continue;
            }
            
            String value = state.getValue();
            valueCounts.put(value, valueCounts.getOrDefault(value, 0) + 1);
        }
        
        // Check if any value has reached quorum
        for (Map.Entry<String, Integer> entry : valueCounts.entrySet()) {
            Integer count = entry.getValue();
            String value = entry.getKey();
            
            Logger.log(Logger.CONDITIONAL_COLLECT, "Checking value with count: " + count);
            if (value.equals("NULL_VALUE")) {
                if (count >= memberManager.getQuorumSize() - 1) { // -1 because the leader is not included in the count
                    Logger.log(Logger.CONDITIONAL_COLLECT, "Quorum reached with null value: " + count);
                    return true;
                }
            } else {
                if (count >= memberManager.getQuorumSize()) {
                    Logger.log(Logger.CONDITIONAL_COLLECT, "Quorum reached");
                    return true;
                }
            }
        }
        
        Logger.log(Logger.CONDITIONAL_COLLECT, "No quorum reached");
        return false;
    }
    
    /**
     * Checks if a quorum exists in the acknowledgments
     */
    @Override
    public boolean checkAckQuorum(Map<String, String> Acks) {
        // If we don't have enough entries, quorum can't be reached
        if (Acks.size() < (memberManager.getQuorumSize() - 1)) { // - 1 because we count with ourself
            return false;
        }
        
        // Count occurrences of each value
        Map<String, Integer> valueCounts = new HashMap<>();
        
        // Group by value and count
        for (String ack : Acks.values()) {
            // Handle null values
            if (ack == null) {
                valueCounts.put(null, valueCounts.getOrDefault(null, 0) + 1);
                continue;
            }
            
            valueCounts.put(ack, valueCounts.getOrDefault(ack, 0) + 1);
        }
        
        // Check if any value has reached quorum
        for (Integer count : valueCounts.values()) {
            if (count >= (memberManager.getQuorumSize() - 1)) { // -1 because we count with ourself
                Logger.log(Logger.CONDITIONAL_COLLECT, "Quorum reached with value: " + count);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Appends acknowledgments from the provided payload
     */
    @Override
    public void appendAck(String ackPayload, String ackType) {
        switch (behavior) {
            case NORMAL:
            case YES_MAN:
                processAck(ackPayload, ackType);
                break;
                
            case NO_MAN:
                // NoMan doesn't append any acks
                Logger.log(Logger.CONDITIONAL_COLLECT, "NO_MAN behavior: ignoring " + ackType + " ack");
                break;
                
            case OMIT:
                // Omit behavior sometimes ignores acks
                if (random.nextDouble() < 0.5) {
                    Logger.log(Logger.CONDITIONAL_COLLECT, "OMIT behavior: ignoring " + ackType + " ack");
                } else {
                    processAck(ackPayload, ackType);
                }
                break;
                
            case RANDOM:
                // Random behavior might manipulate acks or process normally
                if (random.nextBoolean()) {
                    processAck(ackPayload, ackType);
                } else {
                    Logger.log(Logger.CONDITIONAL_COLLECT, "RANDOM behavior: manipulating " + ackType + " ack");
                    // Corrupt the ack by adding fake entries
                    Map<String, String> ack = parseAck(ackPayload);
                    if (ackType.equals("ACCEPT")) {
                        acceptAcks.putAll(ack);
                        acceptAcks.put("fake-member-" + random.nextInt(100), "fake-ack");
                    } else if (ackType.equals("WRITE")) {
                        writeAcks.putAll(ack);
                        writeAcks.put("fake-member-" + random.nextInt(100), "fake-ack");
                    }
                }
                break;
        }
    }
    
    /**
     * Helper method to process acknowledgments
     */
    private void processAck(String ackPayload, String ackType) {
        if (ackType.equals("ACCEPT")) {
            if (acceptAcked) { return; }
            Map<String, String> ack = parseAck(ackPayload);
            acceptAcks.putAll(ack);
        } else if (ackType.equals("WRITE")) {
            if (writeAcked) { return; }
            Map<String, String> ack = parseAck(ackPayload);
            writeAcks.putAll(ack);
        }
    }
    
    /**
     * Parses an acknowledgment payload into a map
     */
    @Override
    public Map<String, String> parseAck(String ackPayload) {
        Map<String, String> result = new HashMap<>();
        
        if (ackPayload != null && !ackPayload.isEmpty()) {
            // Split the payload by newline to handle multiple entries
            String[] entries = ackPayload.split("\n");
            
            for (String entry : entries) {
                // Check if the entry contains a colon
                if (entry.contains(":")) {
                    // Split by the first colon to separate member name and value
                    String[] parts = entry.split(":", 2);
                    
                    if (parts.length == 2) {
                        String memberName = parts[0].trim();
                        String value = parts[1].trim();
                        
                        // Extract the value from between square brackets if present
                        if (value.startsWith("[") && value.endsWith("]")) {
                            value = value.substring(1, value.length() - 1);
                        }
                        
                        result.put(memberName, value);
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * Creates an acknowledgment payload
     */
    @Override
    public String createAck(EpochState value) {
        // Format as "membername: [value]"
        StringBuilder builder = new StringBuilder();
        builder.append(name).append(": [");
        
        // Add the value if it's not null
        if (value != null) {
            builder.append(value.toString());
        }
        
        builder.append("]");
        
        return builder.toString();
    }
    
    /**
     * Inputs a message to the conditional collect primitive
     */
    @Override
    public void input(EpochState message) {
        try {
            switch (behavior) {
                case NORMAL:
                    normalInput(message);
                    break;
                    
                case NO_MAN:
                    // NoMan pretends to participate but will abort later
                    if (memberManager.isLeader()) {
                        collected.put(memberManager.getName(), message);
                        // Send READ message to all members
                        for (String member : memberManager.getMemberLinks().keySet()) {
                            memberManager.sendToMember(member, "", "READ");
                        }
                        // Wait for replies but plan to abort later
                        Thread collectionThread = new Thread(() -> {
                            waitForStates();
                        });
                        collectionThread.start();
                    } else {
                        // Respond to leader with manipulated state
                        Logger.log(Logger.CONDITIONAL_COLLECT, "NO_MAN behavior: sending manipulated state to leader");
                        memberManager.sendToMember(memberManager.getLeaderName(), "fake-state", "STATE");
                        Thread collectionThread = new Thread(() -> {
                            // Wait briefly, then abort
                            try {
                                Thread.sleep(2000);
                                abort();
                            } catch (InterruptedException e) {
                                Logger.log(Logger.CONDITIONAL_COLLECT, "Error in NoMan wait: " + e.getMessage());
                            }
                        });
                        collectionThread.start();
                    }
                    break;
                    
                case OMIT:
                    // Omit behavior sometimes doesn't send any message
                    if (random.nextDouble() < 0.5) {
                        Logger.log(Logger.CONDITIONAL_COLLECT, "OMIT behavior: omitting input response");
                        // Do nothing - omit the response
                    } else {
                        // Behave normally
                        normalInput(message);
                    }
                    break;
                    
                case RANDOM:
                    // Random behavior might input correct state or corrupted state
                    if (random.nextBoolean()) {
                        normalInput(message);
                    } else {
                        Logger.log(Logger.CONDITIONAL_COLLECT, "RANDOM behavior: sending corrupted input");
                        // Create corrupted message
                        EpochState corruptedMessage = new EpochState(message.getTimeStamp(), "corrupted-value");
                        normalInput(corruptedMessage);
                    }
                    break;
                    
                case YES_MAN:
                    // YesMan always behaves normally for input
                    normalInput(message);
                    break;
            }
        } catch (Exception e) {
            Logger.log(Logger.CONDITIONAL_COLLECT, "Error in ByzantineConditionalCollect.input: " + e.getMessage());
        }
    }
    
    /**
     * Normal input behavior
     */
    private void normalInput(EpochState message) {
        if (memberManager.isLeader()) {
            collected.put(memberManager.getName(), message);
            // Send READ message to all members
            for (String member : memberManager.getMemberLinks().keySet()) {
                memberManager.sendToMember(member, "", "READ");
            }
            // Wait for the STATE replies from each member
            Thread collectionThread = new Thread(() -> {
                waitForStates();
            });
            collectionThread.start();
        } else {
            // Send to leader the STATE message
            String statePayload = createCollectedPayload(collected);
            Logger.log(Logger.CONDITIONAL_COLLECT, "Sending state to leader");
            memberManager.sendToMember(memberManager.getLeaderName(), statePayload, "STATE");
            
            // Start waitForCollected in a new thread
            Thread collectionThread = new Thread(() -> {
                waitForStates();
            });
            collectionThread.start();
        }
    }
    
    /**
     * Waits for states from all members
     */
    @Override
    protected void waitForStates() {
        try {
            // Wait for all members to send their state
            for (int i = 0; i < 12; i++) {
                Thread.sleep(1000);
                if (isCollected) { break; }
                Logger.log(Logger.CONDITIONAL_COLLECT, "Waiting for states");
                Logger.log(Logger.CONDITIONAL_COLLECT, "Quorum size: " + memberManager.getQuorumSize());
                Logger.log(Logger.CONDITIONAL_COLLECT, "Collected size: " + collected.size());
            }
            
            if (isCollected) {
                if (memberManager.isLeader()) {
                    // Send to all members
                    String collectedPayload = createCollectedPayload(collected);
                    for (String member : memberManager.getMemberLinks().keySet()) {
                        memberManager.sendToMember(member, collectedPayload, "COLLECTED");
                    }
                    processCollected();
                } else {
                    Logger.log(Logger.CONDITIONAL_COLLECT, "Collected message received");
                    processCollected();
                }
            } else {
                Logger.log(Logger.CONDITIONAL_COLLECT, "Timeout waiting for states");
                abort(); // Send ABORT message to all members
            }
        } catch (Exception e) {
            Logger.log(Logger.CONDITIONAL_COLLECT, "Error collecting States, aborting: " + e.getMessage() + e.getStackTrace() + e.getCause());
        }
    }
    
    /**
     * Creates a formatted string payload from the collected map
     */
    private String createCollectedPayload(Map<String, EpochState> collected) {
        StringBuilder payload = new StringBuilder();
        boolean first = true;
        
        for (Map.Entry<String, EpochState> entry : collected.entrySet()) {
            if (!first) {
                payload.append(", ");
            } else {
                first = false;
            }
            
            payload.append(entry.getKey())
                .append(": [");
            
            // Check if the value is null
            if (entry.getValue() != null) {
                payload.append(entry.getValue().toString());
            }
            
            payload.append("]");
        }
        
        if (payload.toString().isEmpty()) {
            return memberManager.getName() + ": []";
        }
        
        return payload.toString();
    }
    
    /**
     * Parses a formatted collected payload back into a HashMap
     */
    protected Map<String, EpochState> parseCollectedPayload(String payload) {
        Map<String, EpochState> collected = new HashMap<>();
        Logger.log(Logger.CONDITIONAL_COLLECT, "Parsing collected payload");
        
        // Handle empty payload
        if (payload == null || payload.trim().isEmpty()) {
            return collected;
        }
        
        int startIdx = 0;
        while (startIdx < payload.length()) {
            // Find the key part
            int keyEnd = payload.indexOf(": [", startIdx);
            if (keyEnd == -1) break;
            
            String key = payload.substring(startIdx, keyEnd);
            
            // Find the closing bracket that matches our opening bracket
            int openBracket = keyEnd + 2; // position of '['
            int closeBracket = openBracket + 1;
            int bracketCount = 1;
            
            while (closeBracket < payload.length() && bracketCount > 0) {
                if (payload.charAt(closeBracket) == '[') bracketCount++;
                else if (payload.charAt(closeBracket) == ']') bracketCount--;
                closeBracket++;
            }
            
            if (bracketCount != 0) break; // Malformed input
            
            // Extract content between brackets
            String content = payload.substring(openBracket + 1, closeBracket - 1);
            
            // Handle empty brackets
            if (content.isEmpty()) {
                collected.put(key, null);
            } else {
                // For non-empty content, find the first comma to separate timestamp and value
                int firstComma = content.indexOf(", ");
                if (firstComma != -1) {
                    try {
                        int timestamp = Integer.parseInt(content.substring(0, firstComma));
                        String value = content.substring(firstComma + 2);
                        collected.put(key, new EpochState(timestamp, value));
                    } catch (NumberFormatException e) {
                        Logger.log(Logger.CONDITIONAL_COLLECT, "Error parsing timestamp: " + e.getMessage());
                    }
                }
            }
            
            // Move to next entry
            startIdx = closeBracket;
            // Skip comma and space if present
            if (startIdx < payload.length() && payload.charAt(startIdx) == ',') {
                startIdx += 2; // Skip ", "
            }
        }
        
        Logger.log(Logger.CONDITIONAL_COLLECT, "Parsed collected payload");
        return collected;
    }
    
    /**
     * Processes the collected states and makes a decision
     */
    private void processCollected() {
        Logger.log(Logger.CONDITIONAL_COLLECT, "Processing collected message");
        
        // Find the most frequent value and its count
        Map<EpochState, Integer> valueCounts = new HashMap<>();
        EpochState leaderValue = null;
        
        for (Map.Entry<String, EpochState> entry : collected.entrySet()) {
            String memberId = entry.getKey();
            EpochState value = entry.getValue();
            
            // Store leader's value separately
            if (memberId.equals(memberManager.getLeaderName())) {
                leaderValue = value;
            }
            
            // Count occurrences of each value
            valueCounts.put(value, valueCounts.getOrDefault(value, 0) + 1);
        }
        
        // Check for quorum
        int quorumSize = memberManager.getQuorumSize();
        boolean foundQuorum = false;
        
        for (Map.Entry<EpochState, Integer> entry : valueCounts.entrySet()) {
            EpochState value = entry.getKey();
            int count = entry.getValue();
            
            if (count >= quorumSize) {
                // Found a quorum for this value
                foundQuorum = true;
                
                // Check if quorum is for null (undefined state)
                if (value == null || value.toString().equals("null")) {
                    // For null quorum, follow leader value logic
                    if (leaderValue != null && !leaderValue.toString().equals("null")) {
                        Logger.log(Logger.CONDITIONAL_COLLECT, "Null Quorum found, adopting leader's value");
                        consensusLoop(leaderValue);
                    } else {
                        Logger.log(Logger.CONDITIONAL_COLLECT, "Quorum for null found and no leader value, aborting");
                        abort();
                    }
                } else {
                    consensusLoop(value);
                }
                return;
            }
        }
        
        // No quorum found, check leader's value
        if (!foundQuorum) {
            if (leaderValue != null && !leaderValue.toString().equals("null")) {
                Logger.log(Logger.CONDITIONAL_COLLECT, "No quorum, adopting leader's value");
                consensusLoop(leaderValue);
            } else {
                // No quorum and no leader value, abort
                Logger.log(Logger.CONDITIONAL_COLLECT, "No quorum and no leader value, aborting");
                abort();
            }
        }
    }
    
    /**
     * Starts the consensus loop
     */
    private void consensusLoop(EpochState value) {
        try {
            if (!(behavior == ByzantineBehavior.YES_MAN)) {
                Block block = new Block();
                block = Block.deserializeFromBase64(value.getValue());
                if (!epochConsensus.getWorldState().areAllTransactionsValid(block)) {
                    Logger.log(Logger.CONDITIONAL_COLLECT, "Block is not valid, aborting");
                    abort();
                    return;
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            Logger.log(Logger.CONDITIONAL_COLLECT, "Error deserializing block");
            abort();
            return;
        }
        
        // Phase 1: Send WRITE messages to all members
        for (String memberId : memberManager.getMemberLinks().keySet()) {
            String payload = createAck(value);
            memberManager.sendToMember(memberId, payload, "WRITE");
        }
        
        // Start a new thread for the write phase
        Thread writeThread = new Thread(() -> {
            waitForWrite(value);
        });
        writeThread.start();
    }
    
    /**
     * Waits for write acknowledgments
     */
    private void waitForWrite(EpochState value) {
        try {
            // Wait for enough write acknowledgments (quorum)
            for (int i = 0; i < 12; i++) {
                Thread.sleep(1000);
                if (writeAcks.size() == (memberManager.getQuorumSize() - 1)) { break; }
                Logger.log(Logger.CONDITIONAL_COLLECT, "current write acks: " + writeAcks.size() + "/" + memberManager.getQuorumSize());
            }
            
            // Check if we got enough acknowledgments
            if (checkAckQuorum(writeAcks)) {
                Logger.log(Logger.CONDITIONAL_COLLECT, "Received sufficient write acknowledgments");
                writeAcked = true;
                
                // Phase 2: Send ACCEPT messages to all members
                for (String memberId : memberManager.getMemberLinks().keySet()) {
                    String payload = createAck(value);
                    memberManager.sendToMember(memberId, payload, "ACCEPT");
                }
                
                // Start a new thread for the accept phase
                Thread acceptThread = new Thread(() -> {
                    waitForAccept(value);
                });
                acceptThread.setName("AcceptPhase-" + System.currentTimeMillis());
                acceptThread.start();
            } else {
                Logger.log(Logger.CONDITIONAL_COLLECT, "Failed to receive sufficient write acknowledgments");
                abort();
            }
        } catch (Exception e) {
            Logger.log(Logger.CONDITIONAL_COLLECT, "Error waiting for write acknowledgments: " + e.getMessage());
        }
    }
    
    /**
     * Waits for accept acknowledgments
     */
    private void waitForAccept(EpochState value) {
        try {
            // Wait for enough accept acknowledgments (quorum)
            Logger.log(Logger.CONDITIONAL_COLLECT, "------------- ACCEPT SIZE: " + acceptAcks.size() + " QUORUM SIZE: " + memberManager.getQuorumSize() + "----------");
            for (int i = 0; i < 12; i++) {
                Thread.sleep(1000);
                if (acceptAcks.size() == (memberManager.getQuorumSize() - 1)) { break; }
                Logger.log(Logger.CONDITIONAL_COLLECT, "current accept acks: " + acceptAcks.size() + "/" + memberManager.getQuorumSize());
            }
            
            // Check if we got enough acknowledgments
            if (checkAckQuorum(acceptAcks)) {
                Logger.log(Logger.CONDITIONAL_COLLECT, "Received sufficient accept acknowledgments");
                acceptAcked = true;
                
                // Phase 3: Send DECIDE messages to all members
                for (String memberId : memberManager.getMemberLinks().keySet()) {
                    String payload = createAck(value);
                    memberManager.sendToMember(memberId, payload, "DECIDE");
                    writeAcked = false;
                    acceptAcked = false;
                    collected.clear();
                    writeAcks.clear();
                    acceptAcks.clear();
                    isCollected = false;
                }
                
                // Phase 4: Decide the value
                Logger.log(Logger.CONDITIONAL_COLLECT, "Deciding value");
                collected.clear();
                writeAcks.clear();
                acceptAcks.clear();
                isCollected = false;
                
                epochConsensus.decide(value);
            } else {
                Logger.log(Logger.CONDITIONAL_COLLECT, "Failed to receive sufficient accept acknowledgments");
                abort();
            }
        } catch (Exception e) {
            Logger.log(Logger.CONDITIONAL_COLLECT, "Error waiting for accept acknowledgments: " + e.getMessage());
        }
    }
    
    /**
     * Aborts the consensus process
     */
    @Override
    protected void abort() {
        Logger.log(Logger.CONDITIONAL_COLLECT, "Aborting conditional collect");
        collected.clear();
        writeAcks.clear();
        acceptAcks.clear();
        isCollected = false;
        epochConsensus.abort();
        for (String member : memberManager.getMemberLinks().keySet()) {
            memberManager.sendToMember(member, "", "ABORT");
        }
    }
}