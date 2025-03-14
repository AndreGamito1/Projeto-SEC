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
    void onCollected(Message message);
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
    
    // State variables
    private Dictionary<String, Message> messages;
    private Dictionary<String, Message> protocolMessages;
    private String[] signatures;
    private boolean collected;
    private boolean accepted;
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
            int N,
            int f,
            AuthenticatedPerfectLinks[] perfectLinks) {
        
        this.self = self;
        this.leader = leader;
        this.isLeader = self.equals(leader);
        this.f = f;
        this.N = N;
        this.links = perfectLinks;
        this.messages = new Hashtable<>();
        this.protocolMessages = new Hashtable<>();
        this.accepted = false;
        
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
        this.collected = false;
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
            System.out.println("Not the leader, ignoring state message");
            return;
        }
        if (collected) {
            System.out.println("Already collected, ignoring state message");
            return;
        }
        System.out.println("------------- Receiving State ---------------");
        System.out.println("sourceProcess: " + sourceProcess);
        messages.put(sourceProcess, message);
        checkCollectionCondition();
        }
    
    
    
        public void checkCollectionCondition() {
            if (!isLeader) {
                return;
            }
            System.out.println("------------- Checking Collection Condition ---------------");
            
            // Count message occurrences to find if there's a Byzantine quorum
            Map<String, Integer> stateOccurrences = new HashMap<>();
            int nullCount = 0;
            int messageCount = 0;
            
            System.out.println("Total messages in dictionary: " + messages.size());
            System.out.println("Messages: " + messages);
            
            // Iterate through the Dictionary entries
            Enumeration<String> keys = messages.keys();
            while (keys.hasMoreElements()) {
                String processId = keys.nextElement();
                Message message = messages.get(processId);
                messageCount++;
                
                if (message != null) {
                    String statePayload = message.getPayload();
                    if (statePayload == null) {
                        System.out.println("Process " + processId + " has NULL payload");
                        nullCount++;
                    } else {
                        System.out.println("Process " + processId + " has payload: [" + statePayload + "]");
                        // Check if the payload is the string "null"
                        if ("null".equals(statePayload)) {
                            System.out.println("WARNING: Process " + processId + " has string 'null' as payload, not actual null!");
                        }
                        stateOccurrences.put(statePayload, stateOccurrences.getOrDefault(statePayload, 0) + 1);
                    }
                }
            }
            
            // Byzantine quorum needs at least (N+1)/2 states for N=4
            int quorumSize = (N + 1) / 2;
            System.out.println("Total nodes: " + N);
            System.out.println("Required quorum size: " + quorumSize);
            System.out.println("Total null count: " + nullCount);
            System.out.println("State occurrences: " + stateOccurrences);
            
            // Check if any state has reached the quorum
            String quorumState = null;
            for (Map.Entry<String, Integer> entry : stateOccurrences.entrySet()) {
                System.out.println("Checking state: [" + entry.getKey() + "] with count: " + entry.getValue());
                if (entry.getValue() >= quorumSize) {
                    System.out.println("Quorum state found with " + entry.getValue() + " occurrences: [" + entry.getKey() + "]");
                    quorumState = entry.getKey();
                    break;
                }
            }
            
            // Check if null states reached a quorum
            boolean nullQuorum = (nullCount >= quorumSize);
            System.out.println("Null quorum reached: " + nullQuorum + " (" + nullCount + "/" + quorumSize + ")");
           
            // Broadcast if either a regular quorum or a null quorum is found
            if (quorumState != null || nullQuorum) {
                // Create a Map to hold all messages
                Map<String, Message> allMessages = new HashMap<>();

                // Collect all messages
                keys = messages.keys();
                while (keys.hasMoreElements()) {
                    String processId = keys.nextElement();
                    Message message = messages.get(processId);
                    
                    if (message != null) {
                        allMessages.put(processId, message);
                    }
                }

                // Convert messages to serializable format
                System.out.println("Serializing messages:" + messages);
                String messagesJson = serializeMessages(allMessages);  // Using allMessages instead of quorumMessages
                System.out.println("Serialized messages:" + messagesJson);
               
                // Create a COLLECTED message with the collected messages
                Message collectedMessage = new Message(messagesJson, CMD_COLLECTED);
               
                // Send COLLECTED message to all processes
                for (AuthenticatedPerfectLinks link : links) {
                    link.alp2pSend(link.getDestinationEntity(), collectedMessage);
                    System.out.println("----------------- Sent COLLECTED message to member: " + link.getDestinationEntity() + " -----------------");
                }
               
                // Reset state
                init();
                this.collected = true;
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
 * Method to handle received WRITE messages.
 *
 * @param message The authenticated message containing the adopted value
 */
public boolean receiveWrite(AuthenticatedMessage message) {
    if (this.accepted) {
        System.out.println("Already accepted, ignoring WRITE message");
        return false; // Already collected
    }
   
    System.out.println("------------- Receiving WRITE ---------------");
   
    // Store the received message using a unique identifier
    String messageId = message.getMessageID(); // Using the message ID as the key
    protocolMessages.put(messageId, message);
   
    // Check if we have a quorum of equal WRITE messages
    Map<String, Object> quorumResult = checkQuorum(protocolMessages);
   
    // If a quorum is found, send ACCEPT message to all processes
    if ((Boolean)quorumResult.get("quorumFound")) {
        String quorumPayload = (String)quorumResult.get("payload");
        List<String> quorumProcesses = (List<String>)quorumResult.get("processes");
       
        System.out.println("Quorum established by processes: " + quorumProcesses);
       
        // Create an ACCEPT message with the quorum payload
        Message acceptMessage = new Message(quorumPayload, "CMD_ACCEPT");

        // Mark as collected to avoid duplicate processing
        this.accepted = true;

        // Clear the protocol messages
        protocolMessages = new Hashtable<>();
                
        // Send ACCEPT message to all processes
        for (AuthenticatedPerfectLinks link : links) {
            link.alp2pSend(link.getDestinationEntity(), acceptMessage);
            System.out.println("----------------- Sent CMD_ACCEPT message to member: " + link.getDestinationEntity() + " -----------------");
            return true;
        }

    }
    return false;
}

/**
 * Checks if there is a quorum of equal messages present.
 *
 * @param messageDict Dictionary containing processId to message mappings
 * @return A Map containing "quorumFound" (Boolean), "payload" (String) if found, and "processes" (List<String>) if found
 */
private Map<String, Object> checkQuorum(Dictionary<String, Message> messageDict) {
    System.out.println("------------- Checking Quorum ---------------");
    
    // Count message payload occurrences to find if there's a Byzantine quorum
    Map<String, Integer> payloadOccurrences = new HashMap<>();
    Map<String, List<String>> payloadToProcesses = new HashMap<>();
    
    System.out.println("Total messages in dictionary: " + messageDict.size());
    
    // Iterate through the Dictionary entries
    Enumeration<String> keys = messageDict.keys();
    while (keys.hasMoreElements()) {
        String processId = keys.nextElement();
        Message message = messageDict.get(processId);
        
        if (message != null) {
            String payload = message.getPayload();
            if (payload != null) {
                System.out.println("Process " + processId + " has payload: [" + payload + "]");
                
                // Count occurrences
                payloadOccurrences.put(payload, payloadOccurrences.getOrDefault(payload, 0) + 1);
                
                // Track which processes have this payload
                List<String> processes = payloadToProcesses.getOrDefault(payload, new ArrayList<>());
                processes.add(processId);
                payloadToProcesses.put(payload, processes);
            }
        }
    }
    
    // Byzantine quorum needs at least (N+1)/2 states
    int quorumSize = (N + 1) / 2;
    System.out.println("Required quorum size: " + quorumSize);
    System.out.println("Payload occurrences: " + payloadOccurrences);
    
    // Check if any payload has reached the quorum
    String quorumPayload = null;
    List<String> quorumProcesses = null;
    
    for (Map.Entry<String, Integer> entry : payloadOccurrences.entrySet()) {
        System.out.println("Checking payload: [" + entry.getKey() + "] with count: " + entry.getValue());
        if (entry.getValue() >= quorumSize) {
            System.out.println("Quorum found with " + entry.getValue() + " occurrences: [" + entry.getKey() + "]");
            quorumPayload = entry.getKey();
            quorumProcesses = payloadToProcesses.get(quorumPayload);
            break;
        }
    }
    
    // Return result information
    Map<String, Object> result = new HashMap<>();
    result.put("quorumFound", quorumPayload != null);
    result.put("payload", quorumPayload);
    result.put("processes", quorumProcesses);
    return result;
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