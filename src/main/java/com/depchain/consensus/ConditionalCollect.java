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
    private boolean isCollected = false;
    private final String name;
    
    /**
     * Creates a new ConditionalCollect instance
     * 
     * 
     * @param memberManager The manager to handle communications with members
     * @param outputPredicate The predicate that defines when collection is valid
     */
    public ConditionalCollect(MemberManager memberManager) {
        this.memberManager = memberManager;
        this.name = memberManager.getName();
    }

    public void setCollected(String payload) {
        collected = parseCollectedPayload(payload);
        isCollected = true;
    }

    public void appendState(String statePayload){
        Map<String, String> state = parseCollectedPayload(statePayload);
        collected.putAll(state);
        Logger.log(Logger.CONDITIONAL_COLLECT, "State appended: " + statePayload);
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
                Logger.log(Logger.CONDITIONAL_COLLECT, "I AM THE LEADER");
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
                Logger.log(Logger.CONDITIONAL_COLLECT, "I AM A MEMBERRRR");
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
                Thread.sleep(1000);
                System.out.println("Waiting for states");
                Logger.log(Logger.CONDITIONAL_COLLECT, "current collected: " + createCollectedPayload(collected));
            }

            if (collected.size() >= getQuorumSize()) { isCollected = true; }

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
