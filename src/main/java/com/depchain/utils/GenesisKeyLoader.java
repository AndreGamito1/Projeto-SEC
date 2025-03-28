package com.depchain.utils; 

import org.json.JSONObject; 

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

public class GenesisKeyLoader {

    private final Map<String, PublicKey> loadedPublicKeys = new HashMap<>(); // Maps identifier -> PublicKey

    /**
     * Loads public keys defined in the genesis accounts configuration file.
     *
     * @param genesisAccountsFilePath Path to the genesis accounts JSON file.
     * @throws IOException If the file cannot be read.
     * @throws Exception If parsing or key loading fails.
     */
    public GenesisKeyLoader(String genesisAccountsFilePath) throws Exception {
        System.out.println("Loading genesis account keys from: " + genesisAccountsFilePath);
        String content = new String(Files.readAllBytes(Paths.get(genesisAccountsFilePath)));
        JSONObject json = new JSONObject(content);

        for (String accountIdentifier : json.keySet()) {
            JSONObject accountDetails = json.getJSONObject(accountIdentifier);

            if (!accountDetails.has("public_key_path")) {
                System.err.println("Warning: Missing 'public_key_path' for account '" + accountIdentifier + "' in " + genesisAccountsFilePath + ". Skipping.");
                continue;
            }
            String publicKeyPath = accountDetails.getString("public_key_path");

            try {
                PublicKey publicKey = loadPublicKeyFromFile(publicKeyPath); // Reuse loading logic
                this.loadedPublicKeys.put(accountIdentifier, publicKey);
                System.out.println("Loaded public key for genesis account: " + accountIdentifier);
            } catch (Exception e) {
                System.err.println("Failed to load public key for " + accountIdentifier + " from " + publicKeyPath + ": " + e.getMessage());
                throw new Exception("Failed to load key for " + accountIdentifier, e);
            }
        }
         if (this.loadedPublicKeys.isEmpty()) {
              System.err.println("Warning: No public keys were loaded from " + genesisAccountsFilePath);
         }
    }

    /**
     * Creates and returns the map needed for genesis state loading, mapping
     * derived string addresses to their corresponding PublicKey objects.
     *
     * @return The populated Map<String, PublicKey>.
     */
    public Map<String, PublicKey> getGenesisAddressMap() {
        Map<String, PublicKey> addressMap = new HashMap<>();
        System.out.println("--- Building Genesis Address Map from Loaded Keys ---");

        for (Map.Entry<String, PublicKey> entry : this.loadedPublicKeys.entrySet()) {
            String accountIdentifier = entry.getKey();
            PublicKey pubKey = entry.getValue();
            if (pubKey != null) {
                String address = deriveAddressFromPublicKey(pubKey);
                addressMap.put(address, pubKey);
                System.out.println("Mapped Address: " + address + " <-- Account ID: " + accountIdentifier);
            }
        }
        System.out.println("--- Address Map Built (" + addressMap.size() + " entries) ---");
        return addressMap;
    }


    /**
     * Derives a string address from an RSA PublicKey.
     */
    public static String deriveAddressFromPublicKey(PublicKey publicKey) {
        // Reusing the placeholder logic from before. ENSURE THIS IS CORRECT for your project.
        try {
            byte[] pubKeyBytes = publicKey.getEncoded();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(pubKeyBytes);
             byte[] addressBytes = hashBytes;
            return "0x" + bytesToHexString(addressBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }

    /** Helper to convert byte array to hex string. */
    private static String bytesToHexString(byte[] bytes) {
         StringBuilder hexString = new StringBuilder(2 * bytes.length);
         for (byte b : bytes) {
             String hex = Integer.toHexString(0xff & b);
             if (hex.length() == 1) hexString.append('0');
             hexString.append(hex);
         }
         return hexString.toString();
     }


    /** Loads a public key from a file (PEM or DER). */
    private static PublicKey loadPublicKeyFromFile(String keyFilePath) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        File keyFile = new File(keyFilePath);
        if (!keyFile.exists()) {
            throw new IOException("Public key file not found: " + keyFilePath);
        }
        byte[] keyData = Files.readAllBytes(keyFile.toPath());
        String keyContent = new String(keyData).trim();
        byte[] keyBytes;

        if (keyContent.contains("-----BEGIN PUBLIC KEY-----")) {
            String base64Content = extractBase64Content(keyContent, "-----BEGIN PUBLIC KEY-----", "-----END PUBLIC KEY-----");
            keyBytes = Base64.getDecoder().decode(base64Content);
        } else {
            keyBytes = keyData; // Assume DER/binary if no PEM markers
        }

        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        // Assuming RSA keys based on your KeyManager. Change if using EC or other.
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }

    /** Extracts Base64 content from PEM formatted keys. */
    private static String extractBase64Content(String pemContent, String beginMarker, String endMarker) {
        int beginIndex = pemContent.indexOf(beginMarker) + beginMarker.length();
        int endIndex = pemContent.indexOf(endMarker);
        if (beginIndex == -1 || endIndex == -1 || beginIndex >= endIndex) {
            throw new IllegalArgumentException("Invalid PEM format in file content.");
        }
        return pemContent.substring(beginIndex, endIndex).replaceAll("\\s", "");
    }
}