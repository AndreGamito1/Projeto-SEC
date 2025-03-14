package com.example;

import java.util.ArrayList;
import java.util.List;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

interface MessageCallback {
    void onMessageReceived(AuthenticatedMessage authMessage);
}

public class AuthenticatedPerfectLinks implements MessageCallback {
    private List<AuthenticatedMessage> received;
    private StubbornLinks stubbornLink;
    private String destinationEntity; 
    private SecretKey aesKey; 
    private boolean usingAesKey = false; 
    
    // Constants for key exchange protocol
    private static final String CMD_KEY_EXCHANGE = "KEY_EXCHANGE";
    private static final String CMD_KEY_ACK = "KEY_ACK";
    private static final String CMD_KEY_OK = "KEY_OK";
    
    /**
     * Constructor for AuthenticatedPerfectLinks.
     * 
     * @param destIP The destination IP address
     * @param destPort The destination port
     * @param hostPort The host port
     * @param destEntity The name of the destination entity (e.g., "member1", "leader")
     */
    public AuthenticatedPerfectLinks(String destIP, int destPort, int hostPort, String destEntity) {
        try {
            this.destinationEntity = destEntity;
            this.stubbornLink = new StubbornLinks(destIP, destPort, hostPort, this);
            this.received = new ArrayList<>();
            Logger.log(Logger.AUTH_LINKS, "AuthenticatedPerfectLinks initialized for: " + destEntity);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Clears all received messages from the buffer.
     */
    public void clearReceivedMessages() {
        synchronized (received) {
            received.clear();
        }
    }

    /**
     * Constructor without destEntity.
     */
    public AuthenticatedPerfectLinks(String destIP, int destPort, int hostPort) {
        this(destIP, destPort, hostPort, "unknown");
    }
   
    /**
     * Sends an authenticated message to the destination.
     * 
     * @param dest The destination name (for logging)
     * @param message The message to send
     */
    public void alp2pSend(String dest, Message message) {
        // Check if this is a key exchange message
        if (message.getCommand().equals(CMD_KEY_EXCHANGE) || 
            message.getCommand().equals(CMD_KEY_ACK) ||
            message.getCommand().equals(CMD_KEY_OK)) {
            String authString = authenticate(message);
            AuthenticatedMessage authMessage = new AuthenticatedMessage(message, authString);
            stubbornLink.sp2pSend(authMessage);
            Logger.log(Logger.AUTH_LINKS, "Key exchange message sent to " + dest + ": " + message.getCommand());
        } else if (usingAesKey) {
            try {
                // Encrypt the payload with AES
                String encryptedPayload = encryptWithAes(message.getPayload(), aesKey);
                Message encryptedMessage = new Message(encryptedPayload, message.getCommand());
                
                // Create auth string for the encrypted message
                String authString = authenticate(encryptedMessage);
                AuthenticatedMessage authMessage = new AuthenticatedMessage(encryptedMessage, authString);
                
                stubbornLink.sp2pSend(authMessage);
                Logger.log(Logger.AUTH_LINKS, "Encrypted message sent to " + dest + ": " + message.getCommand());
            } catch (Exception e) {
                Logger.log(Logger.AUTH_LINKS, "Error encrypting message: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            // Use default authentication
            System.out.println("Sending message to " + dest + ": " + message.getPayload());
            String authString = authenticate(message);
            AuthenticatedMessage authMessage = new AuthenticatedMessage(message, authString);
            stubbornLink.sp2pSend(authMessage);
            Logger.log(Logger.AUTH_LINKS, "Message sent to " + dest + ": " + message.getPayload());
        }
    }
   
    /**
     * Receives and verifies an authenticated message.
     * 
     * @param authMessage The authenticated message
     */
    public void alp2pReceive(AuthenticatedMessage authMessage) {
        if (verifyauth(authMessage)) {
            Logger.log(Logger.AUTH_LINKS, "Message verified: " + authMessage.getMessageID());
            
            // Handle key exchange protocol messages
            String command = authMessage.getCommand();
            if (command.equals(CMD_KEY_EXCHANGE) || command.equals(CMD_KEY_ACK) || command.equals(CMD_KEY_OK)) {
                received.add(authMessage);
                Logger.log(Logger.AUTH_LINKS, "Received key exchange message: " + command);
            } else if (usingAesKey) {
                try {
                    // Decrypt the payload
                    String decryptedPayload = decryptWithAes(authMessage.getPayload(), aesKey);
                    
                    Message decryptedMessage = new Message(decryptedPayload, authMessage.getCommand());
                    AuthenticatedMessage newAuthMessage = new AuthenticatedMessage(
                        decryptedMessage, authMessage.getAuthString(), authMessage.getMessageID());
                    
                    received.add(newAuthMessage);
                    Logger.log(Logger.AUTH_LINKS, "Received and decrypted authenticated message: " + decryptedPayload);
                } catch (Exception e) {
                    Logger.log(Logger.AUTH_LINKS, "Error decrypting message: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                received.add(authMessage);
                Logger.log(Logger.AUTH_LINKS, "Received authenticated message: " + authMessage.getPayload());
            }
        }
    }
    
    /**
     * Authenticates a message by creating a hash of its content.
     * 
     * @param message The message to authenticate
     * @return The authentication string
     */
    public String authenticate(Message message) {
        try {
            String content = message.getPayload();
           
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }
   
    /**
     * Verifies the authentication of a message.
     * 
     * @param authMessage The authenticated message to verify
     * @return true if the message is authentic and not a duplicate
     */
    public boolean verifyauth(AuthenticatedMessage authMessage) {
        for (AuthenticatedMessage receivedMessage : received) {
            if (receivedMessage.getMessageID().equals(authMessage.getMessageID())) {
                return false;
            }
        }
        
        String command = authMessage.getCommand();
        if (command.equals(CMD_KEY_EXCHANGE) || command.equals(CMD_KEY_ACK) || command.equals(CMD_KEY_OK)) {
            String expectedAuthString = authenticate(authMessage);
            return authMessage.getAuthString().equals(expectedAuthString);
        } else if (usingAesKey) {
            String expectedAuthString = authenticate(authMessage);
            return authMessage.getAuthString().equals(expectedAuthString);
        } else {
            String expectedAuthString = authenticate(authMessage);
            return authMessage.getAuthString().equals(expectedAuthString);
        }
    }
    
    /**
     * Changes the key used for encryption/decryption.
     * 
     * @param newKey The new AES key to use
     */
    public void changeKey(SecretKey newKey) {
        this.aesKey = newKey;
        this.usingAesKey = (newKey != null);
        Logger.log(Logger.AUTH_LINKS, "Changed encryption key for " + destinationEntity + 
                  (usingAesKey ? " to AES" : " to default"));
    }
    
    /**
     * Encrypts a string using AES.
     * 
     * @param data The data to encrypt
     * @param key The AES key
     * @return The encrypted data as a Base64 string
     * @throws Exception If encryption fails
     */
    private String encryptWithAes(String data, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encryptedBytes = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }
    
    /**
     * Decrypts a Base64 string using AES.
     * 
     * @param encryptedData The data to decrypt (Base64 encoded)
     * @param key The AES key
     * @return The decrypted data
     * @throws Exception If decryption fails
     */
    private String decryptWithAes(String encryptedData, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decodedBytes = Base64.getDecoder().decode(encryptedData);
        byte[] decryptedBytes = cipher.doFinal(decodedBytes);
        return new String(decryptedBytes);
    }
    
    /**
     * Encrypts data with an RSA public key.
     * 
     * @param data The data to encrypt
     * @param publicKey The RSA public key
     * @return The encrypted data as a Base64 string
     * @throws Exception If encryption fails
     */
    public static String encryptWithRsa(String data, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encryptedBytes = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }
    
    /**
     * Decrypts data with an RSA private key.
     * 
     * @param encryptedData The data to decrypt (Base64 encoded)
     * @param privateKey The RSA private key
     * @return The decrypted data
     * @throws Exception If decryption fails
     */
    public static String decryptWithRsa(String encryptedData, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decodedBytes = Base64.getDecoder().decode(encryptedData);
        byte[] decryptedBytes = cipher.doFinal(decodedBytes);
        return new String(decryptedBytes);
    }
    
    /**
     * Generates a new AES key.
     * 
     * @return A new AES key
     * @throws Exception If key generation fails
     */
    public static SecretKey generateAesKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256); // 256-bit AES key
        return keyGen.generateKey();
    }
    
    /**
     * Converts an AES key to a Base64 string.
     * 
     * @param key The AES key
     * @return The Base64 encoded key
     */
    public static String aesKeyToString(SecretKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }
    
    /**
     * Converts a Base64 string to an AES key.
     * 
     * @param keyStr The Base64 encoded key
     * @return The AES key
     */
    public static SecretKey stringToAesKey(String keyStr) {
        byte[] decodedKey = Base64.getDecoder().decode(keyStr);
        return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
    }
    
    /**
     * Gets the size of the received message queue.
     * 
     * @return The number of received messages
     */
    public int getReceivedSize() {
        return received.size();
    }
    
    /**
     * Gets the list of received messages.
     * 
     * @return The list of authenticated messages
     */
    public List<AuthenticatedMessage> getReceivedMessages() {
        return received;
    }
    
    /**
     * Called when a message is received from the stubborn link.
     * 
     * @param authMessage The authenticated message
     */
    @Override
    public void onMessageReceived(AuthenticatedMessage authMessage) {
        alp2pReceive(authMessage);
    }
    
    /**
     * Gets the destination entity name.
     * 
     * @return The destination entity name
     */
    public String getDestinationEntity() {
        return destinationEntity;
    }
}