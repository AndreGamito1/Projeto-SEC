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
import javax.crypto.SecretKey;
import org.json.JSONObject;

public class Member {
    private String name;
    private int port;
    private PrivateKey privateKey;
    private PublicKey publicKey;
    private SecretKey sessionKey;
    private static final String RESOURCES_FILE = "shared/resources.json";
    private AuthenticatedPerfectLinks leaderLink;
    
    /**
     * Constructor for Member.
     * 
     * @param name The name of the member (e.g., "member1")
     * @throws Exception If loading keys from JSON fails
     */
    public Member(String name) throws Exception {
        this.name = name;
        
        // Load member details from resources.json
        loadMemberDetails();
        
        System.out.println("[" + name + "] Initialized with port " + port);
    }
    
    /**
     * Loads member details (port, keys) from resources.json file.
     * 
     * @throws Exception If loading fails
     */
    private void loadMemberDetails() throws Exception {
        // Ensure the resources file exists
        File resourcesFile = new File(RESOURCES_FILE);
        if (!resourcesFile.exists()) {
            throw new Exception("Resources file not found: " + RESOURCES_FILE);
        }
        
        // Read the JSON file
        String content = new String(Files.readAllBytes(Paths.get(RESOURCES_FILE)));
        JSONObject json = new JSONObject(content);
        
        // Check if the member exists in the file
        if (!json.has(name)) {
            throw new Exception("Member '" + name + "' not found in resources.json");
        }
        
        JSONObject memberJson = json.getJSONObject(name);
        
        // Load member port - this is the port we listen on
        if (memberJson.has("memberPort")) {
            this.port = Integer.parseInt(memberJson.getString("memberPort"));
        } else if (memberJson.has("port")) {
            this.port = Integer.parseInt(memberJson.getString("port"));
        } else {
            throw new Exception("Neither 'memberPort' nor 'port' found for member: " + name);
        }
        
        // Load public key
        String encodedPubKey = memberJson.getString("pubKey");
        byte[] publicKeyBytes = Base64.getDecoder().decode(encodedPubKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
        this.publicKey = keyFactory.generatePublic(publicKeySpec);
        
        // Load private key
        String encodedPrivateKey = memberJson.getString("privateKey");
        byte[] privateKeyBytes = Base64.getDecoder().decode(encodedPrivateKey);
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        this.privateKey = keyFactory.generatePrivate(privateKeySpec);
        
        // Decrypt session key if it exists
        if (memberJson.has("encryptedSessionKey")) {
            String encryptedSessionKey = memberJson.getString("encryptedSessionKey");
            this.sessionKey = CryptoUtils.decryptAESKeyWithRSA(privateKey, encryptedSessionKey);
            System.out.println("[" + name + "] Decrypted session key from resources.json");
        }
        
        
        System.out.println("[" + name + "] Loaded keys and port from resources.json");
    }
    
    /**
     * Connects to the leader using AuthenticatedPerfectLinks.
     * 
     * @throws Exception If connection fails
     */
    private void connectToLeader() throws Exception {
        // Calculate leader port based on member port (always memberPort + 1000)
        int leaderPort = this.port + 1000;
        
        System.out.println("[" + name + "] Using calculated leader port: " + leaderPort);
        
        String leaderIP = "127.0.0.1"; // Assuming leader is on localhost
        
        // Create authenticated perfect link to leader using our outbound port
        System.out.println("Creating authenticated link to leader at " + leaderIP + ":" + leaderPort + 
                          " using outbound port " + this.port);
        this.leaderLink = new AuthenticatedPerfectLinks(leaderIP, leaderPort, this.port);
        
        System.out.println("[" + name + "] Connected to leader at " + leaderIP + ":" + leaderPort + 
                          " using outbound port " + this.port);
    }
    
    /**
     * Sends a message to the leader.
     * 
     * @param payload The message payload
     * @param command The command to execute
     * @throws Exception If sending fails
     */
    public void sendToLeader(String payload, String command) throws Exception {
        if (leaderLink == null) {
            throw new Exception("Not connected to leader");
        }
        
        Message message = new Message(payload, command);
        leaderLink.alp2pSend("leader", message);
        
        System.out.println("[" + name + "] Sent message to leader: payload=\"" + payload + "\", command=\"" + command + "\"");
    }
    
    /**
     * Starts the member service.
     */
    public void start() {
        
        try {
            // Connect to the leader
            connectToLeader();
            
            // Start listening for messages
            System.out.println("[" + name + "] Listening for messages on memberPort " + port);
            
            // The authenticated perfect links will handle message reception
            // and we'll print any received messages
            
            // Keep the program running
            while (true) {
                Thread.sleep(1000);
                
                // Check for delivered messages from the leaderLink
                if (leaderLink != null && leaderLink.getDeliveredSize() > 0) {
                    System.out.println("[" + name + "] Delivered message count: " + 
                                      leaderLink.getDeliveredSize());
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error in " + name + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Get the member's private key.
     * 
     * @return PrivateKey
     */
    public PrivateKey getPrivateKey() {
        return privateKey;
    }
    
    /**
     * Get the member's public key.
     * 
     * @return PublicKey
     */
    public PublicKey getPublicKey() {
        return publicKey;
    }
    
    /**
     * Main method to start a member instance.
     * 
     * @param args Command line arguments: <name> [outboundPort]
     * @throws Exception If initialization fails
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            System.out.println("Usage: java Member <name> [outboundPort]");
            return;
        }
        
        String name = args[0];
        Member member = new Member(name);
        
        
        member.start();
    }
}