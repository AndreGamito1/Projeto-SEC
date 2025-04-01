package com.depchain.blockchain;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

import com.depchain.utils.Encryption;

/**
 * Represents a transaction in the blockchain.
 * Each transaction contains information about the sender, receiver, amount, and signature.
 */
public class Transaction implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sender;
    private String data; // For Smart Contracts
    private String receiver;
    private double amount;
    private String signature;
    
    /**
     * Creates a new transaction with the given parameters
     * 
     * @param sender The address/ID of the sender
     * @param receiver The address/ID of the receiver
     * @param amount The transaction amount
     */
    public Transaction(String sender, String receiver, double amount, String data, String signature) {
        this.sender = sender;
        this.receiver = receiver;
        this.amount = amount;
        this.data = data;
        this.signature = signature;
        }
    
    /**
     * Default constructor for deserialization
     */
    public Transaction() {
    }
    
    /**
     * Signs this transaction with the provided signature
     * 
     * @param signature The digital signature for this transaction
     */
    public void setSignature(String signature) {
        this.signature = signature;
    }
    
    /**
     * Calculates a hash of this transaction's data
     * 
     * @return A hash string for this transaction
     */
    public String calculateHash() {
        // This is a placeholder - implement your actual hashing algorithm
        String data = sender + receiver + Double.toString(amount);
        
        // Use a proper hashing algorithm in production (SHA-256, etc.)
        // For example: return DigestUtils.sha256Hex(data);
        return "hash_of_" + data.hashCode();
    }


     // --- Static Serialization / Deserialization Methods ---

    /**
     * Serializes the given Transaction object into a Base64 encoded String.
     *
     * @param tx The Transaction object to serialize.
     * @return A Base64 encoded String representing the serialized object.
     * @throws IOException If an I/O error occurs during serialization.
     */
    public static String serializeToString(Transaction tx) throws IOException {
        if (tx == null) {
            return null;
        }
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        try (ObjectOutputStream objOut = new ObjectOutputStream(byteOut)) {
            objOut.writeObject(tx);
        } 
        return Base64.getEncoder().encodeToString(byteOut.toByteArray());
    }

    /**
     * Deserializes a Transaction object from a Base64 encoded String.
     *
     * @param base64String The Base64 encoded String. 
     * @return The deserialized Transaction object.
     * @throws IOException            If an I/O error occurs during deserialization.
     * @throws ClassNotFoundException If the Transaction class definition cannot be found.
     * @throws IllegalArgumentException If the input string is null, empty, or not valid Base64.
     */
    public static Transaction deserializeFromString(String base64String) throws IOException, ClassNotFoundException {
        if (base64String == null || base64String.isEmpty()) {
             throw new IllegalArgumentException("Input Base64 string cannot be null or empty.");
        }
        byte[] bytes;
        try {
             bytes = Base64.getDecoder().decode(base64String);
        } catch (IllegalArgumentException e) {
             throw new IllegalArgumentException("Input string is not valid Base64.", e);
        }

        ByteArrayInputStream byteIn = new ByteArrayInputStream(bytes);
        try (ObjectInputStream objIn = new ObjectInputStream(byteIn)) {
            Object obj = objIn.readObject();
            if (obj instanceof Transaction) {
                return (Transaction) obj;
            } else {
                throw new ClassCastException("Deserialized object is not of type Transaction: " + obj.getClass().getName());
            }
        } 
    }


    /**
     * Calculates a hash of this transaction's data to be signed
     */
    private String getDataToSign() {
        String data = sender + receiver + Double.toString(amount);
        try {
            // Create a message digest using SHA-256
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            System.err.println("Error creating hash: " + e.getMessage());
            return "";
        }
    }

    /**
     * Signs this transaction with the sender's private key
     */
    public boolean sign(PrivateKey privateKey) {
        try {
            // Get the digest of data to sign (much smaller than the full data)
            String dataDigest = getDataToSign();
            
            // Sign the digest instead of the full data
            String signatureData = Encryption.encryptWithPrivateKey(dataDigest, privateKey);
            if (signatureData == null) { 
                return false; 
            }
            
            this.signature = signatureData;
            return true;
        } catch (Exception e) {
            System.err.println("Error signing transaction: " + e.getMessage());
            return false;
        }
    }

    /**
     * Verifies the transaction signature
     */
    public boolean isValid(PublicKey publicKey) {
        try {
            if (signature == null || signature.isEmpty()) {
                return false;
            }
            
            String dataDigest = getDataToSign();
            String decryptedSignature = Encryption.decryptWithPublicKey(signature, publicKey);
            
            if (decryptedSignature == null) {
                return false;
            }
            
            return decryptedSignature.equals(dataDigest);
        } catch (Exception e) {
            System.err.println("Error verifying transaction: " + e.getMessage());
            return false;
        }
    }

    // Getters and setters
    
    public String getSender() {
        return sender;
    }
    
    public void setSender(String sender) {
        this.sender = sender;
    }
    
    public String getReceiver() {
        return receiver;
    }
    
    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }
    
    public double getAmount() {
        return amount;
    }
    
    public void setAmount(double amount) {
        this.amount = amount;
    }
    
    public String getSignature() {
        return signature;
    }
    
    @Override
    public String toString() {
        return "Transaction{" +
               "sender='" + sender + '\'' +
               ", receiver='" + receiver + '\'' +
               ", amount=" + amount +
               '}';
    }
}