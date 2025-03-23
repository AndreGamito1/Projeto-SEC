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
    private Map <String, String>  collected = new HashMap<>();
    private Map <String, String> writeAcks = new HashMap<>();
    private Map <String, String> acceptAcks = new HashMap<>();
    private boolean isCollected = false;
    private final String name;
    private final Member member;
    
    /**
     * Creates a new ConditionalCollect instance
     * 
     * 
     * @param memberManager The manager to handle communications with members
     * @param outputPredicate The predicate that defines when collection is valid
     */
    public ConditionalCollect(MemberManager memberManager, Member member) {
        this.memberManager = memberManager;
        this.name = memberManager.getName();
        this.member = member;
    }

    public void setCollected(String payload) {
        collected = parseCollectedPayload(payload);
        isCollected = true;
    }

    public void appendState(String statePayload){
        Map<String, String> state = parseCollectedPayload(statePayload);
        collected.putAll(state);
        Logger.log(Logger.CONDITIONAL_COLLECT, "State appended: " + statePayload);
        if (collected.size() >= getQuorumSize()) { 
            isCollected = true; 
            Logger.log(Logger.CONDITIONAL_COLLECT, "Quorum reached, collected: " + createCollectedPayload(collected));
        }
    }

    public void appendAck(String ackPayload, String ackType) {
        if (ackType.equals("ACCEPT")) {
            Map<String, String> ack = parseAck(ackPayload);
            acceptAcks.putAll(ack);
            Logger.log(Logger.CONDITIONAL_COLLECT, "Accept ack appended: " + ackPayload);
            Logger.log(Logger.CONDITIONAL_COLLECT, "Accept acks state: " + acceptAcks);
        } else if (ackType.equals("WRITE")) {
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

    public String createAck(String value) {
        // Format as "membername: [value]"
        StringBuilder builder = new StringBuilder();
        builder.append(name).append(": [");
        
        // Add the value if it's not null
        if (value != null) {
            builder.append(value);
        }
        
        builder.append("]");
        
        return builder.toString();
    }
    
    /**
     * Inputs a message to the conditional collect primitive.
     * 
     * @param message The message to input
     */
    public void input(String message) {
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
            while ((collected.size() <= getQuorumSize() ) &&  timeout(7)) {
                Thread.sleep(100);
                System.out.println("Waiting for states");
                Logger.log(Logger.CONDITIONAL_COLLECT, "current collected: " + createCollectedPayload(collected));
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
                abort(); // Send ABORT message to all members
            }
        } catch (Exception e) {
            Logger.log(Logger.CONDITIONAL_COLLECT, "Error collecting States, aborting: " + e.getMessage());
        }
    }


    private void processCollected() {
        Logger.log(Logger.CONDITIONAL_COLLECT, "Processing collected message: " + createCollectedPayload(collected));
        
        // Find the most frequent value and its count
        Map<String, Integer> valueCounts = new HashMap<>();
        String leaderValue = null;
        
        for (Map.Entry<String, String> entry : collected.entrySet()) {
            String memberId = entry.getKey();
            String value = entry.getValue();
            
            // Store leader's value separately
            if (memberId.equals(memberManager.getLeaderName())) {
                Logger.log(Logger.CONDITIONAL_COLLECT, "Leader's value: " + value);
                leaderValue = value;
            }
            
            // Count occurrences of each value
            valueCounts.put(value, valueCounts.getOrDefault(value, 0) + 1);
        }
        
        // Check for quorum
        int quorumSize = getQuorumSize();
        boolean foundQuorum = false;
        
        for (Map.Entry<String, Integer> entry : valueCounts.entrySet()) {
            String value = entry.getKey();
            int count = entry.getValue();
            
            if (count >= quorumSize) {
                // Found a quorum for this value
                foundQuorum = true;
                
                // Check if quorum is for null (undefined state)
                if (value == null || value.equals("null")) {
                    // For null quorum, follow leader value logic
                    if (leaderValue != null && !leaderValue.equals("null")) {
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
            if (leaderValue != null &&  !leaderValue.equals("null")) {
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

    private void consensusLoop(String value) {
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
    
    private void waitForWrite(String value) {
        try {
            // Wait for enough write acknowledgments (quorum)
            while ((writeAcks.size() < getQuorumSize()) && timeout(7)) {
                Thread.sleep(100);
                System.out.println("Waiting for write acknowledgments");
                Logger.log(Logger.CONDITIONAL_COLLECT, "current write acks: " + writeAcks.size() + "/" + getQuorumSize());
            }
            
            // Check if we got enough acknowledgments
            if (writeAcks.size() >= getQuorumSize()) {
                Logger.log(Logger.CONDITIONAL_COLLECT, "Received sufficient write acknowledgments");
                
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
    
    private void waitForAccept(String value) {
        try {
            // Wait for enough accept acknowledgments (quorum)
            while ((acceptAcks.size() < getQuorumSize()) && timeout(7)) {
                Thread.sleep(100);
                System.out.println("Waiting for accept acknowledgments");
                Logger.log(Logger.CONDITIONAL_COLLECT, "current accept acks: " + acceptAcks.size() + "/" + getQuorumSize());
            }
            
            // Check if we got enough acknowledgments
            if (acceptAcks.size() >= getQuorumSize()) {
                Logger.log(Logger.CONDITIONAL_COLLECT, "Received sufficient accept acknowledgments");
                
            // Phase 3: Decide the value
            Logger.log(Logger.CONDITIONAL_COLLECT, "Deciding value: " + value);
            collected.clear();
            writeAcks.clear();
            acceptAcks.clear();
            isCollected = false;
            member.decide(value);

            // Phase 4: Send DECIDE messages to all members
            // TODO - Confirm if thi is right
             for (String memberId : memberManager.getMemberLinks().keySet()) {
                String payload = createAck(value);
                memberManager.sendToMember(memberId, payload, "DECIDE");
            }

            } else {
                abort();
                Logger.log(Logger.CONDITIONAL_COLLECT, "Failed to receive sufficient accept acknowledgments");
            }
        } catch (Exception e) {
            Logger.log(Logger.CONDITIONAL_COLLECT, "Error waiting for accept acknowledgments: " + e.getMessage());
        }
    }

    /**
     * Creates a formatted string payload from the collected map
     * @return String in format "key1: [value1], key2: [value2], ..."
     */
    private String createCollectedPayload(Map<String, String> collected) {
        StringBuilder payload = new StringBuilder();
        boolean first = true;
        
        for (Map.Entry<String, String> entry : collected.entrySet()) {
            if (!first) {
                payload.append(", ");
            } else {
                first = false;
            }
            
            payload.append(entry.getKey())
                .append(": [")
                .append(entry.getValue())
                .append("]");
        }

        if (payload.toString().isEmpty()) {
            return memberManager.getName() + ": []";
        }
        
        return payload.toString();
    }


    /**
     * Parses a formatted collected payload back into a HashMap
     * @param payload String in format "key1: [value1], key2: [value2], ..."
     * @return HashMap containing the parsed key-value pairs
     */
    private Map<String, String> parseCollectedPayload(String payload) {
        Map<String, String> collected = new HashMap<>();
        
        // Handle empty payload
        if (payload == null || payload.trim().isEmpty()) {
            return collected;
        }
        
        // Split the payload by comma followed by space
        String[] entries = payload.split(", ");
        
        for (String entry : entries) {
            // Find the position of ": [" which separates key from value
            int separatorIndex = entry.indexOf(": [");
            
            if (separatorIndex > 0) {
                // Extract the key (everything before ": [")
                String key = entry.substring(0, separatorIndex);
                
                // Extract the value (everything between "[" and "]")
                int valueStartIndex = separatorIndex + 3; // Skip ": ["
                int valueEndIndex = entry.lastIndexOf("]");
                
                // Handle the case where value is empty (null case)
                if (valueEndIndex == valueStartIndex) {
                    collected.put(key, null);
                } else if (valueEndIndex > valueStartIndex) {
                    String value = entry.substring(valueStartIndex, valueEndIndex);
                    collected.put(key, value);
                }
            }
        }
        
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

    private int getQuorumSize() {
        return memberManager.getMemberLinks().size() / 2 + 1;
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
