package com.example;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

/**
 * Manages cryptographic keys for secure communication
 */
public class KeyManager {
    private static final String KEYS_FILE = "shared/keys.json";
    private Map<String, PublicKey> publicKeys = new HashMap<>();
    private Map<String, PrivateKey> privateKeys = new HashMap<>();
    
    /**
     * Constructor for KeyManager.
     * 
     * @param entityName The name of the entity (e.g., "leader", "member1")
     * @throws Exception If loading keys fails
     */
    public KeyManager(String entityName) throws Exception {
        loadKeys(entityName);
    }
    
    /**
     * Loads cryptographic keys from the paths specified in keys.json file.
     * 
     * @param entityName The name of the entity to load keys for
     * @throws Exception If loading fails
     */
    private void loadKeys(String entityName) throws Exception {
        try {
            // Make sure the keys file exists
            File keysFile = new File(KEYS_FILE);
            if (!keysFile.exists()) {
                throw new IOException("Keys file not found: " + KEYS_FILE);
            }
            
            // Read the keys.json file
            String content = new String(Files.readAllBytes(Paths.get(KEYS_FILE)));
            JSONObject json = new JSONObject(content);
            
            if (!json.has("keys")) {
                throw new Exception("No 'keys' object found in keys.json");
            }
            
            JSONObject keysJson = json.getJSONObject("keys");
            
            // Get the directory where keys.json is located to use for relative paths
            String baseDir = new File(KEYS_FILE).getParent();
            if (baseDir == null) {
                baseDir = ".";
            }
            
            // Load keys for all entities
            for (String entity : keysJson.keySet()) {
                JSONObject entityKeys = keysJson.getJSONObject(entity);
                
                if (!entityKeys.has("public") || !entityKeys.has("private")) {
                    throw new Exception("Invalid key format for " + entity + ": missing public or private key paths");
                }
                
                String publicKeyPath = entityKeys.getString("public");
                String privateKeyPath = entityKeys.getString("private");
                
                // Resolve paths relative to the keys.json location
                String fullPublicKeyPath = Paths.get(baseDir, publicKeyPath).toString();
                String fullPrivateKeyPath = Paths.get(baseDir, privateKeyPath).toString();
                
                // Always load public keys for all entities
                PublicKey publicKey = loadPublicKeyFromFile(fullPublicKeyPath);
                publicKeys.put(entity, publicKey);
                
                // Only load our private key
                if (entity.equals(entityName)) {
                    PrivateKey privateKey = loadPrivateKeyFromFile(fullPrivateKeyPath);
                    privateKeys.put(entity, privateKey);
                }
            }
            
            Logger.log(Logger.LEADER_ERRORS, "Loaded keys for " + entityName + 
                    " and public keys for " + publicKeys.size() + " entities");
            
        } catch (Exception e) {
            Logger.log(Logger.LEADER_ERRORS, "Error loading keys: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Loads a public key from a file.
     * 
     * @param keyFilePath The path to the public key file
     * @return The public key
     * @throws Exception If loading or decoding fails
     */
    private PublicKey loadPublicKeyFromFile(String keyFilePath) throws Exception {
        File keyFile = new File(keyFilePath);
        if (!keyFile.exists()) {
            throw new IOException("Public key file not found: " + keyFilePath);
        }
        
        String keyContent = new String(Files.readAllBytes(keyFile.toPath())).trim();
        String base64Content;
        
        // Handle PEM format
        if (keyContent.contains("-----BEGIN PUBLIC KEY-----")) {
            base64Content = extractBase64Content(keyContent, 
                    "-----BEGIN PUBLIC KEY-----", 
                    "-----END PUBLIC KEY-----");
        } else if (keyContent.contains("-----BEGIN RSA PUBLIC KEY-----")) {
            base64Content = extractBase64Content(keyContent,
                    "-----BEGIN RSA PUBLIC KEY-----",
                    "-----END RSA PUBLIC KEY-----");
            Logger.log(Logger.LEADER_ERRORS, "Warning: RSA PUBLIC KEY format detected. " +
                    "This is an older format and might cause issues.");
        } else {
            // Assume it's just base64 encoded
            base64Content = keyContent;
        }
        
        try {
            byte[] decodedKey = Base64.getDecoder().decode(base64Content);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decodedKey);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(spec);
        } catch (IllegalArgumentException e) {
            // Log the error for debugging
            Logger.log(Logger.LEADER_ERRORS, "Error decoding public key from " + keyFilePath + 
                    ": " + e.getMessage() + ". First 20 chars of processed content: " + 
                    (base64Content.length() > 20 ? base64Content.substring(0, 20) + "..." : base64Content));
            throw e;
        }
    }
    
    /**
     * Loads a private key from a file.
     * 
     * @param keyFilePath The path to the private key file
     * @return The private key
     * @throws Exception If loading or decoding fails
     */
    private PrivateKey loadPrivateKeyFromFile(String keyFilePath) throws Exception {
        File keyFile = new File(keyFilePath);
        if (!keyFile.exists()) {
            throw new IOException("Private key file not found: " + keyFilePath);
        }
        
        String keyContent = new String(Files.readAllBytes(keyFile.toPath())).trim();
        String base64Content;
        
        // Handle different PEM formats
        if (keyContent.contains("-----BEGIN PRIVATE KEY-----")) {
            // PKCS#8 format
            base64Content = extractBase64Content(keyContent, 
                    "-----BEGIN PRIVATE KEY-----", 
                    "-----END PRIVATE KEY-----");
        } else if (keyContent.contains("-----BEGIN RSA PRIVATE KEY-----")) {
            // PKCS#1 format (older RSA-specific format)
            base64Content = extractBase64Content(keyContent,
                    "-----BEGIN RSA PRIVATE KEY-----",
                    "-----END RSA PRIVATE KEY-----");
            
            // Note: PKCS#1 keys would need to be converted to PKCS#8 for Java
            // For simplicity, we'll assume keys are in the right format for now
            Logger.log(Logger.LEADER_ERRORS, "Warning: RSA PRIVATE KEY format detected. " +
                    "Converting to PKCS#8 format might be required.");
        } else {
            // Assume it's just base64 encoded
            base64Content = keyContent;
        }
        
        try {
            byte[] decodedKey = Base64.getDecoder().decode(base64Content);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decodedKey);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(spec);
        } catch (IllegalArgumentException e) {
            // Log the error for debugging
            Logger.log(Logger.LEADER_ERRORS, "Error decoding private key from " + keyFilePath + 
                    ": " + e.getMessage() + ". First 20 chars of processed content: " + 
                    (base64Content.length() > 20 ? base64Content.substring(0, 20) + "..." : base64Content));
            throw e;
        }
    }
    
    /**
     * Helper method to extract Base64 content from PEM formatted keys.
     * 
     * @param pemContent The full PEM content
     * @param beginMarker The begin marker (e.g., "-----BEGIN PRIVATE KEY-----")
     * @param endMarker The end marker (e.g., "-----END PRIVATE KEY-----")
     * @return The Base64 encoded content
     */
    private String extractBase64Content(String pemContent, String beginMarker, String endMarker) {
        int beginIndex = pemContent.indexOf(beginMarker) + beginMarker.length();
        int endIndex = pemContent.indexOf(endMarker);
        
        if (beginIndex == -1 || endIndex == -1 || beginIndex >= endIndex) {
            // If markers aren't found or are in wrong order, return original content
            return pemContent;
        }
        
        // Extract content between markers and remove all whitespace
        return pemContent.substring(beginIndex, endIndex).replaceAll("\\s", "");
    }
    
    /**
     * Gets the public key for an entity.
     * 
     * @param entityName The name of the entity
     * @return The public key, or null if not found
     */
    public PublicKey getPublicKey(String entityName) {
        return publicKeys.get(entityName);
    }
    
    /**
     * Gets the private key for the current entity.
     * 
     * @param entityName The name of the entity
     * @return The private key, or null if not found
     */
    public PrivateKey getPrivateKey(String entityName) {
        return privateKeys.get(entityName);
    }
}