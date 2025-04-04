package com.depchain.blockchain;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.File;
import java.io.IOException;
import java.util.Map;

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
        this.hash = calculateHash(previousHash); // Initial hash calculation
    }

    public Block(String previousHash, List<Transaction> transactions, String hash) {
        this.hash = hash;
        this.previousHash = previousHash;
        this.transactions = transactions != null ? transactions : new ArrayList<>();
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
    public static String calculateHash(String previousHash) {
        String dataToHash = previousHash + System.currentTimeMillis();
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(dataToHash.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
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
     * Deserializes a byte array into a Block and adds it to the blockchain
     *
     * @param serializedBlock Byte array containing the serialized block data
     * @return The deserialized Block object
     * @throws IOException If deserialization fails
     */
    public static Block deserializeBlock(byte[] serializedBlock) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(serializedBlock);
        DataInputStream dis = new DataInputStream(bais);
        
        // Read block header information
        String hash = dis.readUTF();
        String previousHash = dis.readUTF();
        
        // Create a new block with the header information
        Block block = new Block(previousHash, null);
        block.setHash(hash);
        // Read transaction count
        int transactionCount = dis.readInt();
        
        // Read each transaction and add to the block
        for (int i = 0; i < transactionCount; i++) {
            String sender = dis.readUTF();
            String receiver = dis.readUTF();
            double amount = dis.readDouble();
            String data = dis.readUTF();
            String signature = dis.readUTF();
            
            // Create and add the transaction to the block
            Transaction transaction = new Transaction(sender, receiver, amount, data, signature);
            block.addTransaction(transaction);
        }
        
        // Read and verify block hash
        String verificationHash = dis.readUTF();
        if (!hash.equals(verificationHash)) {
            throw new IOException("Block verification failed: Hash mismatch");
        }
        
        return block;
    }

    /**
     * Deserializes a Base64 encoded string into a Block
     *
     * @param base64String Base64 encoded string representation of a block
     * @return The deserialized Block object
     * @throws IOException If deserialization fails
     */
    public static Block deserializeFromBase64(String base64String) throws IOException {
        byte[] serializedBlock = Base64.getDecoder().decode(base64String);
        return deserializeBlock(serializedBlock);
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
    

    @Override
    public String toString() {
        return "Block{" +
               ", previousHash='" + previousHash + '\'' +
               ", transactions=" + transactions.size() +
               ", hash='" + hash + '\'' +
               '}';
    }

    /**
     * Saves the block as a JSON file in the specified directory
     * 
     * @param block The block to save
     * @throws IOException If file writing fails
     */ 
    public static void saveBlockAsJson(Block block, WorldState worldState) throws IOException {
        File dir = new File("src/main/resources/blocks");
        if (!dir.exists()) {
            dir.mkdirs();
        }
    
        int blockNumber = 1;
        File[] files = dir.listFiles((d, name) -> name.matches("block\\d+\\.json"));
        if (files != null) {
            blockNumber = files.length + 1;
        }
    
        File blockFile = new File(dir, "block" + blockNumber + ".json");
    
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        
        root.put("block_hash", block.getHash());
        root.put("previous_block_hash", block.getPreviousHash());
    
        ArrayNode transactionsNode = root.putArray("transactions");
        for (Transaction tx : block.getTransactions()) {
            ObjectNode txNode = transactionsNode.addObject();
            txNode.put("sender", tx.getSender());
            txNode.put("receiver", tx.getReceiver());
            txNode.put("amount", tx.getAmount());
            txNode.put("data", tx.getData());
            txNode.put("signature", tx.getSignature());
        }

        ObjectNode stateNode = root.putObject("state");
        for (Map.Entry<String, AccountState> entry : worldState.getAccounts().entrySet()) {
            String address = entry.getKey();
            AccountState account = entry.getValue();
            ObjectNode accNode = stateNode.putObject(address);

            accNode.put("balance", account.getBalance());

            if (account.isContract()) {
                accNode.put("code", account.getCode());
                ObjectNode storageNode = accNode.putObject("storage");

                if (account.getStorage() != null) {
                    for (Map.Entry<String, String> storageEntry : account.getStorage().entrySet()) {
                        storageNode.put(storageEntry.getKey(), storageEntry.getValue());
                    }
                }
            }
        }

    
        mapper.writerWithDefaultPrettyPrinter().writeValue(blockFile, root);
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
}