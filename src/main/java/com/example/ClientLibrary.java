package com.example;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;
import org.json.JSONObject;

/**
 * Client library for interacting with the Byzantine consensus blockchain
 */
public class ClientLibrary {
    private String name = "clientLibrary";
    private int port;
    private int leaderPort;
    private AuthenticatedPerfectLinks leaderLink;
    
    /**
     * Constructor for ClientLibrary.
     * 
     * @throws Exception If initialization fails
     */
    public ClientLibrary() throws Exception {
        // Load configuration from resources.json
        loadConfig();
        
        // Create a link to the leader
        String leaderIP = "127.0.0.1";
        System.out.println("Initiating link to leader at " + leaderIP + ":" + leaderPort + " from port " + port);
        leaderLink = new AuthenticatedPerfectLinks(leaderIP, leaderPort, port);
        
        Logger.log(Logger.CLIENT_LIBRARY, "Initialized client library on port " + port);
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
            throw new Exception("ClientLibrary not found in resources.json");
        }
        
        JSONObject clientJson = json.getJSONObject(name);
        this.port = Integer.parseInt(clientJson.getString("memberPort"));
        this.leaderPort = this.port + 1000;
        
        // Leader port is at BASE_PORT (5000)
        
    }
    
    /**
     * Appends a string to the blockchain.
     * 
     * @param data The string to append
     * @return true if the request was sent, false otherwise
     * @throws Exception If sending fails
     */
    public boolean appendToBlockchain(String data) throws Exception {
        sendToLeader(data, "APPEND_BLOCKCHAIN");
        Logger.log(Logger.CLIENT_LIBRARY, "Sent append request: " + data);
        return true;
    }
    
    /**
     * Gets the current blockchain.
     * 
     * @return The blockchain as a formatted string
     * @throws Exception If getting fails
     */
    public String getBlockchain() throws Exception {
        sendToLeader("", "GET_BLOCKCHAIN");
        Logger.log(Logger.CLIENT_LIBRARY, "Sent get blockchain request");
        
        // Wait for response (in a real implementation, we'd use a CompletableFuture)
        Thread.sleep(1000);
        
        // Get the response
        List<AuthenticatedMessage> messages = leaderLink.getReceivedMessages();
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            if (message.getCommand().equals("BLOCKCHAIN_RESULT")) {
                return message.getPayload();
            }
        }
        
        return "No blockchain data received";
    }
    
    /**
     * Sends a test message to the leader.
     * 
     * @param times Number of times to run the test
     * @throws Exception If sending fails
     */
    public void sendTestMessage(int times) throws Exception {
        sendToLeader(String.valueOf(times), "TEST");
        Logger.log(Logger.CLIENT_LIBRARY, "Sent test message for " + times + " run(s)");
    }
    
    /**
     * Sends a message to the leader.
     * 
     * @param payload The message payload
     * @param command The command to execute
     * @throws Exception If sending fails
     */
    private void sendToLeader(String payload, String command) throws Exception {
        Message message = new Message(payload, command);
        leaderLink.alp2pSend("leader", message);
    }
    
    /**
     * Displays a menu and processes user input.
     */
    public void runMenu() {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;
        
        while (running) {
            System.out.println("\n=== Blockchain Client Menu ===");
            System.out.println("1. Append to blockchain");
            System.out.println("2. Get blockchain");
            System.out.println("3. Send test message");
            System.out.println("0. Exit");
            System.out.print("Enter your choice: ");
            
            String input = scanner.nextLine().trim();
            
            try {
                switch (input) {
                    case "1":
                        System.out.print("Enter data to append: ");
                        String data = scanner.nextLine();
                        if (appendToBlockchain(data)) {
                            System.out.println("Append request sent successfully");
                        }
                        break;
                        
                    case "2":
                        String blockchain = getBlockchain();
                        System.out.println("\n--- Current Blockchain ---");
                        System.out.println(blockchain);
                        break;
                        
                    case "3":
                        System.out.print("Enter number of test runs: ");
                        int times = Integer.parseInt(scanner.nextLine());
                        sendTestMessage(times);
                        System.out.println("Test message sent");
                        break;
                        
                    case "0":
                        running = false;
                        System.out.println("Exiting...");
                        break;
                        
                    default:
                        System.out.println("Invalid option, please try again");
                        break;
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
        
        scanner.close();
    }
    
    /**
     * Main method that runs the interactive client.
     * 
     * @param args Command line arguments (none required)
     * @throws Exception If client initialization fails
     */
    public static void main(String[] args) throws Exception {
        Logger.initFromArgs("--log=clientLibrary");
        
        System.out.println("Initializing blockchain client...");
        ClientLibrary client = new ClientLibrary();
        
        // Run the interactive menu
        client.runMenu();
    }
}