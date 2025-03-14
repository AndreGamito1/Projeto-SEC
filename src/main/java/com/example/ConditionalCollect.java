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
            
            Map<String, Integer> stateOccurrences = new HashMap<>();
            int nullCount = 0;
            int messageCount = 0;
            
            System.out.println("Total messages in dictionary: " + messages.size());
            System.out.println("Messages: " + messages);
            
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
                        if ("null".equals(statePayload)) {
                            System.out.println("WARNING: Process " + processId + " has string 'null' as payload, not actual null!");
                        }
                        stateOccurrences.put(statePayload, stateOccurrences.getOrDefault(statePayload, 0) + 1);
                    }
                }
            }
            
            int quorumSize = (N + 1) / 2;
            System.out.println("Total nodes: " + N);
            System.out.println("Required quorum size: " + quorumSize);
            System.out.println("Total null count: " + nullCount);
            System.out.println("State occurrences: " + stateOccurrences);
            
            String quorumState = null;
            for (Map.Entry<String, Integer> entry : stateOccurrences.entrySet()) {
                System.out.println("Checking state: [" + entry.getKey() + "] with count: " + entry.getValue());
                if (entry.getValue() >= quorumSize) {
                    System.out.println("Quorum state found with " + entry.getValue() + " occurrences: [" + entry.getKey() + "]");
                    quorumState = entry.getKey();
                    break;
                }
            }
            
            boolean nullQuorum = (nullCount >= quorumSize);
            System.out.println("Null quorum reached: " + nullQuorum + " (" + nullCount + "/" + quorumSize + ")");
           
            if (quorumState != null || nullQuorum) {
                Map<String, Message> allMessages = new HashMap<>();
                keys = messages.keys();
                while (keys.hasMoreElements()) {
                    String processId = keys.nextElement();
                    Message message = messages.get(processId);
                    
                    if (message != null) {
                        allMessages.put(processId, message);
                    }
                }

                System.out.println("Serializing messages:" + messages);
                String messagesJson = serializeMessages(allMessages);  // Using allMessages instead of quorumMessages
                System.out.println("Serialized messages:" + messagesJson);
               
                Message collectedMessage = new Message(messagesJson, CMD_COLLECTED);
               
                for (AuthenticatedPerfectLinks link : links) {
                    link.alp2pSend(link.getDestinationEntity(), collectedMessage);
                    System.out.println("----------------- Sent COLLECTED message to member: " + link.getDestinationEntity() + " -----------------");
                }
               
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
        System.out.println("Already collected, ignoring COLLECTED message");
        return; 
    }
    System.out.println("----------------- Processing COLLECTED message -----------------");
    try {
        String strPayload = message.getPayload();
        System.out.println("Received payload: " + strPayload);
        
        Map<String, Message> collectedMessages = new HashMap<>();
        
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
                
                collectedMessages.put(leader, leaderMessage);
                
                System.out.println("Extracted leader message with payload: " + leaderPayload);
            }
        }
        
        Map<String, Integer> stateOccurrences = new HashMap<>();
        int nonNullCount = 0;
        
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
        
        String adoptedValue = null;
        
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
            
            collected = true;
            
            Message writeMessage = new Message(adoptedValue, CMD_WRITE);
            
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
        return false; 
    }
   
    System.out.println("------------- Receiving WRITE ---------------");
   
    String messageId = message.getMessageID(); 
    protocolMessages.put(messageId, message);
   
    Map<String, Object> quorumResult = checkQuorum(protocolMessages);
   
    if ((Boolean)quorumResult.get("quorumFound")) {
        String quorumPayload = (String)quorumResult.get("payload");
        List<String> quorumProcesses = (List<String>)quorumResult.get("processes");
       
        System.out.println("Quorum established by processes: " + quorumProcesses);
       
        Message acceptMessage = new Message(quorumPayload, "CMD_ACCEPT");

        this.accepted = true;

        protocolMessages = new Hashtable<>();
                
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
    
    Map<String, Integer> payloadOccurrences = new HashMap<>();
    Map<String, List<String>> payloadToProcesses = new HashMap<>();
    
    System.out.println("Total messages in dictionary: " + messageDict.size());
    
    Enumeration<String> keys = messageDict.keys();
    while (keys.hasMoreElements()) {
        String processId = keys.nextElement();
        Message message = messageDict.get(processId);
        
        if (message != null) {
            String payload = message.getPayload();
            if (payload != null) {
                System.out.println("Process " + processId + " has payload: [" + payload + "]");
                
                payloadOccurrences.put(payload, payloadOccurrences.getOrDefault(payload, 0) + 1);
                
                List<String> processes = payloadToProcesses.getOrDefault(payload, new ArrayList<>());
                processes.add(processId);
                payloadToProcesses.put(payload, processes);
            }
        }
    }
    
    int quorumSize = (N + 1) / 2;
    System.out.println("Required quorum size: " + quorumSize);
    System.out.println("Payload occurrences: " + payloadOccurrences);
    
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
        
        if (json.startsWith("{") && json.endsWith("}")) {
            String content = json.substring(1, json.length() - 1);
            
            String[] entries = content.split(",(?=\")");
            
            for (String entry : entries) {
                int idStart = entry.indexOf("\"") + 1;
                int idEnd = entry.indexOf("\"", idStart);
                
                if (idStart > 0 && idEnd > idStart) {
                    String processId = entry.substring(idStart, idEnd);
                    
                    if (entry.contains(":null")) {
                        result.put(processId, null);
                    } else {
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