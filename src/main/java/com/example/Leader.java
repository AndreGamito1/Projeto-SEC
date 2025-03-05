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
import org.json.JSONObject;

public class Leader {
    private String name = "leader";
    private int port;
    private static final String RESOURCES_FILE = "shared/resources.json";
    private static final String[] MEMBERS = {"member1", "member2", "member3", "member4", "clientLibrary"};
    private static final int BASE_PORT = 5000; // Leader will be at 5000, members at 5001-5004, clientLibrary at 5005
    
    private Map<String, AuthenticatedPerfectLinks> memberLinks = new HashMap<>();
    private Map<String, Integer> memberPorts = new HashMap<>();
    
    // Change handler type to accept AuthenticatedMessage
    private Map<String, BiConsumer<String, AuthenticatedMessage>> messageHandlers = new ConcurrentHashMap<>();
    
    // Add EpochConsensus instance
    private EpochConsensus epochConsensus;
    
    /**
     * Constructor for Leader.
     * 
     * @param port The port number for the leader
     * @throws Exception If initialization fails
     */
    public Leader(int port) throws Exception {
        this.port = port;     
        // Initialize member ports
        for (int i = 0; i < MEMBERS.length; i++) {
            memberPorts.put(MEMBERS[i], BASE_PORT + i + 1);
        }
        
        // Generate config file with ports
        generateConfig();
        
        // Setup perfect links to all members
        setupMemberLinks();
        
        // Initialize EpochConsensus
        this.epochConsensus = new EpochConsensus(this);
    }
    
    /**
     * Generates a configuration file with ports.
     * 
     * @throws Exception If generation fails
     */
    private void generateConfig() throws Exception {
        // Ensure the directory exists
        File directory = new File("shared");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        
        // Create a new JSON object for resources
        JSONObject json = new JSONObject();
        
        // Add leader info
        JSONObject leaderJson = new JSONObject();
        leaderJson.put("leaderPort", String.valueOf(port));
        json.put(name, leaderJson);
        
        Logger.log(Logger.LEADER_ERRORS, "Generated leader config");
        
        // Add member info
        for (int i = 0; i < MEMBERS.length; i++) {
            String memberName = MEMBERS[i];
            int memberPort = memberPorts.get(memberName);
            
            JSONObject memberJson = new JSONObject();
            memberJson.put("memberPort", String.valueOf(memberPort));
            json.put(memberName, memberJson);
            
            Logger.log(Logger.LEADER_ERRORS, "Generated config for " + memberName);
        }
        
        // Write the JSON to the resources file
        try (FileWriter file = new FileWriter(RESOURCES_FILE)) {
            file.write(json.toString(2));
        }
        
        Logger.log(Logger.LEADER_ERRORS, "Updated resources.json with all member ports");   
    }
    
    /**
     * Sets up perfect links to all members.
     * 
     * @throws Exception If setup fails
     */
    private void setupMemberLinks() throws Exception {
        // Read the resources file to get member ports
        String content = new String(Files.readAllBytes(Paths.get(RESOURCES_FILE)));
        JSONObject json = new JSONObject(content);
        
        // Setup links to all members
        for (String memberName : MEMBERS) {
            if (!json.has(memberName)) {
                throw new Exception("Member '" + memberName + "' not found in resources.json");
            }
            
            JSONObject memberJson = json.getJSONObject(memberName);
            int memberPort = Integer.parseInt(memberJson.getString("memberPort"));
            String memberIP = "127.0.0.1"; // Assuming members are on localhost
            
            // Calculate leader port for this connection (memberPort + 1000)
            int leaderPortForMember = memberPort + 1000;
            
            // Create perfect link to member using leader port
            AuthenticatedPerfectLinks link = new AuthenticatedPerfectLinks(memberIP, memberPort, leaderPortForMember);
            memberLinks.put(memberName, link);

            Logger.log(Logger.LEADER_ERRORS, "Established link with " + memberName + " at " + memberIP + ":" + memberPort +
                               " using leader port " + leaderPortForMember);
        }
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
        
        Logger.log(Logger.LEADER_ERRORS, "Sent message to " + memberName + ": payload=\"" + payload + "\", command=\"" + command + "\"");
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
        
        Logger.log(Logger.LEADER_ERRORS, "Broadcasted message to all members: payload=\"" + payload + "\", command=\"" + command + "\"");
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
     * Starts the leader service and begins receiving commands from clientLibrary.
     */
    public void start() {
        Logger.log(Logger.LEADER_ERRORS, "Initializing...");
        
        try {
            // Start listening for messages
            Logger.log(Logger.LEADER_ERRORS, "Listening for messages on port " + port);
            
            // Print member connections with their corresponding leader ports
            for (String memberName : memberLinks.keySet()) {
                int memberPort = memberPorts.get(memberName);
                int leaderPortForMember = memberPort + 1000;
                Logger.log(Logger.LEADER_ERRORS, "Connection to " + memberName + ": member port " + memberPort + 
                        ", leader port " + leaderPortForMember);
            }
            
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
        
        Logger.log(Logger.LEADER_ERRORS, "Processing message from " + memberName + ": " + command + " with payload: " + payload);
        
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
                boolean success = epochConsensus.appendToBlockchain(payload);
                if (success) {
                    Logger.log(Logger.LEADER_ERRORS, "Successfully appended to blockchain: " + payload);
                } else {
                    Logger.log(Logger.LEADER_ERRORS, "Failed to append to blockchain: " + payload);
                }
                break;
                
            case "GET_BLOCKCHAIN":
                Logger.log(Logger.LEADER_ERRORS, "Executing get blockchain command");
                List<String> blockchain = epochConsensus.getBlockchain();
                String blockchainStr = String.join(", ", blockchain);
                Logger.log(Logger.LEADER_ERRORS, "Current blockchain: " + blockchainStr);
                // Send response to the client
                sendToMember("clientLibrary", blockchainStr, "BLOCKCHAIN_RESULT");
                break;
                
            default:
                Logger.log(Logger.LEADER_ERRORS, "Unknown command: " + command);
                break;
        }
    }
    
    /**
     * Gets the EpochConsensus instance.
     * 
     * @return The EpochConsensus instance
     */
    public EpochConsensus getEpochConsensus() {
        return epochConsensus;
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
        
        if (args.length > 0 && args[0].equalsIgnoreCase("test")) {
            // Only run the communication test
            Logger.log(Logger.LEADER_ERRORS, "Running in test mode");
            leader.testCommunication();
            
            // Test blockchain append
            Logger.log(Logger.LEADER_ERRORS, "Testing blockchain append");
            leader.getEpochConsensus().appendToBlockchain("Initial block");
            leader.getEpochConsensus().appendToBlockchain("Second block");
            List<String> blockchain = leader.getEpochConsensus().getBlockchain();
            Logger.log(Logger.LEADER_ERRORS, "Blockchain: " + String.join(", ", blockchain));
        } else {
            // Run the full leader service
            leader.start();
        }
    }
}