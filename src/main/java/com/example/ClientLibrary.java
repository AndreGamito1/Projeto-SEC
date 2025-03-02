package com.example;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Scanner;

import javax.crypto.SecretKey;
import org.json.JSONObject;

public class ClientLibrary {
    private static final String RESOURCES_FILE = "shared/resources.json";
    private static final String LEADER_HOST = "localhost";
    private static final String CLIENT_NAME = "clientLibrary";
    
    private int leaderPort;
    private int clientPort;
    private PrivateKey privateKey;
    private PublicKey publicKey;
    private SecretKey sessionKey;
    
    public static void main(String[] args) {
        try {
            ClientLibrary library = new ClientLibrary();
            library.loadConfiguration();
            library.startTerminalInterface();
        } catch (Exception e) {
            System.err.println("Error in ClientLibrary: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Loads the client configuration from resources.json
     */
    private void loadConfiguration() throws Exception {
        // Ensure the resources file exists
        File resourcesFile = new File(RESOURCES_FILE);
        if (!resourcesFile.exists()) {
            throw new Exception("Resources file not found: " + RESOURCES_FILE);
        }
        
        // Read the JSON file
        String content = new String(Files.readAllBytes(Paths.get(RESOURCES_FILE)));
        JSONObject json = new JSONObject(content);
        
        // Load client details
        if (!json.has(CLIENT_NAME)) {
            throw new Exception("Client configuration not found in resources.json");
        }
        
        JSONObject clientJson = json.getJSONObject(CLIENT_NAME);
        this.clientPort = Integer.parseInt(clientJson.getString("port"));
        
        // Load public key
        String encodedPubKey = clientJson.getString("pubKey");
        byte[] publicKeyBytes = Base64.getDecoder().decode(encodedPubKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
        this.publicKey = keyFactory.generatePublic(publicKeySpec);
        
        // Load private key
        String encodedPrivateKey = clientJson.getString("privateKey");
        byte[] privateKeyBytes = Base64.getDecoder().decode(encodedPrivateKey);
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        this.privateKey = keyFactory.generatePrivate(privateKeySpec);
        
        // Decrypt session key if it exists
        if (clientJson.has("encryptedSessionKey")) {
            String encryptedSessionKey = clientJson.getString("encryptedSessionKey");
            this.sessionKey = CryptoUtils.decryptAESKeyWithRSA(privateKey, encryptedSessionKey);
            Logger.log(Logger.CLIENT_LIBRARY, "Decrypted session key from resources.json");
        }
        
        // Load leader port
        if (!json.has("leader")) {
            throw new Exception("Leader configuration not found in resources.json");
        }
        
        JSONObject leaderJson = json.getJSONObject("leader");
        this.leaderPort = Integer.parseInt(leaderJson.getString("port"));
        Logger.log(Logger.CLIENT_LIBRARY, "Loaded configuration from resources.json");
        Logger.log(Logger.CLIENT_LIBRARY, "Client port: " + clientPort);
        Logger.log(Logger.CLIENT_LIBRARY, "Leader port: " + leaderPort);
    }
    
    /**
     * Starts the client terminal interface
     */
    public void startTerminalInterface() throws Exception {
        Scanner scanner = new Scanner(System.in);
        AuthenticatedPerfectLinks alp2p = new AuthenticatedPerfectLinks(LEADER_HOST, leaderPort, clientPort);
        
        Logger.log(Logger.CLIENT_LIBRARY, "Client Library started. Connected to Leader on port " + leaderPort);
        Logger.log(Logger.CLIENT_LIBRARY, "Listening on port " + clientPort);
        
        boolean running = true;
        while (running) {
            System.out.println("\nSelect an option:");
            System.out.println("1. Buy (sends <\"buy\", \"2\">)");
            System.out.println("2. Sell (sends <\"sell\", \"1\">)");
            System.out.println("3. Custom message");
            System.out.println("4. Check received messages");
            System.out.println("5. Exit");
            System.out.print("> ");
            
            String input = scanner.nextLine().trim();
            
            switch (input) {
                case "1":
                    sendMessage(alp2p, "buy", "2");
                    break;
                case "2":
                    sendMessage(alp2p, "sell", "1");
                    break;
                case "3":
                    System.out.print("Enter message type: ");
                    String type = scanner.nextLine().trim();
                    System.out.print("Enter message content: ");
                    String content = scanner.nextLine().trim();
                    sendMessage(alp2p, type, content);
                    break;
                case "4":
                    System.out.println("Received message count: " + alp2p.getReceivedSize());
                    break;
                case "5":
                    running = false;
                    System.out.println("Exiting...");
                    break;
                default:
                    System.out.println("Invalid option. Please try again.");
                    break;
            }
        }
        
        scanner.close();
    }
    
    /**
     * Sends a message to the leader
     */
    private void sendMessage(AuthenticatedPerfectLinks alp2p, String command, String payload) throws Exception {
        Message message = new Message(payload, command);
        alp2p.alp2pSend(CLIENT_NAME, message);
        Logger.log(Logger.CLIENT_LIBRARY, "Sent message: payload=\"" + payload + "\", command=\"" + command + "\", ID=" + message.getMessageID());
    }
}