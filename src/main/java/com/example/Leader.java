package com.example;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import javax.crypto.SecretKey;

import org.json.JSONArray;
import org.json.JSONObject;


public class Leader extends Member {
    private String name = "leader";
    private int port;
    private static final String RESOURCES_FILE = "shared/resources.json";
    private static final String[] MEMBERS = {"member1", "member2", "member3", "member4"};
    private static final int BASE_PORT = 5000; // Leader will be at 5000, members at 5001-5004, clientLibrary at 5005
    
    private Map<String, AuthenticatedPerfectLinks> memberLinks = new HashMap<>();
    private Map<String, Integer> memberPorts = new HashMap<>();
    private Map<String, SecretKey> memberKeys = new HashMap<>(); // Store AES keys for each member
    
    // Constants for key exchange protocol
    private static final String CMD_KEY_EXCHANGE = "KEY_EXCHANGE";
    private static final String CMD_KEY_ACK = "KEY_ACK";
    private static final String CMD_KEY_OK = "KEY_OK";
    
    // Change handler type to accept AuthenticatedMessage
    private Map<String, BiConsumer<String, AuthenticatedMessage>> messageHandlers = new ConcurrentHashMap<>();
    
    // Add EpochConsensus instance
    private ByzantineEpochConsensus epochConsensus;
    
    /**
     * Constructor for Leader.
     * 
     * @param port The port number for the leader
     * @throws Exception If initialization fails
     */
    public Leader(int port) throws Exception {
        // Call the Member constructor with "leader" as the name
        super("leader");
        this.port = port;    
        // Initialize member ports
        for (int i = 0; i < MEMBERS.length; i++) {
            memberPorts.put(MEMBERS[i], BASE_PORT + i + 1);
            
            // Initialize connections map entry for this member with an empty key (to be filled later)
            connections.put(MEMBERS[i], "");
        }
        setupMemberLinks();

        // Register message handlers for key exchange protocol
        registerKeyExchangeHandlers();
        
        // Initialize EpochConsensus
        // Only proceed if memberLinks has entries
        if (memberLinks.isEmpty()) {
            Logger.log(Logger.LEADER_ERRORS, "Warning: memberLinks is empty when initializing EpochConsensus");
        }
        AuthenticatedPerfectLinks[] memberLinksArray = memberLinks.values().toArray(new AuthenticatedPerfectLinks[0]);
        this.epochConsensus = new ByzantineEpochConsensus(this.name, this.name, 4, 1, 0, memberLinksArray);
        
        // Initialize the KeyManager (inherited from Member class)
        Logger.log(Logger.LEADER_ERRORS, "Initializing KeyManager for leader");
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
    /**
     * Registers message handlers for the key exchange protocol.
     */
    private void registerKeyExchangeHandlers() {
        // Register handler for key exchange acknowledgment
        registerMessageHandler(CMD_KEY_ACK, (memberName, message) -> {
            try {
                handleKeyAcknowledgment(memberName, message);
            } catch (Exception e) {
                Logger.log(Logger.LEADER_ERRORS, "Error handling key acknowledgment from " + memberName + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Initiates the key exchange protocol with a specific member.
     * 
     * @param memberName The name of the member
     * @throws Exception If key exchange fails
     */
    public void initiateKeyExchange(String memberName) throws Exception {
        Logger.log(Logger.LEADER_ERRORS, "Initiating key exchange with " + memberName);
        
        try {
            // Generate a new AES key for this member
            SecretKey aesKey = AuthenticatedPerfectLinks.generateAesKey();
            memberKeys.put(memberName, aesKey);
            
            // Convert the key to a string for transmission
            String keyString = AuthenticatedPerfectLinks.aesKeyToString(aesKey);
            
            // Get the member's public key using KeyManager
            KeyManager keyManager = new KeyManager("leader");
            java.security.PublicKey publicKey = keyManager.getPublicKey(memberName);
            
            if (publicKey == null) {
                throw new Exception("No public key found for " + memberName);
            }
            
            // Encrypt the AES key with the member's public key
            String encryptedKey = AuthenticatedPerfectLinks.encryptWithRsa(keyString, publicKey);
            
            // Send the encrypted key to the member
            sendToMember(memberName, encryptedKey, CMD_KEY_EXCHANGE);
            
            Logger.log(Logger.LEADER_ERRORS, "Sent RSA-encrypted key exchange to " + memberName);
        } catch (Exception e) {
            Logger.log(Logger.LEADER_ERRORS, "Error initiating key exchange with " + memberName + ": " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Handles key acknowledgment from a member.
     * 
     * @param memberName The name of the member
     * @param message The key acknowledgment message
     * @throws Exception If handling fails
     */
    private void handleKeyAcknowledgment(String memberName, AuthenticatedMessage message) throws Exception {
        Logger.log(Logger.LEADER_ERRORS, "Received key acknowledgment from " + memberName);
        
        // Verify that we have a key for this member
        if (!memberKeys.containsKey(memberName)) {
            throw new Exception("No key found for " + memberName);
        }
        
        // Get the key for this member
        SecretKey aesKey = memberKeys.get(memberName);
        
        // Set the key for the perfect link
        AuthenticatedPerfectLinks link = memberLinks.get(memberName);
        link.changeKey(aesKey);
        
        // Send a final confirmation that we're now using the key
        sendToMember(memberName, "OK", CMD_KEY_OK);
        
        Logger.log(Logger.LEADER_ERRORS, "Completed key exchange with " + memberName + ", now using AES encryption");
        Logger.log(Logger.LEADER_ERRORS, "RSA was used for key exchange, AES will be used for ongoing communication");
    }
    
    /**
     * Initiates key exchange with all members.
     * 
     * @throws Exception If key exchange fails
     */
    public void initiateAllKeyExchanges() throws Exception {
        Logger.log(Logger.LEADER_ERRORS, "Starting key exchange with all members");
        
        for (String memberName : memberLinks.keySet()) {
            try {
                initiateKeyExchange(memberName);
                // Short delay to prevent overloading the network
                Thread.sleep(500);
            } catch (Exception e) {
                Logger.log(Logger.LEADER_ERRORS, "Failed to initiate key exchange with " + memberName + ": " + e.getMessage());
            }
        }
        
        Logger.log(Logger.LEADER_ERRORS, "All key exchanges initiated");
    }
    
    /**
     * Sends a message to a specific member.
     * 
     * @param memberName The name of the member
     * @param payload The message payload
     * @param command The command to execute
     * @throws Exception If sending fails
     */
    public void sendToMember(String memberName, String payload, String command) throws Exception {
        if (!memberLinks.containsKey(memberName)) {
            throw new Exception("No link established with member '" + memberName + "'");
        }
        
        Message message = new Message(payload, command);
        memberLinks.get(memberName).alp2pSend(memberName, message);
        
        Logger.log(Logger.LEADER_ERRORS, "Sent message to " + memberName + ": command=\"" + command + "\"");
    }
    
    /**
     * Broadcasts a message to all members.
     * 
     * @param payload The message payload
     * @param command The command to execute
     * @throws Exception If sending fails
     */
    public void broadcastToMembers(String payload, String command) throws Exception {
        for (String memberName : memberLinks.keySet()) {
            sendToMember(memberName, payload, command);
        }
        
        Logger.log(Logger.LEADER_ERRORS, "Broadcasted message to all members: command=\"" + command + "\"");
    }
    
    /**
     * Tests communication by sending a mock message to all members.
     * 
     * @throws Exception If sending fails
     */
    public void testCommunication() throws Exception {
        
        String command = "TEST_MESSAGE";
        Logger.log(Logger.LEADER_ERRORS, "Starting communication test");

        
        // Send the mock message to each member
        for (String memberName : memberLinks.keySet()) {
            try {
                String mockMessage = "Hello " + memberName + " I'm your leader";
                sendToMember(memberName, mockMessage, command);
                Logger.log(Logger.LEADER_ERRORS, "Test message sent to " + memberName);
            } catch (Exception e) {
                Logger.log(Logger.LEADER_ERRORS, "Failed to send test message to " + memberName + ": " + e.getMessage());
            }
        }
        
        // Wait a moment to allow messages to be processed
        Thread.sleep(2000);
        
        // Report on received messages
        Logger.log(Logger.LEADER_ERRORS, "-----------------TEST RECEIVED MESSAGES STATUS-----------------");
        for (String memberName : memberLinks.keySet()) {
            AuthenticatedPerfectLinks link = memberLinks.get(memberName);
            int receivedCount = link.getReceivedSize();
            Logger.log(Logger.LEADER_ERRORS, "Received messages for " + memberName + ": " + receivedCount);
        }
        Logger.log(Logger.LEADER_ERRORS, "Communication test complete");
    }
    
    /**
     * Override the Member's start method to provide Leader-specific behavior.
     */
    @Override
    public void start() {
        Logger.log(Logger.LEADER_ERRORS, "Initializing...");
        
        try {
            // Start listening for messages
            Logger.log(Logger.LEADER_ERRORS, "Listening for messages on port " + port);
            
            
            // Initiate key exchange with all members
            initiateAllKeyExchanges();
            
            // Start the command processing thread for client commands
            Thread commandThread = new Thread(this::receiveCommands);
            commandThread.setDaemon(true);
            commandThread.start();
            
            // Start threads to process messages from each member
            for (String memberName : memberLinks.keySet()) {
                if (!memberName.equals("clientLibrary")) {
                    Thread memberThread = new Thread(() -> receiveMemberMessages(memberName));
                    memberThread.setDaemon(true);
                    memberThread.start();
                    Logger.log(Logger.LEADER_ERRORS, "Started message processing thread for " + memberName);
                }
            }
            
            // Run the main leader loop
            while (true) {
                Thread.sleep(5000);
                Logger.log(Logger.LEADER_ERRORS, "Leader is running...");
                logReceivedMessagesStatus();
            }
            
        } catch (Exception e) {
            System.err.println("Error in " + name + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Continuously processes messages from a specific member.
     * 
     * @param memberName The name of the member
     */
    private void receiveMemberMessages(String memberName) {
        Logger.log(Logger.LEADER_ERRORS, "Starting to receive messages from " + memberName + "...");
        
        try {
            AuthenticatedPerfectLinks memberLink = memberLinks.get(memberName);
            if (memberLink == null) {
                throw new Exception("No link established with " + memberName);
            }
            
            int previousCount = 0;
            
            while (true) {
                List<AuthenticatedMessage> messages = memberLink.getReceivedMessages();
                int currentCount = messages.size();
                
                if (currentCount > previousCount) {
                    for (int i = previousCount; i < currentCount; i++) {
                        AuthenticatedMessage message = messages.get(i);
                        String command = message.getCommand();
                        
                        // Special debug for READ_REPLY messages
                        if (command.equals("EPOCH_READ_REPLY")) {
                            System.out.println("DEBUG: Found a READ_REPLY message from " + memberName);
                        }
                        
                        processMessage(memberName, message);
                    }
                    
                    previousCount = currentCount;
                }
                
                Thread.sleep(100);
            }
        } catch (Exception e) {
            Logger.log(Logger.LEADER_ERRORS, "Error processing messages from " + memberName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Process a message from a member.
     * 
     * @param memberName The name of the member
     * @param message The authenticated message
     * @throws Exception If processing fails
     */
    private void processMessage(String memberName, AuthenticatedMessage message) throws Exception {
        String command = message.getCommand();
        String payload = message.getPayload();
        
        Logger.log(Logger.LEADER_ERRORS, "Processing message from " + memberName + ": " + command);
        
        // Check if we have a registered handler for this command
        if (messageHandlers.containsKey(command)) {
            System.out.println("Routing to handler for " + command + " from " + memberName);
            messageHandlers.get(command).accept(memberName, message);
        } else {
            Logger.log(Logger.LEADER_ERRORS, "No handler for command: " + command + " from " + memberName);
        }
    }

    /**
     * Continuously checks for and processes commands from the clientLibrary.
     * This runs in a separate thread.
     */
    private void receiveCommands() {
        
        Logger.log(Logger.LEADER_ERRORS, "Starting to receive commands from clientLibrary...");
        
        try {
            // Get clientLibrary link
            System.out.println("Printing memberLinks keys");
            System.out.println(memberLinks.keySet());
            AuthenticatedPerfectLinks clientLink = memberLinks.get("clientLibrary");

            if (clientLink == null) {
                throw new Exception("No link established with clientLibrary");
            }
            
            // Previous received message count
            int previousCount = 0;
            
            // Continuously check for new messages
            while (true) {
                // Check if there are any new messages from clientLibrary
                List<AuthenticatedMessage> messages = clientLink.getReceivedMessages();
                int currentCount = messages.size();
                
                // If we have new messages
                if (currentCount > previousCount) {
                    // Process new messages (from previous count to current count)
                    for (int i = previousCount; i < currentCount; i++) {
                        AuthenticatedMessage message = messages.get(i);
                        processClientCommand(message);
                    }
                    
                    previousCount = currentCount;
                }
                
                // Short sleep to prevent CPU hogging
                Thread.sleep(100);
            }
        } catch (Exception e) {
            Logger.log(Logger.LEADER_ERRORS, "Error while receiving commands: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Logs the status of received messages from all members.
     */
    private void logReceivedMessagesStatus() {
        Logger.log(Logger.LEADER_ERRORS, "-----------------RECEIVED MESSAGES STATUS-----------------");
        for (String memberName : memberLinks.keySet()) {
            AuthenticatedPerfectLinks link = memberLinks.get(memberName);
            int receivedCount = link.getReceivedSize();
            Logger.log(Logger.LEADER_ERRORS, "Received messages for " + memberName + ": " + receivedCount);
            
            // Also log encryption status
            SecretKey key = memberKeys.getOrDefault(memberName, null);
            boolean usingEncryption = (key != null);
            Logger.log(Logger.LEADER_ERRORS, "Encryption status for " + memberName + ": " + 
                              (usingEncryption ? "Using AES encryption" : "Not encrypted"));
        }
    }
    
    /**
     * Registers a message handler for a specific command type.
     * 
     * @param command The command to handle
     * @param handler The handler function that takes (memberName, message)
     */
    public void registerMessageHandler(String command, BiConsumer<String, AuthenticatedMessage> handler) {
        messageHandlers.put(command, handler);
    }
    
    /**
     * Process a command from the client.
     * 
     * @param message The message containing the command
     * @throws Exception If processing fails
     */
    private void processClientCommand(AuthenticatedMessage message) throws Exception {
        String command = message.getCommand();
        String payload = message.getPayload();
        
        Logger.log(Logger.LEADER_ERRORS, "Received client command: " + command + " with payload: " + payload);
        
        // Process standard commands
        switch (command) {
            case "TEST":
                Logger.log(Logger.LEADER_ERRORS, "Executing test command");
                testCommunication();
                break;
                
            case "BROADCAST":
                Logger.log(Logger.LEADER_ERRORS, "Executing broadcast command");
                broadcastToMembers("Broadcast from leader", "LEADER_BROADCAST");
                break;
                
            case "APPEND_BLOCKCHAIN":
                Logger.log(Logger.LEADER_ERRORS, "Executing append to blockchain command");
                epochConsensus.propose(payload);
                break;
                
            case "GET_BLOCKCHAIN":
                Logger.log(Logger.LEADER_ERRORS, "Executing get blockchain command");
                break;
                
            case "INIT_KEY_EXCHANGE":
                Logger.log(Logger.LEADER_ERRORS, "Executing key exchange initialization");
                String targetMember = payload.trim();
                if (targetMember.equals("ALL")) {
                    initiateAllKeyExchanges();
                } else if (memberLinks.containsKey(targetMember)) {
                    initiateKeyExchange(targetMember);
                } else {
                    Logger.log(Logger.LEADER_ERRORS, "Unknown member for key exchange: " + targetMember);
                }
                break;
            case "CMD_STATE":
                Logger.log(Logger.LEADER_ERRORS, "Executing state command");
                epochConsensus.processState(message);
                break;
                
            default:
                Logger.log(Logger.LEADER_ERRORS, "Unknown command: " + command);
                break;
        }
    }


    /**
     * Main method to start a leader instance.
     * 
     * @param args Command line arguments (none required)
     * @throws Exception If initialization fails
     */
    public static void main(String[] args) throws Exception {
        Leader leader = new Leader(BASE_PORT);
        Logger.initFromArgs("--log=2,3,4,1"); 
        leader.start();
    }
}