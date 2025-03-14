package com.example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

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
 * A distributed protocol that collects messages from processes in a system
 * and outputs them when certain conditions are met.
 */
public class ConditionalCollect {
    private final String self;
    private final String leader;
    private final boolean isLeader;
    private final int N; // Total number of processes
    private final int f; // Max number of process failures
    private final CollectionPredicate collectionCondition;
    private final AuthenticatedPerfectLinks[] links;
    private final ConditionalCollectCallback callback;
    
    // State variables
    private Message[] messages;
    private String[] signatures;
    private boolean collected;
    
    // Constants for message commands
    private static final String CMD_SEND = "SEND";
    private static final String CMD_COLLECTED = "COLLECTED";
    
    /**
     * Constructor for ConditionalCollect.
     * 
     * @param self The identifier of this process
     * @param leader The identifier of the leader process
     * @param n Total number of processes
     * @param f Maximum number of process failures
     * @param condition The condition predicate for collection
     * @param perfectLinks Array of authenticated perfect links to all processes
     * @param callback Callback for when messages are collected
     */
    public ConditionalCollect(
            String self, 
            String leader, 
            int n, 
            int f, 
            CollectionPredicate condition,
            AuthenticatedPerfectLinks[] perfectLinks,
            ConditionalCollectCallback callback) {
        
        this.self = self;
        this.leader = leader;
        this.isLeader = self.equals(leader);
        this.N = n;
        this.f = f;
        this.collectionCondition = condition;
        this.links = perfectLinks;
        this.callback = callback;
        
        // Initialize state
        init();
    }
    
    /**
     * Initialize the state variables.
     */
    public void init() {
        this.messages = new Message[N];
        this.signatures = new String[N];
        this.collected = false;
        
        // Initialize arrays
        for (int i = 0; i < N; i++) {
            messages[i] = null; // UNDEFINED in pseudocode
            signatures[i] = null; // ⊥ in pseudocode
        }
        
        Logger.log(Logger.CONDITIONAL_COLLECT, "ConditionalCollect initialized. Self: " + self + ", Leader: " + leader);
    }
    
    /**
     * Input event handler - submits a message to the collection.
     * 
     * @param message The message to input
     */
    public void input(Message message) {
        // Sign the message
        String signature = sign(self, "cc", self, "INPUT", message.getPayload());
        
        // Find the link to the leader and send the message
        for (AuthenticatedPerfectLinks link : links) {
            if (link.getDestinationEntity().equals(leader)) {
                Message sendMessage = new Message(message.getPayload(), CMD_SEND);
                link.alp2pSend(leader, sendMessage);
                
                Logger.log(Logger.CONDITIONAL_COLLECT, 
                          "Input: Sent message to leader " + leader + ": " + message.getPayload());
                break;
            }
        }

    }
    
    /**
     * Check if the collection condition is met and broadcast if it is.
     */
    public void checkCollectionCondition() {
        if (!isLeader) {
            return;
        }
        
        // Count non-null messages
        int count = 0;
        for (Message m : messages) {
            if (m != null) {
                count++;
            }
        }
        
        // Check if we have enough messages and the condition is satisfied
        if (count >= N - f && collectionCondition.test(messages)) {
            // Convert messages and signatures to serializable format
            String messagesJson = serializeMessages(messages);
            
            // Create a COLLECTED message with the collected messages and signatures
            Message collectedMessage = new Message(messagesJson, CMD_COLLECTED);
            
            // Send COLLECTED message to all processes
            for (AuthenticatedPerfectLinks link : links) {
                link.alp2pSend(link.getDestinationEntity(), collectedMessage);
                Logger.log(Logger.CONDITIONAL_COLLECT, 
                          "Leader sent COLLECTED message to " + link.getDestinationEntity());
            }
            
            // Reset state
            for (int i = 0; i < N; i++) {
                messages[i] = null;
                signatures[i] = null;
            }
        }
    }
    
    /**
     * Process a COLLECTED message.
     * 
     * @param message The message containing the collected messages
     */
    private void processCollectedMessage(Message message) {
        if (collected) {
            return; // Already collected
        }
        
        try {
            // Deserialize messages
            String messagesJson = message.getPayload();
            Message[] collectedMessages = deserializeMessages(messagesJson);
            
            // Count non-null messages
            int count = 0;
            for (Message m : collectedMessages) {
                if (m != null) {
                    count++;
                }
            }
            
            // Check conditions
            if (count >= N - f && collectionCondition.test(collectedMessages)) {
                collected = true;
                Logger.log(Logger.CONDITIONAL_COLLECT, "Messages collected successfully");
                
                // Trigger the callback with the collected messages
                if (callback != null) {
                    callback.onCollected(collectedMessages);
                }
            }
        } catch (Exception e) {
            Logger.log(Logger.CONDITIONAL_COLLECT, 
                      "Error processing COLLECTED message: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Sign a message.
     * In a real implementation, this would use a cryptographic signature.
     * 
     * @param signer The signer
     * @param params The parameters to sign
     * @return The signature
     */
    private String sign(String signer, Object... params) {
        StringBuilder sb = new StringBuilder();
        for (Object param : params) {
            sb.append(param).append("||");
        }
        String content = sb.toString();
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((signer + content).getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    private String serializeMessages(Message[] messages) {
        // In a real implementation, this would use proper JSON serialization
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < messages.length; i++) {
            if (messages[i] == null) {
                sb.append("null");
            } else {
                sb.append("{\"payload\":\"").append(messages[i].getPayload())
                  .append("\",\"command\":\"").append(messages[i].getCommand())
                  .append("\",\"messageID\":\"").append(messages[i].getMessageID()).append("\"}");
            }
            if (i < messages.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * Deserialize messages from JSON.
     * 
     * @param json The JSON string
     * @return Array of messages
     */
    private Message[] deserializeMessages(String json) {
        // In a real implementation, this would use proper JSON deserialization
        // This is a simplified version for demonstration
        Message[] result = new Message[N];
        
        // Simple parsing - in a real implementation use a JSON library
        if (json.startsWith("[") && json.endsWith("]")) {
            String[] parts = json.substring(1, json.length() - 1).split(",(?=\\{|null)");
            for (int i = 0; i < Math.min(parts.length, N); i++) {
                if (parts[i].equals("null")) {
                    result[i] = null;
                } else {
                    // Extract payload and command - very simplified parsing
                    String part = parts[i];
                    int payloadStart = part.indexOf("\"payload\":\"") + 11;
                    int payloadEnd = part.indexOf("\"", payloadStart);
                    int commandStart = part.indexOf("\"command\":\"") + 11;
                    int commandEnd = part.indexOf("\"", commandStart);
                    
                    if (payloadStart > 10 && commandStart > 10) {
                        String payload = part.substring(payloadStart, payloadEnd);
                        String command = part.substring(commandStart, commandEnd);
                        result[i] = new Message(payload, command);
                    }
                }
            }
        }
        
        return result;
    }

}