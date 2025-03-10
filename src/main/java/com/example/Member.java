package com.example;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.json.JSONObject;

/**
 * Implements a member node in the Byzantine consensus system
 */
public class Member {
    protected String name;
    protected int port;
    protected int leaderPort;
    protected AuthenticatedPerfectLinks leaderLink;
    protected List<String> blockchain = new ArrayList<>();
    protected int currentEpoch = 0;
    protected Map<Integer, EpochState> epochs = new HashMap<>();
    
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
    private static final String CMD_READ = "EPOCH_READ";
    private static final String CMD_READ_REPLY = "EPOCH_READ_REPLY";
    
    // Constants for key exchange protocol
    private static final String CMD_KEY_EXCHANGE = "KEY_EXCHANGE";
    private static final String CMD_KEY_ACK = "KEY_ACK";
    private static final String CMD_KEY_OK = "KEY_OK";
    
    /**
     * Class to track the state of an epoch
     */
    private class EpochState {
        int epoch;
        String proposedValue;
        boolean decided = false;
        boolean aborted = false;
        
        EpochState(int epoch) {
            this.epoch = epoch;
        }
    }
    
    /**
     * Constructor for Member.
     * 
     * @param name The name of this member
     * @throws Exception If initialization fails
     */
    public Member(String name) throws Exception {
        this.name = name;
        
        // Initialize the KeyManager
        keyManager = new KeyManager(name);
        
        // Load configuration from resources.json
        loadConfig();
        
        // Create a link to the leader
        String leaderIP = "127.0.0.1";
        System.out.println("Initiating link to leader at " + leaderIP + ":" + leaderPort + " from port " + port);
        leaderLink = new AuthenticatedPerfectLinks(leaderIP, leaderPort, port, "leader");
        
        // Initialize connections map with an empty key for the leader
        connections.put("leader", "");
        
        Logger.log(Logger.MEMBER, "Initialized member: " + name + " on port " + port);
    }
    
    /**
     * Loads configuration from resources.json.
     * 
     * @throws Exception If loading fails
     */
    private void loadConfig() throws Exception {
        String content = new String(Files.readAllBytes(Paths.get("shared/resources.json")));
        JSONObject json = new JSONObject(content);
        
        if (!json.has(name)) {
            throw new Exception("Member '" + name + "' not found in resources.json");
        }
        
        JSONObject memberJson = json.getJSONObject(name);
        this.port = Integer.parseInt(memberJson.getString("memberPort"));
        this.leaderPort = this.port + 1000;
    }
    
    /**
     * Starts the member service and begins listening for messages.
     */
    public void start() {
        System.out.println("Starting member service on port " + port);
        Logger.log(Logger.MEMBER, "Starting member service on port " + port);
        
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
                System.out.println("Current epoch: " + currentEpoch);
                System.out.println("Blockchain length: " + blockchain.size());
                System.out.println("Blockchain content: " + String.join(" -> ", blockchain));
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
            
            Logger.log(Logger.MEMBER, "Processing message: " + command + " with payload length: " + payload.length());
            
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
                    handlePropose(payload);
                    break;
                    
                case CMD_ABORT:
                    handleAbort(payload);
                    break;
                    
                case CMD_DECIDE:
                    handleDecide(payload);
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
        int epoch = Integer.parseInt(payload);
        System.out.println("Received READ request for epoch " + epoch);
        System.out.println("Current blockchain: " + String.join(", ", blockchain));

        // Get the most recent value (valts, val)
        String lastValue = blockchain.isEmpty() ? "" : blockchain.get(blockchain.size() - 1);
        
        // Create the writeSet (all values in the blockchain)
        String writeSet = String.join("|", blockchain);
        
        // Send reply with format: "epoch|lastValue|writeSet"
        sendToLeader(epoch + "|" + lastValue + "|" + writeSet, CMD_READ_REPLY);
        
        Logger.log(Logger.MEMBER, "Sent READ_REPLY for epoch " + epoch + 
                   " with lastValue: " + lastValue + 
                   " and writeSet: " + writeSet);
    }
    
    /**
     * Handles a propose message from the leader.
     * 
     * @param payload The epoch and proposed value (format: "epoch|value")
     * @throws Exception If sending fails
     */
    private void handlePropose(String payload) throws Exception {
        String[] parts = payload.split("\\|");
        int epoch = Integer.parseInt(parts[0]);
        String value = parts[1];
        
        Logger.log(Logger.MEMBER, "Received PROPOSE for epoch " + epoch + " with value: " + value);
        
        // Create or get the epoch state
        EpochState state = epochs.computeIfAbsent(epoch, e -> new EpochState(e));
        
        // In a real Byzantine implementation, we would validate the proposal here
        // For simplicity, we'll just accept it
        state.proposedValue = value;
        
        // Send acknowledgment
        sendToLeader(epoch + "|ACK", CMD_ACK);
        
        Logger.log(Logger.MEMBER, "Sent ACK for epoch " + epoch);
    }
    
    /**
     * Handles an abort message from the leader.
     * 
     * @param payload The epoch and abort reason (format: "epoch|reason")
     */
    private void handleAbort(String payload) {
        try {
            String[] parts = payload.split("\\|");
            int epoch = Integer.parseInt(parts[0]);
            String reason = parts.length > 1 ? parts[1] : "Unknown reason";
            
            // Get or create the epoch state
            EpochState state = epochs.computeIfAbsent(epoch, e -> new EpochState(e));
            
            // Mark as aborted
            state.aborted = true;
            
            Logger.log(Logger.MEMBER, "Epoch " + epoch + " aborted: " + reason);
        } catch (Exception e) {
            Logger.log(Logger.MEMBER, "Error handling abort: " + e.getMessage());
        }
    }
    
    /**
     * Handles a decide message from the leader.
     * 
     * @param payload The epoch and decided value (format: "epoch|value")
     */
    private void handleDecide(String payload) {
        try {
            String[] parts = payload.split("\\|");
            int epoch = Integer.parseInt(parts[0]);
            String value = parts[1];
            
            // Get or create the epoch state
            EpochState state = epochs.computeIfAbsent(epoch, e -> new EpochState(e));
            
            // In a Byzantine implementation, we would verify the decision is valid
            // For simplicity, we'll just accept it if not already decided
            
            if (!state.decided && !state.aborted) {
                state.decided = true;
                
                // Append to our blockchain
                blockchain.add(value);
                
                // Update current epoch if higher
                currentEpoch = Math.max(currentEpoch, epoch + 1);
                
                Logger.log(Logger.MEMBER, "Decided value for epoch " + epoch + ": " + value);
                Logger.log(Logger.MEMBER, "Current blockchain: " + String.join(", ", blockchain));
                
                // Print immediate update when blockchain changes
                System.out.println("\n*** BLOCKCHAIN UPDATED ***");
                System.out.println("Added new block: " + value);
                System.out.println("Current blockchain: " + String.join(" -> ", blockchain));
                System.out.println("*********************\n");
            } else if (state.decided) {
                Logger.log(Logger.MEMBER, "Ignoring duplicate decision for epoch " + epoch);
            } else {
                Logger.log(Logger.MEMBER, "Ignoring decision for aborted epoch " + epoch);
            }
        } catch (Exception e) {
            Logger.log(Logger.MEMBER, "Error handling decide: " + e.getMessage());
        }
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
        member.blockchain.add("Genesis");
        System.out.println("Starting member: " + memberName);
        member.start();
    }
}