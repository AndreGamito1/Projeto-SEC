package com.depchain.utils;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class Encryption {
    
    /**
     * Encrypts a string using RSA and returns the result as a Base64-encoded string.
     * 
     * @param plainText The plain text to encrypt
     * @param publicKey The RSA public key
     * @return The Base64-encoded encrypted data
     * @throws Exception If encryption fails
     */
    public static String encryptWithRsa(String plainText, PublicKey publicKey) throws Exception {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            
            byte[] textBytes = plainText.getBytes(StandardCharsets.UTF_8);
            
            // Check RSA size limitations
            int keySize = ((java.security.interfaces.RSAPublicKey)publicKey).getModulus().bitLength();
            int maxDataSize = (keySize / 8) - 11; // For PKCS1 padding
            
            if (textBytes.length > maxDataSize) {
                Logger.log(Logger.AUTH_LINKS, "Data too large for RSA encryption: " + 
                          textBytes.length + " bytes (max " + maxDataSize + " bytes)");
                throw new IllegalArgumentException("Data too large for RSA encryption");
            }
            
            byte[] encryptedBytes = cipher.doFinal(textBytes);
            
            // Encode binary data as Base64 string for safe serialization
            return Base64.getEncoder().encodeToString(encryptedBytes);
            
        } catch (Exception e) {
            Logger.log(Logger.AUTH_LINKS, "Encryption error: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Decrypts a Base64-encoded string using RSA.
     * 
     * @param encryptedBase64 The Base64-encoded encrypted data
     * @param privateKey The RSA private key
     * @return The decrypted string
     * @throws Exception If decryption fails
     */
    public static String decryptWithRsa(String encryptedBase64, PrivateKey privateKey) throws Exception {
        try {
            // Verify input is not null or empty
            if (encryptedBase64 == null || encryptedBase64.isEmpty()) {
                throw new IllegalArgumentException("Encrypted data is empty");
            }
            
            // Make sure the data is properly Base64 encoded
            byte[] encryptedBytes;
            try {
                encryptedBytes = Base64.getDecoder().decode(encryptedBase64);
            } catch (IllegalArgumentException e) {
                Logger.log(Logger.AUTH_LINKS, "Invalid Base64 input: " + encryptedBase64.substring(0, Math.min(20, encryptedBase64.length())) + "...");
                throw new IllegalArgumentException("Invalid Base64 input: " + e.getMessage());
            }
            
            // Check the size against the RSA key
            int keySize = ((java.security.interfaces.RSAPrivateKey)privateKey).getModulus().bitLength() / 8;
            if (encryptedBytes.length > keySize) {
                Logger.log(Logger.AUTH_LINKS, "Encrypted data size (" + encryptedBytes.length + 
                          " bytes) exceeds key size (" + keySize + " bytes)");
                throw new IllegalArgumentException("Data size exceeds key size");
            }
            
            // Decrypt the data
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            
            return new String(decryptedBytes, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            Logger.log(Logger.AUTH_LINKS, "Decryption error: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Converts a string representation of an AES key back to a SecretKey object.
     * 
     * @param keyString The Base64-encoded AES key
     * @return The SecretKey object
     */
    public static SecretKey stringToAesKey(String keyString) {
        byte[] decodedKey = Base64.getDecoder().decode(keyString);
        return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
    }
    
    /**
     * Converts a SecretKey object to a string representation.
     * 
     * @param secretKey The AES SecretKey
     * @return The Base64-encoded string representation
     */
    public static String aesKeyToString(SecretKey secretKey) {
        return Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }
}