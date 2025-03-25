package com.depchain.consensus;

import java.util.*;
import java.util.function.Predicate;
import org.json.JSONObject;

import com.depchain.networking.AuthenticatedMessage;
import com.depchain.utils.*;

/**
 * Implementation of the Conditional Collect primitive for Byzantine CONDITIONAL_COLLECT
 */
public class ConditionalCollect {
    private final MemberManager memberManager;
    private Map <String, EpochState>  collected = new HashMap<>();
    private Map <String, String> writeAcks = new HashMap<>();
    private Map <String, String> acceptAcks = new HashMap<>();
    private boolean isCollected = false;
    private boolean writeAcked = false;
    private boolean acceptAcked = false;
    private final String name;
    private final ByzantineEpochConsensus epochConsensus;
    
    /**
     * Creates a new ConditionalCollect instance
     * 
     * 
     * @param memberManager The manager to handle communications with members
     * @param outputPredicate The predicate that defines when collection is valid
     */
    public ConditionalCollect(MemberManager memberManager, ByzantineEpochConsensus epochConsensus) {
        this.memberManager = memberManager;
        this.name = memberManager.getName();
        this.epochConsensus = epochConsensus;
    }

    public void setCollected(String payload) {
        collected = parseCollectedPayload(payload);
        isCollected = true;
    }

    public void appendState(String statePayload){
        Map<String, EpochState> state = parseCollectedPayload(statePayload);
        Logger.log(Logger.CONDITIONAL_COLLECT, "Appending state: " + state);

        collected.putAll(state);
        Logger.log(Logger.CONDITIONAL_COLLECT, "State appended: " + statePayload);
        Logger.log(Logger.CONDITIONAL_COLLECT, "Collected state: " + collected);
        
        if (checkQuorum(collected)) { 
            isCollected = true;
        }
    }

    public boolean checkQuorum(Map<String, EpochState> collected) {
        Logger.log(Logger.CONDITIONAL_COLLECT, "Checking quorum with " + collected.size() + " entries");
        
        // If we don't have enough entries, quorum can't be reached
        if (collected.size() < memberManager.getQuorumSize()) {
            Logger.log(Logger.CONDITIONAL_COLLECT, "Not enough entries for quorum: " + collected.size() + " < " + memberManager.getQuorumSize());
            return false;
        }
        
        // Count occurrences of each value
        Map<String, Integer> valueCounts = new HashMap<>();
        
        // Group by value and count
        for (Map.Entry<String, EpochState> entry : collected.entrySet()) {
            String key = entry.getKey();
            EpochState state = entry.getValue();
            
            // Log each entry being processed
            Logger.log(Logger.CONDITIONAL_COLLECT, "Processing entry: " + key + " = " + state);
            
            // Handle null states
            if (state == null) {
                valueCounts.put("NULL_VALUE", valueCounts.getOrDefault("NULL_VALUE", 0) + 1);
                continue;
            }
            
            String value = state.getValue();
            valueCounts.put(value, valueCounts.getOrDefault(value, 0) + 1);
        }
        
        // Log value counts
        Logger.log(Logger.CONDITIONAL_COLLECT, "Value counts: " + valueCounts);
        
        // Check if any value has reached quorum
        for (Map.Entry<String, Integer> entry : valueCounts.entrySet()) {
            String value = entry.getKey();
            Integer count = entry.getValue();
            
            Logger.log(Logger.CONDITIONAL_COLLECT, "Checking value: " + value + " with count: " + count);
            
            if (count >= memberManager.getQuorumSize()) {
                Logger.log(Logger.CONDITIONAL_COLLECT, "Quorum reached with value: " + value);
                return true;
            }
        }
        
        Logger.log(Logger.CONDITIONAL_COLLECT, "No quorum reached");
        return false;
    }

    public boolean checkAckQuorum(Map<String, String> Acks) {
        // If we don't have enough entries, quorum can't be reached
        if (Acks.size() < memberManager.getQuorumSize()) {
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
        Logger.log(Logger.CONDITIONAL_COLLECT, "Value counts: " + valueCounts);
        
        // Check if any value has reached quorum
        for (Integer count : valueCounts.values()) {
            if (count >= memberManager.getQuorumSize()) {
                Logger.log(Logger.CONDITIONAL_COLLECT, "Quorum reached with value: " + count);
                return true;
            }
        }
        
        return false;
    }
    public void appendAck(String ackPayload, String ackType) {
        if (ackType.equals("ACCEPT")) {
            if (acceptAcked) { return; }
            Map<String, String> ack = parseAck(ackPayload);
            acceptAcks.putAll(ack);
            Logger.log(Logger.CONDITIONAL_COLLECT, "Accept ack appended: " + ackPayload);
            Logger.log(Logger.CONDITIONAL_COLLECT, "Accept acks state: " + acceptAcks);
        } else if (ackType.equals("WRITE")) {
            if (writeAcked) { return; }
            Map<String, String> ack = parseAck(ackPayload);
            writeAcks.putAll(ack);
            Logger.log(Logger.CONDITIONAL_COLLECT, "Write ack appended: " + ackPayload);
            Logger.log(Logger.CONDITIONAL_COLLECT, "Write acks state: " + writeAcks);
        }
    }

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
     * Inputs a message to the conditional collect primitive.
     * 
     * @param message The message to input
     */
    public void input(EpochState message) {
        try {
            // If this member is the leader
            if (memberManager.isLeader()) {
                Logger.log(Logger.CONDITIONAL_COLLECT, "Inputtng to leader");
                collected.put(memberManager.getName(), message);
                // Send READ message to all members
                for (String member : memberManager.getMemberLinks().keySet()) {
                    memberManager.sendToMember(member, "", "READ");
                }
                // wait for the STATE replies from each member
                Thread collectionThread = new Thread(() -> {
                waitForStates();
                });
                collectionThread.start();

            } 
            else {
                Logger.log(Logger.CONDITIONAL_COLLECT, "Inputting to member");
                // Send to leader the STATE message
                String statePayload = createCollectedPayload(collected);
                Logger.log(Logger.CONDITIONAL_COLLECT, "Sending state to leader: " + statePayload);
                memberManager.sendToMember(memberManager.getLeaderName(), statePayload, "STATE");
                
                // Start waitForCollected in a new thread
                Thread collectionThread = new Thread(() -> {
                    waitForCollected();
                });
                collectionThread.start();
            }
            Logger.log(Logger.CONDITIONAL_COLLECT, "Input sent to conditional collect: " + message);
        } catch (Exception e) {
            Logger.log(Logger.CONDITIONAL_COLLECT, "Error in ConditionalCollect.input: " + e.getMessage());
        }
    

    }


    private void waitForCollected() {
        try {
            // Wait for leader to send the collected message
            while (!isCollected && timeout(7)) {
                Thread.sleep(1000);
                System.out.println("Waiting for collected message");
                Logger.log(Logger.CONDITIONAL_COLLECT, "current collected: " + createCollectedPayload(collected));
            }

            if (isCollected) {
                Logger.log(Logger.CONDITIONAL_COLLECT, "Collected message received: " + createCollectedPayload(collected));
                processCollected();
            }
            else {
                Logger.log(Logger.CONDITIONAL_COLLECT, "Timeout waiting for collected message");
                abort();
            }

        } catch (Exception e) {
            Logger.log(Logger.CONDITIONAL_COLLECT, "Error waiting for collected message: " + e.getMessage());
        }
            
    }

    private void waitForStates() {
        try {
            // Wait for all members to send their state
            while ((!isCollected ) &&  timeout(7)) {
                Thread.sleep(100);
                System.out.println("Waiting for states");
                Logger.log(Logger.CONDITIONAL_COLLECT, "current collected: " + createCollectedPayload(collected));
                Logger.log(Logger.CONDITIONAL_COLLECT, "Quorum size: " + memberManager.getQuorumSize());
                Logger.log(Logger.CONDITIONAL_COLLECT, "Collected size: " + collected.size());
            }

            if (isCollected) {
                // Send to all members
                String collectedPayload = createCollectedPayload(collected);
                for (String member : memberManager.getMemberLinks().keySet()) {
                    memberManager.sendToMember(member, collectedPayload, "COLLECTED");
                }
                processCollected();

                
            }
            else {
                Logger.log(Logger.CONDITIONAL_COLLECT, "Timeout waiting for states");
                abort(); // Send ABORT message to all members
            }
        } catch (Exception e) {
            Logger.log(Logger.CONDITIONAL_COLLECT, "Error collecting States, aborting: " + e.getMessage());
        }
    }


    private void processCollected() {
        Logger.log(Logger.CONDITIONAL_COLLECT, "Processing collected message: " + createCollectedPayload(collected));
        
        // Find the most frequent value and its count
        Map<EpochState, Integer> valueCounts = new HashMap<>();
        EpochState leaderValue = null;
        
        for (Map.Entry<String, EpochState> entry : collected.entrySet()) {
            String memberId = entry.getKey();
            EpochState value = entry.getValue();
            
            // Store leader's value separately
            if (memberId.equals(memberManager.getLeaderName())) {
                Logger.log(Logger.CONDITIONAL_COLLECT, "Leader's value: " + value);
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
                        Logger.log(Logger.CONDITIONAL_COLLECT, "Null Quorum found, adopting leader's value: " + leaderValue);
                        //adoptValue(leaderValue);
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
            if (leaderValue != null &&  !leaderValue.toString().equals("null")) {
                Logger.log(Logger.CONDITIONAL_COLLECT, "No quorum, adopting leader's value: " + leaderValue);
                //adoptValue(leaderValue);
                consensusLoop(leaderValue);
            } else {
                // No quorum and no leader value, abort
                Logger.log(Logger.CONDITIONAL_COLLECT, "No quorum and no leader value, aborting");
                abort();
            }
        }
    }

    private void consensusLoop(EpochState value) {
        // Log the consensus value
        Logger.log(Logger.CONDITIONAL_COLLECT, "Consensus value: " + value);
        
        // Phase 1: Send WRITE messages to all members
        for (String memberId : memberManager.getMemberLinks().keySet()) {
            String payload = createAck(value);
            memberManager.sendToMember(memberId, payload, "WRITE");
        }
        
        // Start a new thread for the write phase
        Thread writeThread = new Thread(() -> {
            waitForWrite(value);
        });
        writeThread.setName("WritePhase-" + System.currentTimeMillis());
        writeThread.start();
        System.out.println("IM NOT BLOCKING -------------------");
    }
    
    private void waitForWrite(EpochState value) {
        try {
            // Wait for enough write acknowledgments (quorum)
            for (int i = 0; i < 10; i++) {
                Thread.sleep(1000);
                if (writeAcks.size() == memberManager.getQuorumSize()) { break; }
                System.out.println("Waiting for write acknowledgments");
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
    
    private void waitForAccept(EpochState value) {
        try {
            // Wait for enough accept acknowledgments (quorum)
            Logger.log(Logger.CONDITIONAL_COLLECT, "------------- ACCEPT SIZE: " + acceptAcks.size() + " QUORUM SIZE: " + memberManager.getQuorumSize() + "----------");
            for (int i = 0; i < 10; i++) {
                Thread.sleep(1000);
                if (acceptAcks.size() == memberManager.getQuorumSize()) { break; }
                System.out.println("Waiting for accept acknowledgments");
                Logger.log(Logger.CONDITIONAL_COLLECT, "current accept acks: " + acceptAcks.size() + "/" + memberManager.getQuorumSize());
            }
            
            // Check if we got enough acknowledgments
            if (checkAckQuorum(acceptAcks)) {
                Logger.log(Logger.CONDITIONAL_COLLECT, "Received sufficient accept acknowledgments");
                acceptAcked = true;
                
            // Phase 3: Decide the value
            Logger.log(Logger.CONDITIONAL_COLLECT, "Deciding value: " + value);
            collected.clear();
            writeAcks.clear();
            acceptAcks.clear();
            isCollected = false;
            epochConsensus.decide(value);

            // Phase 4: Send DECIDE messages to all members
            // TODO - Confirm if thi is right
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

            } else {
                Logger.log(Logger.CONDITIONAL_COLLECT, "Failed to receive sufficient accept acknowledgments");
                abort();

            }
        } catch (Exception e) {
            Logger.log(Logger.CONDITIONAL_COLLECT, "Error waiting for accept acknowledgments: " + e.getMessage());
        }
    }

    /**
     * Creates a formatted string payload from the collected map
     * @return String in format "key1: [timestamp, value1], key2: [timestamp2, value2], ..."
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
     * @param payload String in format "key1: [timstamp1, value1], key2: [timestamp2, value2], ..."
     * @return HashMap containing the parsed key-value pairs
     */
    private Map<String, EpochState> parseCollectedPayload(String payload) {
        Map<String, EpochState> collected = new HashMap<>();
        Logger.log(Logger.CONDITIONAL_COLLECT, "Parsing collected payload: " + payload);
        
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
        
        Logger.log(Logger.CONDITIONAL_COLLECT, "Parsed collected payload: " + collected);
        return collected;
    }
    
    private void abort() {
        Logger.log(Logger.CONDITIONAL_COLLECT, "Aborting conditional collect");
        collected.clear();
        writeAcks.clear();
        acceptAcks.clear();
        isCollected = false;
        for (String member : memberManager.getMemberLinks().keySet()) {
            memberManager.sendToMember(member, "", "ABORT");
        }
    }

    /**
     * Checks if the timeout has occurred.
     * 
     * @param seconds The number of seconds to wait
     */
    private boolean timeout(int seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Logger.log(Logger.CONDITIONAL_COLLECT, "Timeout interrupted: " + e.getMessage());
        }
        return true;
    }

}
