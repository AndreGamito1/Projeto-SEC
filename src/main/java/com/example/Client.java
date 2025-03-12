package com.example;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.json.JSONObject;

/**
 * Client class that uses the REST API to interact with the blockchain.
 * This is a modified version of the original Client class to work with the REST API.
 */
public class Client {
    private final String clientId;
    private final String baseUrl;
    private final HttpClient httpClient;
    private int clientPort;
    
    /**
     * Constructor for Client.
     * 
     * @param clientId A unique identifier for this client
     */
    public Client(String clientId) {
        this(clientId, "http://localhost:8081");
    }
    
    /**
     * Constructor for Client with specified API URL.
     * 
     * @param clientId A unique identifier for this client
     * @param baseUrl Base URL of the REST API
     */
    public Client(String clientId, String baseUrl) {
        this.clientId = clientId;
        
        // Ensure baseUrl doesn't end with a slash
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        
        // Create HTTP client with timeout
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        
        // Calculate a port based on client ID to maintain compatibility with original code
        this.clientPort = 10000 + Math.abs(clientId.hashCode() % 10000);
        
        System.out.println("Blockchain client initialized with ID: " + clientId);
        System.out.println("Connected to blockchain REST API at: " + baseUrl);
    }
    
    /**
     * Gets the client's unique identifier.
     * 
     * @return The client ID
     */
    public String getClientId() {
        return clientId;
    }
    
    /**
     * Gets the client's port (for compatibility with original code).
     * 
     * @return The port number
     */
    public int getPort() {
        return clientPort;
    }
    
    /**
     * Appends data to the blockchain with the client's ID prefix.
     * 
     * @param data The data to append
     * @return true if the append operation was successful
     * @throws Exception If the operation fails
     */
    public boolean appendToBlockchain(String data) throws Exception {
        // Format the data with client ID prefix
        String formattedData = String.format("[%s] %s", clientId, data);
        
        // Create the request body
        JSONObject requestBody = new JSONObject();
        requestBody.put("data", formattedData);
        
        try {
            // Create the HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/blockchain/append"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();
            
            // Send the request
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            // Check if the request was successful
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JSONObject jsonResponse = new JSONObject(response.body());
                return jsonResponse.optBoolean("success", false);
            } else {
                System.err.println("HTTP Error: " + response.statusCode() + " - " + response.body());
                return false;
            }
        } catch (Exception e) {
            System.err.println("Connection error: " + e.getMessage());
            // Provide a more user-friendly error message
            return false;
        }
    }
    
    /**
     * Retrieves the current blockchain state.
     * 
     * @return The blockchain as a formatted string
     * @throws Exception If the retrieval fails
     */
    public String getBlockchain() throws Exception {
        try {
            // Create the HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/blockchain/get"))
                    .GET()
                    .build();
            
            // Send the request
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            // Check if the request was successful
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JSONObject jsonResponse = new JSONObject(response.body());
                return jsonResponse.optString("blockchain", "No blockchain data received");
            } else {
                System.err.println("HTTP Error: " + response.statusCode() + " - " + response.body());
                return "Error getting blockchain data";
            }
        } catch (Exception e) {
            System.err.println("Connection error: " + e.getMessage());
            return "Error: Could not connect to blockchain service";
        }
    }
    
    /**
     * Sends a test message to the blockchain.
     * This method is kept for backward compatibility but is not supported by the REST API.
     * 
     * @param testRuns Number of test runs to perform
     * @throws Exception If the operation fails
     */
    public void runTest(int testRuns) throws Exception {
        System.out.println("Test operation is not supported by the REST API");
    }
    
    /**
     * Main method to initialize and run a client.
     * 
     * @param args Command line arguments - first argument should be the client ID
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Error: Client ID must be provided as an argument");
            System.exit(1);
        }
        
        String clientId = args[0];
        
        try {
            System.out.println("Initializing client: " + clientId);
            
            // Create a client
            Client client = new Client(clientId);
            
            // Run the client interface
            runClientInterface(client);
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Runs the interactive client interface.
     * 
     * @param client The client to use
     */
    private static void runClientInterface(Client client) {
        java.util.Scanner scanner = new java.util.Scanner(System.in);
        boolean running = true;
        
        while (running) {
            System.out.println("\n=== Blockchain Client: " + client.getClientId() + " ===");
            System.out.println("1. Append data to blockchain");
            System.out.println("2. View current blockchain");
            System.out.println("0. Exit");
            System.out.print("Enter choice: ");
            
            String input = scanner.nextLine().trim();
            
            try {
                switch (input) {
                    case "1":
                        System.out.print("Enter data to append: ");
                        String data = scanner.nextLine();
                        boolean success = client.appendToBlockchain(data);
                        if (success) {
                            System.out.println("Data successfully appended to blockchain.");
                        } else {
                            System.out.println("Failed to append data to blockchain.");
                        }
                        break;
                        
                    case "2":
                        System.out.println("Retrieving blockchain data...");
                        String blockchain = client.getBlockchain();
                        System.out.println("\n=== Current Blockchain State ===");
                        System.out.println(blockchain);
                        break;
                        
                    case "0":
                        running = false;
                        System.out.println("Exiting client...");
                        break;
                        
                    default:
                        System.out.println("Invalid option. Please try again.");
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }
}