package com.depchain.client;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Duration;
import java.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import com.depchain.utils.Encryption;
import com.depchain.utils.KeyManager;

/**
 * Client class that uses the REST API to interact with the blockchain.
 */
public class Client {
    private final String clientId;
    private final String baseUrl;
    private final HttpClient httpClient;
    private int clientPort;
    private PublicKey publicKey;
    private PrivateKey privateKey;

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
        
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        
        this.clientPort = 10000 + Math.abs(clientId.hashCode() % 10000);
        loadClientKeys();

        System.out.println("Blockchain client initialized with ID: " + clientId);
        System.out.println("Connected to blockchain REST API at: " + baseUrl);
    }
 
    public void loadClientKeys() {
        try {
            // Load the JSON file
            String jsonFilePath = "src/main/resources/accounts.json"; // Updated to match the correct file name
            String jsonContent = new String(Files.readAllBytes(Paths.get(jsonFilePath)));
            JSONObject json = new JSONObject(jsonContent);
            JSONArray clients = json.getJSONArray("clients");
    
            boolean clientFound = false;
    
            for (int i = 0; i < clients.length(); i++) {
                JSONObject client = clients.getJSONObject(i);
    
                if (client.getString("name").equals(this.clientId)) {
                    clientFound = true;
    
                    String publicKeyPath = client.optString("publicKeyPath", null);
                    String privateKeyPath = client.optString("privateKeyPath", null);
    
                    if (publicKeyPath == null || privateKeyPath == null) {
                        throw new IllegalArgumentException("Missing key paths for client: " + this.clientId);
                    }
    
                    File publicKeyFile = new File(publicKeyPath);
                    File privateKeyFile = new File(privateKeyPath);
    
                    if (!publicKeyFile.exists() || !privateKeyFile.exists()) {
                        generateKeyPair(publicKeyPath, privateKeyPath);
                    }
    
                    this.publicKey = KeyManager.loadPublicKeyFromFile(publicKeyPath);
                    this.privateKey = KeyManager.loadPrivateKeyFromFile(privateKeyPath);
                    break;
                }
            }
    
            if (!clientFound) {
                throw new IllegalArgumentException("Client ID not found in accounts.json: " + this.clientId);
            }
    
        } catch (IOException e) {
            System.err.println("Error reading JSON file: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Error loading client keys: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void generateKeyPair(String publicKeyPath, String privateKeyPath) {
        try {
            // Generate RSA key pair
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair pair = keyGen.generateKeyPair();
            PrivateKey privateKey = pair.getPrivate();
            PublicKey publicKey = pair.getPublic();
            
            // Encode keys
            String publicKeyEncoded = Base64.getEncoder().encodeToString(publicKey.getEncoded());
            String privateKeyEncoded = Base64.getEncoder().encodeToString(privateKey.getEncoded());
            
            // Save keys to files
            saveKeyToFile(publicKeyEncoded, publicKeyPath);
            saveKeyToFile(privateKeyEncoded, privateKeyPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveKeyToFile(String key, String filePath) throws IOException {
        File file = new File(filePath);
        file.getParentFile().mkdirs(); // Ensure directory exists
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(key);
        }
    }

    
/**
     * Sends the sender name, receiver name, and amount as JSON to the server.
     * 
     *
     * @param receiverName The name of the recipient.
     * @param amountString The amount to transfer (as a string).
     * @return true if the server responds with a 2xx status code, false otherwise.
     */
    public boolean appendToBlockchain(String receiverName, String amountString) {

        if (receiverName == null || receiverName.trim().isEmpty()) {
            System.err.println("Error: Receiver name is required.");
            return false;
        }
        if (amountString == null) {
            System.err.println("Error: Amount string is required.");
            return false;
        }

        double amountValue;
        try {
            amountValue = Double.parseDouble(amountString);
            if (amountValue <= 0) {
                 System.err.println("Error: Amount must be positive.");
                 return false;
            }
        } catch (NumberFormatException e) {
            System.err.println("Error: Invalid format for amount: '" + amountString + "'");
            return false;
        }

        String signature = null;
        try {
            signature = Encryption.encryptWithPrivateKey(receiverName, this.privateKey);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 2. Create JSON Payload
        JSONObject requestBody = new JSONObject();
        requestBody.put("senderName", this.clientId);      // Sender is the client ID 
        requestBody.put("receiverName", receiverName);     // Receiver from parameter
        requestBody.put("amount", amountValue);            // Amount from parameter (parsed)
        requestBody.put("signature", signature);           // Signature of the transaction

        String jsonPayload = requestBody.toString();
        System.out.println("Sending request: " + jsonPayload); // Log what's being sent

        // 3. Build HTTP Request
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + "/blockchain/append"))
                    .header("Content-Type", "application/json") 
                    .POST(BodyPublishers.ofString(jsonPayload)) 
                    .build();

            // 4. Send Request and Get Response
            HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

            // 5. Check Response Status
            int statusCode = response.statusCode();
            System.out.println("Received Status Code: " + statusCode);
            System.out.println("Received Response Body: " + response.body()); // Log response body

            if (statusCode >= 200 && statusCode < 300) {
                System.out.println("Request successful (Status code " + statusCode + ").");
                return true;
            } else {
                System.err.println("Request failed (Status code " + statusCode + ").");
                return false;
            }

        } catch (IOException | InterruptedException e) {
            // Handle network errors or if the sending thread is interrupted
            System.err.println("Error sending request: " + e.getMessage());
             if (e instanceof InterruptedException) {
                 Thread.currentThread().interrupt(); 
             }
            return false;
        } catch (IllegalArgumentException e) {
            // Handle errors like invalid URI format from baseUrl
            System.err.println("Error building request (check URL?): " + e.getMessage());
            return false;
        }
         catch (Exception e) {
            // Catch-all for any other unexpected errors during the process
            System.err.println("An unexpected error occurred: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Retrieves the current blockchain state.
     * 
     * @return The blockchain as a formatted string
     * @throws Exception If the retrieval fails
     */
    public String checkBalance() throws Exception {
        String signature = null;
        try {
            signature = Encryption.encryptWithPrivateKey(clientId, this.privateKey);
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: Could not generate signature";
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/blockchain/get"))
                    .header("Signature", signature) // Adding signature to headers
                    .header("ClientName", clientId)  // Add client ID in header
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JSONObject jsonResponse = new JSONObject(response.body());
                String balance = jsonResponse.optString("balance", "No balance data received");
                return "Balance for " + clientId + ": " + balance;
            } else {
                System.err.println("HTTP Error: " + response.statusCode() + " - " + response.body());
                return "Error getting balance data: " + response.body();
            }
        } catch (Exception e) {
            System.err.println("Connection error: " + e.getMessage());
            return "Error: Could not connect to blockchain service";
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
            System.out.println("2. Check my balance");
            System.out.println("0. Exit");
            System.out.print("Enter choice: ");
            
            String input = scanner.nextLine().trim();
            
            try {
                switch (input) {
                    case "1":
                        System.out.print("Receiver address: ");
                        String receiver = scanner.nextLine();
                        System.out.print("Amount: ");
                        String amount = scanner.nextLine();

                        boolean success = client.appendToBlockchain(receiver, amount);
                        if (success) {
                            System.out.println("Append request successfully sent.");
                        } else {
                            System.out.println("Failed to send append request.");
                        }
                        break;
                        
                    case "2":
                        System.out.println("Retrieving balance data...");
                        String balanceResult = client.checkBalance();
                        System.out.println(balanceResult);
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
                e.printStackTrace();
            }
        }
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
            
            Client client = new Client(clientId);
            
            runClientInterface(client);
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    //--- Getters and Setters ---

    /**
     * Gets the client's unique identifier.
     * 
     * @return The client ID
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Gets the client's port.
     * 
     * @return The port number
     */
    public int getPort() {
        return clientPort;
    }
    
}