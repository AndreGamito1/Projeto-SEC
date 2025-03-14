package com.example;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;

import org.json.JSONArray;
import org.json.JSONObject;
import com.example.EpochState;
/**
 * Implements a member node in the Byzantine consensus system
 */
public class Member {
    protected String name;
    protected int port;
    protected int leaderPort;
    protected Map<String, AuthenticatedPerfectLinks> memberLinks = new HashMap<>();
    protected AuthenticatedPerfectLinks leaderLink;
    protected EpochState currentEpoch;
    protected List<EpochState> writeSet;    
    private static final String RESOURCES_FILE = "shared/resources.json";
    private static final String[] MEMBERS = {"member1", "member2", "member3", "member4"};
    
    // Add connections attribute to store symmetric keys
    protected Map<String, String> connections = new HashMap<>();
    
    // Add KeyManager for cryptographic operations
    protected KeyManager keyManager;
    
    // Track the last processed message index
    private int lastProcessedIndex = 0;
    
    // Constants for commands used in messages
    private static final String CMD_PROPOSE = "EPOCH_PROPOSE";
    private static final String CMD_ABORT = "EPOCH_ABORT";
    private static final String CMD_DECIDE = "EPOCH_DECIDE";
    private static final String CMD_ACK = "EPOCH_ACK";
    
    private static final String CMD_READ_REPLY = "EPOCH_READ_REPLY";
    
    // Constants for key exchange protocol
    private static final String CMD_KEY_EXCHANGE = "KEY_EXCHANGE";
    private static final String CMD_KEY_ACK = "KEY_ACK";
    private static final String CMD_KEY_OK = "KEY_OK";
    private static final String CMD_COLLECTED = "CMD_COLLECTED";
    private static final String CMD_STATE = "CMD_STATE";
    private static final String CMD_READ = "CMD_READ";
    
    /**
     * Constructor for Member.
     * 
     * @param name The name of this member
     * @throws Exception If initialization fails
     */
    public Member(String name) throws Exception {
        this.name = name;
        this.currentEpoch = new EpochState(0, "0", new TimestampValue[0]);
        // Initialize the KeyManager
        keyManager = new KeyManager(name);

        if(!name.equals("leader")){
            setupMemberLinks();
        }


        
        // Initialize connections map with an empty key for the leader
        connections.put("leader", "");
        
        Logger.log(Logger.MEMBER, "Initialized member: " + name + " on port " + port);
    }

    /**
     * Sets up perfect links to all members.
     * 
     * @throws Exception If setup fails
     */
    protected void setupMemberLinks() throws Exception {
        // Read the resources file to get member connections
        String content = new String(Files.readAllBytes(Paths.get(RESOURCES_FILE)));
        JSONObject json = new JSONObject(content);
           
        JSONObject memberJson = json.getJSONObject(this.name);
        JSONArray connections = memberJson.getJSONArray("connections");
        String memberID = "0";
        if (!this.name.equals("leader")){
            memberID = this.name.replace("member", "");
        }
           
        for (int i = 0; i < connections.length(); i++) {
            String targetName = connections.getString(i);
            String targetID = targetName.equals("leader") ? "0" : targetName.replace("member", "");
               
            if (!memberID.equals(targetID)) { // Avoid self-links
                String targetIP = "127.0.0.1"; // Assuming localhost communication
                int portToTarget;
                int portFromTarget;
                
                // Special case for clientLibrary - use port 5005
                if (targetName.equals("clientLibrary")) {
                    portToTarget = 5005;
                    portFromTarget = 6005;
                } else {
                    // Normal case - use the standard port format
                    portToTarget = Integer.parseInt("70" + memberID + targetID);
                    portFromTarget = Integer.parseInt("70" + targetID + memberID);
                }
                   
                // Create and store the link
                System.out.println("Establishing link from " + this.name + " to " + targetName +
                        " at " + targetIP + ":" + portToTarget + ", back at " + targetIP + ":" + portFromTarget);
                AuthenticatedPerfectLinks link = new AuthenticatedPerfectLinks(targetIP, portToTarget, portFromTarget, targetName);
                if (targetName.equals("leader")) {
                    leaderLink = link; // Store link in leaderLink when connecting to leader
                } else {
                    System.out.println("ADDING LINK TO LINKS LIST: " + targetName);
                    memberLinks.put(targetName, link);
                }
                   
                Logger.log(Logger.LEADER_ERRORS, "Established link from " + this.name + " to " + targetName +
                        " at " + targetIP + ":" + portToTarget + ", back at " + targetIP + ":" + portFromTarget);
            }
        }
    }


    public String printWriteSet() {
        if (writeSet == null || writeSet.isEmpty()) {
            return "[]";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < writeSet.size(); i++) {
            sb.append(writeSet.get(i).toString());
            
            if (i < writeSet.size() - 1) {
                sb.append(" -> ");
            }
        }
        
        return sb.toString();
    }



    /**
     * Starts the member service and begins listening for messages.
     */
    public void start() {
        System.out.println("Starting member service on port " + port);
        Logger.log(Logger.MEMBER, "Starting member service on port " + port);
        System.out.println("Generating RSA key pair for member: " + name);
        
        
        try {
            // Start a separate thread to print blockchain status every 3 seconds
            Thread statusThread = new Thread(this::printBlockchainStatus);
            statusThread.setDaemon(true);
            statusThread.start();
            
            // Start the message processing loop
            while (true) {
                // Process any new messages from the leader
                processMessages();
                
                // Sleep to prevent CPU hogging
                Thread.sleep(100);
            }
        } catch (Exception e) {
            Logger.log(Logger.MEMBER, "Error in member service: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Prints the current blockchain status every 3 seconds.
     */
    private void printBlockchainStatus() {
        try {
            while (true) {
                // Print the current blockchain status
                System.out.println("\n========== " + name + " BLOCKCHAIN STATUS ==========");
                System.out.println("=================================================\n");
                
                // Wait for 3 seconds
                Thread.sleep(3000);
            }
        } catch (InterruptedException e) {
            System.err.println("Blockchain status thread interrupted: " + e.getMessage());
        }
    }
    
    /**
     * Processes incoming messages from the leader.
     * 
     * @throws Exception If processing fails
     */
    private void processMessages() throws Exception {
        List<AuthenticatedMessage> messages = leaderLink.getReceivedMessages();
        
        // Only process new messages we haven't seen yet
        for (int i = lastProcessedIndex; i < messages.size(); i++) {
            AuthenticatedMessage authMessage = messages.get(i);
            
            String command = authMessage.getCommand();
            String payload = authMessage.getPayload();
            
            Logger.log(Logger.MEMBER, "Processing message: " + command + " with payload: " + payload);
            
            switch (command) {
                case CMD_KEY_EXCHANGE:
                    handleKeyExchange(authMessage);
                    break;
                    
                case CMD_KEY_OK:
                    Logger.log(Logger.MEMBER, "Key exchange completed successfully");
                    break;
                    
                case CMD_READ:
                    handleReadRequest(payload);
                    break;
                    
                case CMD_PROPOSE:

                    break;
                    
                case CMD_ABORT:

                    break;
                    
                case CMD_DECIDE:

                    break;

                case CMD_COLLECTED:
                    System.out.println("Received COLLECTED message from leader");
                    System.out.println(payload);
                    break;

                case "TEST_MESSAGE":
                    // Just log the test message
                    Logger.log(Logger.MEMBER, "Received test message: " + payload);
                    // Send a reply
                    sendToLeader("Received: " + payload, "TEST_REPLY");
                    break;
                    
                case "LEADER_BROADCAST":
                    Logger.log(Logger.MEMBER, "Received broadcast: " + payload);
                    break;
                    
                default:
                    Logger.log(Logger.MEMBER, "Unknown command: " + command);
                    break;
            }
            
            // Update our last processed index after processing the message
            lastProcessedIndex = i + 1;
        }
    }
    
    /**
     * Handles a key exchange message from the leader.
     * 
     * @param authMessage The authenticated message
     * @throws Exception If handling fails
     */
    private void handleKeyExchange(AuthenticatedMessage authMessage) throws Exception {
        try {
            Logger.log(Logger.MEMBER, "Received KEY_EXCHANGE from leader");
            
            // Get our private key
            PrivateKey privateKey = keyManager.getPrivateKey(name);
            if (privateKey == null) {
                throw new Exception("No private key found for " + name);
            }
            
            // Decrypt the AES key using our private key
            String encryptedKey = authMessage.getPayload();
            String aesKeyStr = AuthenticatedPerfectLinks.decryptWithRsa(encryptedKey, privateKey);
            
            // Store the AES key in the connections map
            connections.put("leader", aesKeyStr);
            
            // Send acknowledgment to the leader
            sendToLeader("ACK", CMD_KEY_ACK);
            Logger.log(Logger.MEMBER, "Sent KEY_ACK to leader");
            
            // Convert string to AES key and change the key in the perfect link
            SecretKey aesKey = AuthenticatedPerfectLinks.stringToAesKey(aesKeyStr);
            leaderLink.changeKey(aesKey);
            
            Logger.log(Logger.MEMBER, "Changed to AES encryption for leader communications");
            
        } catch (Exception e) {
            Logger.log(Logger.MEMBER, "Error handling KEY_EXCHANGE: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Handles a read request from the leader.
     * 
     * @param payload The epoch number
     * @throws Exception If sending fails
     */
    private void handleReadRequest(String payload) throws Exception {
        System.out.println("Received READ request");
        System.out.println("Current blockchain: " + printWriteSet());
        long epoch = currentEpoch.getTimestamp();
    
        
        // Send reply with format: "epoch|lastValue|writeSet"
        sendToLeader(epoch + "|" + currentEpoch.toString() + "|" + printWriteSet(), CMD_STATE);
        
        Logger.log(Logger.MEMBER, "Sent STATE for epoch " + epoch + 
                   " with lastValue: " + currentEpoch.toString() + 
                   " and writeSet: " + printWriteSet());
    }
    
    /**
     * Sends a message to the leader.
     * 
     * @param payload The message payload
     * @param command The command to execute
     * @throws Exception If sending fails
     */
    protected void sendToLeader(String payload, String command) throws Exception {
        Message message = new Message(payload, command);
        leaderLink.alp2pSend("leader", message);
    }
    
    /**
     * Gets the connections map containing symmetric keys.
     * 
     * @return The connections map
     */
    public Map<String, String> getConnections() {
        return connections;
    }
    
    /**
     * Sets a symmetric key for a connection.
     * 
     * @param entityName The name of the entity (e.g., "leader" or another member)
     * @param symmetricKey The symmetric key to use
     */
    public void setConnectionKey(String entityName, String symmetricKey) {
        connections.put(entityName, symmetricKey);
    }
    
    /**
     * Gets the name of this member.
     * 
     * @return The member name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Main method to start a member instance.
     * 
     * @param args Command line arguments (member name required)
     * @throws Exception If initialization fails
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Member <memberName>");
            return;
        }
        
        String memberName = args[0];
        Logger.initFromArgs("--log=1,2,3,4");
        
        Member member = new Member(memberName);
        System.out.println("Starting member: " + memberName);
        member.start();
    }
}