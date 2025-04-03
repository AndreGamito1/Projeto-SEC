package com.depchain.blockchain;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Represents a block in the blockchain.
 * Each block contains a list of transactions and header information.
 */
public class Block implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String hash;
    private String previousHash;
    private List<Transaction> transactions;
    
    
    /**
     * Creates a new block with the given parameters
     * 
     * @param blockId Unique identifier for this block
     * @param previousHash Hash of the previous block in the chain
     * @param transactions List of transactions included in this block
     */
    public Block(String previousHash, List<Transaction> transactions) {
        this.previousHash = previousHash;
        this.transactions = transactions != null ? transactions : new ArrayList<>();
        this.hash = calculateHash(); // Initial hash calculation
    }
    
    /**
     * Default constructor for deserialization
     */
    public Block() {
        this.transactions = new ArrayList<>();
    }
    
    /**
     * Calculates the hash of this block based on its contents
     * 
     * @return A hash string representing this block's contents
     */
    public String calculateHash() {
        // This is a placeholder - implement your actual hashing algorithm
        String dataToHash = transactions.toString();
        
        // Use a proper hashing algorithm in production (SHA-256, etc.)
        // For example: return DigestUtils.sha256Hex(dataToHash);
        return "hash_of_" + dataToHash.hashCode();
    }
    
    /**
     * Serializes this block to a byte array
     * 
     * @return Byte array containing the serialized block data
     * @throws IOException If serialization fails
     */
    public byte[] serializeBlock() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        // Write block header information
        dos.writeUTF(hash);
        dos.writeUTF(previousHash);

        // Write transaction count
        dos.writeInt(transactions.size());
        
        // Write each transaction in the block
        for (Transaction transaction : transactions) {
            dos.writeUTF(transaction.getSender());
            dos.writeUTF(transaction.getReceiver());
            dos.writeDouble(transaction.getAmount());
            dos.writeUTF(transaction.getData());
            dos.writeUTF(transaction.getSignature());
        }
        
        // Write block hash
        dos.writeUTF(hash);
        
        dos.flush();
        return baos.toByteArray();
    }
    
    /**
     * Converts this block to a Base64 encoded string for transmission
     * 
     * @return Base64 encoded string representation of this block
     * @throws IOException If serialization fails
     */
    public String toBase64String() throws IOException {
        return Base64.getEncoder().encodeToString(serializeBlock());
    }
    
    /**
     * Adds a transaction to this block if it's not already mined
     * 
     * @param transaction The transaction to add
     * @return true if the transaction was added, false if the block is already mined
     */
    public boolean addTransaction(Transaction transaction) {
        if (transaction == null) {
            return false;
        }

        transactions.add(transaction);
        return true;
    }
    
    // Getters and setters
    
    
    public String getPreviousHash() {
        return previousHash;
    }
    
    public void setPreviousHash(String previousHash) {
        this.previousHash = previousHash;
    }
    
    public List<Transaction> getTransactions() {
        return transactions;
    }
    
    public void setTransactions(List<Transaction> transactions) {
        this.transactions = transactions;
    }
    
    public String getHash() {
        return hash;
    }
    
    public void setHash(String hash) {
        this.hash = hash;
    }
    @Override
    public String toString() {
        return "Block{" +
               ", previousHash='" + previousHash + '\'' +
               ", transactions=" + transactions.size() +
               ", hash='" + hash + '\'' +
               '}';
    }
}