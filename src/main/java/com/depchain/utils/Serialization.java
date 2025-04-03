package com.depchain.utils;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Base64;

import com.depchain.blockchain.Block;
import com.depchain.blockchain.Transaction;

public class Serialization {

/**
 * Deserializes a byte array into a Block and adds it to the blockchain
 *
 * @param serializedBlock Byte array containing the serialized block data
 * @return The deserialized Block object
 * @throws IOException If deserialization fails
 */
public Block deserializeBlock(byte[] serializedBlock) throws IOException {
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
public Block deserializeFromBase64(String base64String) throws IOException {
    byte[] serializedBlock = Base64.getDecoder().decode(base64String);
    return deserializeBlock(serializedBlock);
}
    
}
