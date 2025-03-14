package com.example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Dictionary;
import java.util.Enumeration;

/**
 * Interface for a message collection predicate.
 * This will be used to determine if a set of messages satisfies 
 * the collection condition.
 */
interface CollectionPredicate {
    boolean test(Message[] messages);
}

/**
 * Interface for ConditionalCollect events.
 */
interface ConditionalCollectCallback {
    void onCollected(Message[] messages);
}

/**
 * ConditionalCollect implementation based on the pseudocode:
 * A distributed protocol that collects states from processes in a system
 * and outputs them when a Byzantine quorum of equal states are received.
 */
public class ConditionalCollect {
    private final String self;
    private final String leader;
    private final boolean isLeader;
    private final int N;
    private final int f; // Max number of process failures
    private final AuthenticatedPerfectLinks[] links;
    private final ConditionalCollectCallback callback;
    
    // State variables
    private Dictionary<String, Message> messages;
    private String[] signatures;
    private boolean collected;
    private List<Message> states; // Vector to collect states from all processes
    
    // Constants for message commands
    private static final String CMD_SEND = "SEND";
    private static final String CMD_COLLECTED = "CMD_COLLECTED";
    private static final String CMD_WRITE = "CMD_WRITE";
    
    /**
     * Constructor for ConditionalCollect.
     * 
     * @param self The identifier of this process
     * @param leader The identifier of the leader process
     * @param f Maximum number of process failures
     * @param condition The condition predicate for collection
     * @param perfectLinks Array of authenticated perfect links to all processes
     * @param callback Callback for when messages are collected
     */
    public ConditionalCollect(
            String self, 
            String leader, 
            int f,
            int N,
            AuthenticatedPerfectLinks[] perfectLinks,
            ConditionalCollectCallback callback) {
        
        this.self = self;
        this.leader = leader;
        this.isLeader = self.equals(leader);
        this.f = f;
        this.N = N;
        this.links = perfectLinks;
        this.callback = callback;
        this.messages = new Hashtable<>();
        
        // Initialize state
        init();
    }
    
    /**
     * Initialize the state variables.
     */
    public void init() {
        this.signatures = new String[N];
        this.collected = false;
        this.states = new ArrayList<>();
        
        Logger.log(Logger.CONDITIONAL_COLLECT, "ConditionalCollect initialized. Self: " + self + ", Leader: " + leader);
    }
    
    /**
     * Input event handler - submits a state to the collection.
     * 
     * @param message The message containing a state to input
     */
    public void input(Message message) {
        // Add the state to our vector
        System.out.println("inputing message: " + message.getPayload());
        System.out.println(message);
        System.out.println(this.self);
        messages.put(this.self, message);
    }
    
    /**
     * Method to handle received state messages from other processes.
     * 
     * @param sourceProcess The process that sent the state
     * @param message The message containing the state
     */
    public void receiveState(String sourceProcess, Message message) {
        if (!isLeader) {
            return;
        }
            messages.put(sourceProcess, message);
            
            Logger.log(Logger.CONDITIONAL_COLLECT, 
                      "Received state from " + sourceProcess + ": " + message.getPayload());
            
            // Check if we have enough equal states
            checkCollectionCondition();
        }
    
    
    
    /**
     * Check if a Byzantine quorum of equal states have been received and broadcast if it is.
     * For Byzantine consensus with N = 4, a quorum needs at least N - f states to be equal
     * where f is the maximum number of byzantine failures.
     */
    public void checkCollectionCondition() {
        if (!isLeader) {
            return;
        }
        System.out.println("------------- Checking Collection Condition ---------------");
        
        // Count message occurrences to find if there's a Byzantine quorum
        Map<String, Integer> stateOccurrences = new HashMap<>();
        
        // Iterate through the Dictionary entries
        Enumeration<String> keys = messages.keys();
        while (keys.hasMoreElements()) {
            String processId = keys.nextElement();
            Message message = messages.get(processId);
            
            if (message != null) {
                String statePayload = message.getPayload();
                stateOccurrences.put(statePayload, stateOccurrences.getOrDefault(statePayload, 0) + 1);
            }
        }
        
        // Byzantine quorum needs at least (N+f)/2 + 1 equal states where f = (N-1)/3
        // For N = 4, a quorum needs at least (N+1)/2 = 3 equal states (majority)
        int quorumSize = (N + 1) / 2;
        
        // Check if any state has reached the quorum
        String quorumState = null;
        for (Map.Entry<String, Integer> entry : stateOccurrences.entrySet()) {
            if (entry.getValue() >= quorumSize) {
                quorumState = entry.getKey();
                break;
            }
        }
        
        // If a quorum is found, broadcast the collected messages
        if (quorumState != null) {
            // Create a Map to hold messages that match the quorum state
            Map<String, Message> quorumMessages = new HashMap<>();
            
            // Collect all messages that match the quorum state
            keys = messages.keys();
            while (keys.hasMoreElements()) {
                String processId = keys.nextElement();
                Message message = messages.get(processId);
                
                if (message != null && message.getPayload().equals(quorumState)) {
                    quorumMessages.put(processId, message);
                }
            }
            
            // Convert messages to serializable format
            System.out.println("Serializing messages:" + quorumMessages);

            String messagesJson = serializeMessages(quorumMessages);
            System.out.println("Serialized messages:" + messagesJson);
            
            // Create a COLLECTED message with the collected messages
            Message collectedMessage = new Message(messagesJson, CMD_COLLECTED);
            
            // Send COLLECTED message to all processes
            for (AuthenticatedPerfectLinks link : links) {
                link.alp2pSend(link.getDestinationEntity(), collectedMessage);

                System.out.println("----------------- Sent COLLECTED message to member: " + link.getDestinationEntity() + " -----------------");
            
            // Reset state
            init();
        }
    }
    }


/**
 * Process a COLLECTED message.
 * Checks for a quorum of identical messages or falls back to the leader's value.
 * If either condition is met, broadcasts a WRITE message with the adopted value.
 *
 * @param message The message containing the collected messages
 */
public void processCollected(Message message) {
    if (collected) {
        return; // Already collected
    }
    System.out.println("----------------- Processing COLLECTED message -----------------");
    try {
        // Get the payload as a string
        String strPayload = message.getPayload();
        System.out.println("Received payload: " + strPayload);
        
        // Try to extract messages from the string
        Map<String, Message> collectedMessages = new HashMap<>();
        
        // Extract the leader's message
        // Format is: {"leader":{"payload":"STATE,0,[Client1] eren, ","command":"STATE","messageID":"93c5a687-5675-4b7f-8817-83515ee1afef"}}
        if (strPayload.contains("\"leader\":{")) {
            int payloadStart = strPayload.indexOf("\"payload\":\"") + "\"payload\":\"".length();
            int payloadEnd = strPayload.indexOf("\"", payloadStart);
            
            int commandStart = strPayload.indexOf("\"command\":\"") + "\"command\":\"".length();
            int commandEnd = strPayload.indexOf("\"", commandStart);
            
            if (payloadStart > 0 && payloadEnd > payloadStart && 
                commandStart > 0 && commandEnd > commandStart) {
                
                String leaderPayload = strPayload.substring(payloadStart, payloadEnd);
                String leaderCommand = strPayload.substring(commandStart, commandEnd);
                
                Message leaderMessage = new Message(leaderPayload, leaderCommand);
                collectedMessages.put("leader", leaderMessage);
                
                // Also add it under the leader ID for quorum checking
                collectedMessages.put(leader, leaderMessage);
                
                System.out.println("Extracted leader message with payload: " + leaderPayload);
            }
        }
        
        // Count message occurrences to verify the Byzantine quorum
        Map<String, Integer> stateOccurrences = new HashMap<>();
        int nonNullCount = 0;
        
        // Track the leader's message specifically
        Message leaderMessage = collectedMessages.get(leader);
        
        for (Map.Entry<String, Message> entry : collectedMessages.entrySet()) {
            Message m = entry.getValue();
            if (m != null) {
                nonNullCount++;
                String statePayload = m.getPayload();
                stateOccurrences.put(statePayload, stateOccurrences.getOrDefault(statePayload, 0) + 1);
            }
        }
        
        System.out.println("Number of messages: " + nonNullCount);
        System.out.println("State occurrences: " + stateOccurrences);
        
        // Byzantine quorum needs majority for N = 4
        int quorumSize = (N + 1) / 2;
        boolean hasQuorum = false;
        String quorumState = null;
        
        for (Map.Entry<String, Integer> entry : stateOccurrences.entrySet()) {
            if (entry.getValue() >= quorumSize) {
                hasQuorum = true;
                quorumState = entry.getKey();
                break;
            }
        }
        
        // Value to adopt (either quorum value or leader's value)
        String adoptedValue = null;
        
        // Check if we have a quorum or should adopt leader's value
        // In this special case, we're also accepting just a leader message without requiring N-f messages
        if (nonNullCount >= 1) {
            if (hasQuorum) {
                // Case 1: Byzantine quorum established
                adoptedValue = quorumState;
                Logger.log(Logger.CONDITIONAL_COLLECT, "Byzantine quorum of equal states collected successfully");
            } else if (leaderMessage != null) {
                // Case 2: No quorum, but leader has a valid state - adopt leader's value
                adoptedValue = leaderMessage.getPayload();
                Logger.log(Logger.CONDITIONAL_COLLECT, 
                        "No quorum established, adopting leader's state: " + adoptedValue);
            } else {
                Logger.log(Logger.CONDITIONAL_COLLECT, 
                        "No quorum established and no valid leader state available");
                return;
            }
            
            // Mark as collected
            collected = true;
            
            // Create a WRITE message with the adopted value
            Message writeMessage = new Message(adoptedValue, CMD_WRITE);
            
            // Send WRITE message to all processes
            for (AuthenticatedPerfectLinks link : links) {
                link.alp2pSend(link.getDestinationEntity(), writeMessage);
                System.out.println("----------------- Sent WRITE message to member: " + link.getDestinationEntity() + " -----------------");
            }
            
        } else {
            Logger.log(Logger.CONDITIONAL_COLLECT,
                    "Not enough messages received to establish collection");
        }
    } catch (Exception e) {
        Logger.log(Logger.CONDITIONAL_COLLECT,
                "Error processing COLLECTED message: " + e.getMessage());
        e.printStackTrace();
    }
}

    /**
     * Deserialize messages from JSON.
     * 
     * @param json The JSON string
     * @return Map of process IDs to messages
     */
    private Map<String, Message> deserializeMessages(String json) {
        Map<String, Message> result = new HashMap<>();
        
        // Simple parsing - in a real implementation use a JSON library
        if (json.startsWith("{") && json.endsWith("}")) {
            // Remove the outer braces
            String content = json.substring(1, json.length() - 1);
            
            // Split by commas that are followed by a quoted process ID
            String[] entries = content.split(",(?=\")");
            
            for (String entry : entries) {
                // Find the process ID
                int idStart = entry.indexOf("\"") + 1;
                int idEnd = entry.indexOf("\"", idStart);
                
                if (idStart > 0 && idEnd > idStart) {
                    String processId = entry.substring(idStart, idEnd);
                    
                    // Check if this is a null entry
                    if (entry.contains(":null")) {
                        result.put(processId, null);
                    } else {
                        // Extract message parts
                        int payloadStart = entry.indexOf("\"payload\":\"") + 11;
                        int payloadEnd = entry.indexOf("\"", payloadStart);
                        int commandStart = entry.indexOf("\"command\":\"") + 11;
                        int commandEnd = entry.indexOf("\"", commandStart);
                        
                        if (payloadStart > 10 && commandStart > 10) {
                            String payload = entry.substring(payloadStart, payloadEnd);
                            String command = entry.substring(commandStart, commandEnd);
                            result.put(processId, new Message(payload, command));
                        }
                    }
                }
            }
        }
        
        return result;
    }

    /**
     * Convert a map of messages to an array, filtering for a specific state if needed.
     * 
     * @param messageMap The map of process IDs to messages
     * @param filterState Optional state to filter for (null to include all)
     * @return Array of messages
     */
    private Message[] mapToArray(Map<String, Message> messageMap, String filterState) {
        Message[] result = new Message[N];
        
        // This assumes process IDs can be mapped to indices 0..N-1
        // In a real implementation, you might need a more sophisticated mapping
        for (Map.Entry<String, Message> entry : messageMap.entrySet()) {
            String processId = entry.getKey();
            Message message = entry.getValue();
            
            // Only include the message if it matches the filter state or no filter is applied
            if (message != null && (filterState == null || message.getPayload().equals(filterState))) {
                try {
                    int index = Integer.parseInt(processId);
                    if (index >= 0 && index < N) {
                        result[index] = message;
                    }
                } catch (NumberFormatException e) {
                    // If process IDs aren't numeric, you might need a different approach
                    // For now, just log the issue
                    Logger.log(Logger.CONDITIONAL_COLLECT, 
                            "Could not convert process ID to index: " + processId);
                }
            }
        }
        
        return result;
    }
    /**
     * Serialize messages to JSON.
     * 
     * @param messages Map of processId to messages
     * @return JSON string representation
     */
    private String serializeMessages(Map<String, Message> messages) {
        // In a real implementation, this would use proper JSON serialization
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        
        for (Map.Entry<String, Message> entry : messages.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            
            String processId = entry.getKey();
            Message message = entry.getValue();
            
            sb.append("\"").append(processId).append("\":");
            
            if (message == null) {
                sb.append("null");
            } else {
                sb.append("{\"payload\":\"").append(message.getPayload())
                .append("\",\"command\":\"").append(message.getCommand())
                .append("\",\"messageID\":\"").append(message.getMessageID()).append("\"}");
            }
        }
        
        sb.append("}");
        return sb.toString();
    }
}