package com.example;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import javax.crypto.SecretKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

public class Leader {
    private String name = "leader";
    private int port;
    private PrivateKey privateKey;
    private PublicKey publicKey;
    private static final String RESOURCES_FILE = "shared/resources.json";
    private static final String[] MEMBERS = {"member1", "member2", "member3", "member4", "clientLibrary"};
    private static final int BASE_PORT = 5000; // Leader will be at 5000, members at 5001-5004, clientLibrary at 5005
    
    private Map<String, AuthenticatedPerfectLinks> memberLinks = new HashMap<>();
    private Map<String, SecretKey> memberSessionKeys = new HashMap<>();
    private Map<String, Integer> memberPorts = new HashMap<>();
    
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
        
        // Generate keys for leader and all members
        generateAllKeys();
        
        // Setup authenticated perfect links to all members
        setupMemberLinks();
    }
    
    /**
     * Generates key pairs for the leader and all members, and populates resources.json.
     * 
     * @throws Exception If key generation or JSON update fails
     */
    private void generateAllKeys() throws Exception {
        // Ensure the directory exists
        File directory = new File("shared");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        
        // Create a new JSON object for resources
        JSONObject json = new JSONObject();
        
        // Generate and add leader keys
        KeyPair leaderKeyPair = generateKeyPair();
        this.privateKey = leaderKeyPair.getPrivate();
        this.publicKey = leaderKeyPair.getPublic();
        
        JSONObject leaderJson = new JSONObject();
        leaderJson.put("leaderPort", String.valueOf(port));
        leaderJson.put("pubKey", Base64.getEncoder().encodeToString(publicKey.getEncoded()));
        leaderJson.put("privateKey", Base64.getEncoder().encodeToString(privateKey.getEncoded()));
        
        json.put(name, leaderJson);
        
        Logger.log(Logger.LEADER_ERRORS, "Generated leader key pair");
        
        // Generate and add member keys
        for (int i = 0; i < MEMBERS.length; i++) {
            String memberName = MEMBERS[i];
            int memberPort = memberPorts.get(memberName);
            
            KeyPair memberKeyPair = generateKeyPair();
            PublicKey memberPublicKey = memberKeyPair.getPublic();
            PrivateKey memberPrivateKey = memberKeyPair.getPrivate();
            
            // Generate a session key for this member
            SecretKey sessionKey = CryptoUtils.generateAESKey();
            memberSessionKeys.put(memberName, sessionKey);
            
            // Encrypt the session key with the member's public key
            String encryptedSessionKey = CryptoUtils.encryptAESKeyWithRSA(memberPublicKey, sessionKey);
            
            JSONObject memberJson = new JSONObject();
            memberJson.put("pubKey", Base64.getEncoder().encodeToString(memberPublicKey.getEncoded()));
            memberJson.put("privateKey", Base64.getEncoder().encodeToString(memberPrivateKey.getEncoded()));
            memberJson.put("memberPort", String.valueOf(memberPort));
            memberJson.put("encryptedSessionKey", encryptedSessionKey);
            json.put(memberName, memberJson);
            
            Logger.log(Logger.LEADER_ERRORS, "Generated key pair for " + memberName);
        }
        
        // Write the JSON to the resources file
        try (FileWriter file = new FileWriter(RESOURCES_FILE)) {
            file.write(json.toString(2));
        }
        
        Logger.log(Logger.LEADER_ERRORS, "Updated resources.json with all keys and member ports");   
    }
    
    /**
     * Generates a new RSA key pair using CryptoUtils.
     * 
     * @return A new KeyPair
     * @throws Exception If key generation fails
     */
    private KeyPair generateKeyPair() throws Exception {
        return CryptoUtils.generateRSAKeyPair();
    }
    
    /**
     * Sets up authenticated perfect links to all members.
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
            
            // Create authenticated perfect link to member using leader port
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
            
            // Start the command processing thread
            Thread commandThread = new Thread(this::receiveCommands);
            commandThread.setDaemon(true);
            commandThread.start();
            
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
                        AuthenticatedMessage authMessage = messages.get(i);
                        processCommand(authMessage);
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
     * Processes commands received from clientLibrary.
     * 
     * @param authMessage The authenticated message containing the command
     * @throws Exception If processing fails
     */
    private void processCommand(AuthenticatedMessage authMessage) throws Exception {
        String command = authMessage.getCommand();
        String payload = authMessage.getPayload();
        
        Logger.log(Logger.LEADER_ERRORS, "Received command: " + command + " with payload: " + payload);
        
        // Parse the number of times to execute (if provided)
        int times = 1; // Default value
        try {
            times = Integer.parseInt(payload);
        } catch (NumberFormatException e) {
            Logger.log(Logger.LEADER_ERRORS, "Invalid number format in payload, defaulting to 1");
        }
        
        // Process different commands
        switch (command) {
            case "TEST":
                Logger.log(Logger.LEADER_ERRORS, "Executing test command " + times + " time(s)");
                for (int i = 0; i < times; i++) {
                    testCommunication();
                    // Add a small delay between tests if running multiple times
                    if (i < times - 1) {
                        Thread.sleep(1000);
                    }
                }
                break;
                
            // Add more commands as needed
            case "BROADCAST":
                Logger.log(Logger.LEADER_ERRORS, "Executing broadcast command");
                broadcastToMembers("Broadcast from leader", "LEADER_BROADCAST");
                break;
                
            default:
                Logger.log(Logger.LEADER_ERRORS, "Unknown command: " + command);
                break;
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
     * Main method to start a leader instance.
     * 
     * @param args Command line arguments (none required or "test" to only run the test)
     * @throws Exception If initialization fails
     */
    public static void main(String[] args) throws Exception {
        Leader leader = new Leader(BASE_PORT);
        Logger.initFromArgs("--log=2,3, 0"); 

        if (args.length > 0 && args[0].equalsIgnoreCase("test")) {
            // Only run the communication test
            Logger.log(Logger.LEADER_ERRORS, "Running in test mode");
            leader.testCommunication();
        } else {
            // Run the full leader service
            leader.start();
        }
    }
}