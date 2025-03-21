package com.depchain.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;

import org.json.JSONObject;

import com.depchain.networking.AuthenticatedMessage;
import com.depchain.networking.Message;

/**
 * Manages cryptographic keys for secure communication
 */
public class KeyManager {
    private static final String KEYS_FILE = "src/main/resources/setup.json";
    private Map<String, PublicKey> publicKeys = new HashMap<>();        //Holds the RSA Public Key for each member connection
    private Map<String, PrivateKey> privateKeys = new HashMap<>();      //Holds the RSA Private Key for each member connection
    private Map<String, SecretKey> memberKeys = new HashMap<>();        //Holds the AES Key for each member connection 
    private String entityName;


    /**
     * Constructor for KeyManager.
     * 
     * @param entityName The name of the entity (e.g., "leader", "member1")
     * @throws Exception If loading keys fails
     */
    public KeyManager(String entityName) throws Exception {
        createKeyPair(entityName);
        waitForKeys();
        loadKeys(entityName);
    }

    /**
     * Creates a new RSA key pair and saves it to the specified paths.
     * Files will be saved in PEM format.
     * 
     * @param entityName The name of the entity to create keys for
     * @return true if successful, false otherwise
     */
    public boolean createKeyPair(String entityName) {
        String privateKeyPath = "src/main/resources/priv_keys/" + entityName + "_private.pem";
        String publicKeyPath = "src/main/resources/pub_keys/" + entityName + "_public.pem";

        try {
            // Create directories if they don't exist
            new File("src/main/resources/priv_keys").mkdirs();
            new File("src/main/resources/pub_keys").mkdirs();

            // Generate RSA key pair with explicit key size (2048 bits recommended)
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048); // Explicitly set key size to 2048 bits
            
            KeyPair pair = keyGen.generateKeyPair();
            PrivateKey privateKey = pair.getPrivate();
            PublicKey publicKey = pair.getPublic();

            // Log key details for debugging
            Logger.log(Logger.AUTH_LINKS, "Generated RSA key pair for " + entityName);
            Logger.log(Logger.AUTH_LINKS, "  Public key format: " + publicKey.getFormat());
            Logger.log(Logger.AUTH_LINKS, "  Public key algorithm: " + publicKey.getAlgorithm());
            Logger.log(Logger.AUTH_LINKS, "  Public key size: " + ((java.security.interfaces.RSAPublicKey)publicKey).getModulus().bitLength() + " bits");

            // Save keys to files
            savePrivateKeyToPem(privateKey, privateKeyPath);
            savePublicKeyToPem(publicKey, publicKeyPath);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    /**
     * Waits until all keys exist in setup.json before proceeding.
     *
     * @throws IOException If file operations fail
     * @throws InterruptedException If thread sleep is interrupted
     */
    private void waitForKeys() throws IOException, InterruptedException {
        File keysFile = new File(KEYS_FILE);
        if (!keysFile.exists()) {
            throw new IOException("Keys file not found: " + KEYS_FILE);
        }

        while (true) {
            System.out.println("Waiting for keys to be generated...");
            String content = new String(Files.readAllBytes(keysFile.toPath()));
            JSONObject json = new JSONObject(content);

            if (!json.has("setup")) {
                throw new IOException("No 'setup' object found in setup.json");
            }

            JSONObject setupJson = json.getJSONObject("setup");
            boolean allKeysExist = true;

            for (String entity : setupJson.keySet()) {
                if (entity.equals("clientLibrary") || entity.equals("leader")) {
                    continue; 
                }
                Object entityKeysObj = setupJson.get(entity);
                if (!(entityKeysObj instanceof JSONObject)) {
                    System.out.println("Invalid key format for " + entity + ": not a JSONObject");
                    allKeysExist = false;
                    break;
                }

                JSONObject entityKeys = (JSONObject) entityKeysObj;
              

                if (!entityKeys.has("public") || !entityKeys.has("private")) {
                    System.out.println("Key path not found for " + entity + " in the json file");
                    allKeysExist = false;
                    break;
                }

                String publicKeyPath = entityKeys.getString("public");
                String privateKeyPath = entityKeys.getString("private");

                if (!new File(publicKeyPath).exists() || !new File(privateKeyPath).exists()) {
                    System.out.println("Keys not found, checking again...");
                    allKeysExist = false;
                    break;
                }
            }

            if (allKeysExist) {
                break;
            }

            Thread.sleep(1000);
        }
    }

    /**
     * Saves a private key to a file in PEM format.
     * 
     * @param privateKey The private key to save
     * @param filePath The file path to save to
     * @throws IOException If file operations fail
     */
    private void savePrivateKeyToPem(PrivateKey privateKey, String filePath) throws IOException {
        byte[] encoded = privateKey.getEncoded();
        String base64Encoded = Base64.getEncoder().encodeToString(encoded);

        String pemContent = "-----BEGIN PRIVATE KEY-----\n" + 
                            formatPemContent(base64Encoded) + 
                            "\n-----END PRIVATE KEY-----";

        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(pemContent.getBytes());
        }
    }

    /**
     * Saves a public key to a file in PEM format.
     * 
     * @param publicKey The public key to save
     * @param filePath The file path to save to
     * @throws IOException If file operations fail
     */
    private void savePublicKeyToPem(PublicKey publicKey, String filePath) throws IOException {
        byte[] encoded = publicKey.getEncoded();
        String base64Encoded = Base64.getEncoder().encodeToString(encoded);

        String pemContent = "-----BEGIN PUBLIC KEY-----\n" + 
                            formatPemContent(base64Encoded) + 
                            "\n-----END PUBLIC KEY-----";

        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(pemContent.getBytes());
        }
    }

    /**
     * Formats base64 encoded content into lines of 64 characters for PEM format.
     * 
     * @param base64Content The base64 encoded content
     * @return Formatted content with line breaks
     */
    private String formatPemContent(String base64Content) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < base64Content.length(); i += 64) {
            result.append(base64Content, i, Math.min(i + 64, base64Content.length())).append("\n");
        }
        return result.toString().trim();
    }

    /**
     * Loads cryptographic keys from the paths specified in setup.json file.
     * 
     * @param entityName The name of the entity to load keys for
     * @throws Exception If loading fails
     */
    private void loadKeys(String entityName) throws Exception {
        String content = new String(Files.readAllBytes(Paths.get(KEYS_FILE)));
        JSONObject json = new JSONObject(content);

     
        if (!json.has("setup")) {
            throw new IOException("No 'setup' object found in setup.json");
        }

        JSONObject setupJson = json.getJSONObject("setup");

        // Load keys for all entities
        for (String entity : setupJson.keySet()) {
            if (entity.equals("clientLibrary") || entity.equals("leader")) {
                continue; 
            }
            JSONObject entityKeys = setupJson.getJSONObject(entity);
         
    
            if (!entityKeys.has("public") || !entityKeys.has("private")) {
                throw new Exception("Invalid key format for " + entity + ": missing public or private key paths");
            }

            String publicKeyPath = entityKeys.getString("public");
            String privateKeyPath = entityKeys.getString("private");

            PublicKey publicKey = loadPublicKeyFromFile(publicKeyPath);
            publicKeys.put(entity, publicKey);

            if (entity.equals(entityName)) {
                PrivateKey privateKey = loadPrivateKeyFromFile(privateKeyPath);
                privateKeys.put(entity, privateKey);
            }
        }
    }

    /**
     * Loads a public key from a file. Supports both PEM and binary formats.
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

        byte[] keyData = Files.readAllBytes(keyFile.toPath());
        String keyContent = new String(keyData).trim();
        byte[] keyBytes;

        // Check if the file is in PEM format
        if (keyContent.contains("-----BEGIN PUBLIC KEY-----")) {
            String base64Content = extractBase64Content(keyContent, "-----BEGIN PUBLIC KEY-----", "-----END PUBLIC KEY-----");
            keyBytes = Base64.getDecoder().decode(base64Content);
        } else {
            keyBytes = keyData;
        }

        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }

    /**
     * Loads a private key from a file. Supports both PEM and binary formats.
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

        byte[] keyData = Files.readAllBytes(keyFile.toPath());
        String keyContent = new String(keyData).trim();
        byte[] keyBytes;

        // Check if the file is in PEM format
        if (keyContent.contains("-----BEGIN PRIVATE KEY-----")) {
            String base64Content = extractBase64Content(keyContent, "-----BEGIN PRIVATE KEY-----", "-----END PRIVATE KEY-----");
            keyBytes = Base64.getDecoder().decode(base64Content);
        } else {
            keyBytes = keyData;
        }

        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(spec);
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
            throw new IllegalArgumentException("Invalid PEM format");
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

    public void storeAesKey(String member, SecretKey aesKey) {
        memberKeys.put(member, aesKey);
    }

    public void handleNewKey(String sourceMember, Message message) {
        try {
            String encryptedKey = message.getPayload();        
            PrivateKey privateKey = getPrivateKey(sourceMember); 
            String decryptedKey = Encryption.decryptWithRsa(encryptedKey, privateKey);
            SecretKey aesKey = Encryption.stringToAesKey(decryptedKey);
            storeAesKey(sourceMember, aesKey);        
        } catch (Exception e) {
            e.printStackTrace();
    }
}

    public SecretKey getAESKey(String memberName) {
        return memberKeys.get(memberName);
    }

    public boolean hasAESKey(String memberName) {
        return memberKeys.containsKey(memberName);
    }
}