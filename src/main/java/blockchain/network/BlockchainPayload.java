package src.main.java.blockchain.network;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Represents the payload for blockchain-specific messages.
 * This separates the content of the message from the message delivery mechanisms.
 */
public class BlockchainPayload implements Serializable {

    private static final long serialVersionUID = 1L;
    
    // Enum to identify the type of blockchain operation
    public enum OperationType {
        APPEND,     // Append data to the blockchain
        READ,       // Read data from the blockchain
        PROPOSE,    // Propose a new block (for consensus)
        VOTE,       // Vote on a proposed block
        DECISION    // Final decision on a block
    }
    
    // The type of operation
    private final OperationType operationType;
    
    // Epoch number for consensus
    private final int epoch;
    
    // Block/transaction data
    private final byte[] data;
    
    // Additional metadata if needed
    private final String metadata;
    
    /**
     * Constructor for creating a blockchain payload
     * 
     * @param operationType The type of blockchain operation
     * @param epoch The epoch number
     * @param data The actual data for the operation
     * @param metadata Additional information if needed
     */
    public BlockchainPayload(OperationType operationType, int epoch, byte[] data, String metadata) {
        this.operationType = operationType;
        this.epoch = epoch;
        this.data = data;
        this.metadata = metadata;
    }
    
    /**
     * Serializes this payload into a byte array that can be included in a Message
     * 
     * @return Serialized payload
     */
    public byte[] toByteArray() throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            
            oos.writeObject(this);
            return baos.toByteArray();
        }
    }
    
    /**
     * Deserializes a byte array back into a BlockchainPayload
     * 
     * @param bytes The serialized payload
     * @return The BlockchainPayload object
     */
    public static BlockchainPayload fromByteArray(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            
            return (BlockchainPayload) ois.readObject();
        }
    }
    
    // Getters
    
    public OperationType getOperationType() {
        return operationType;
    }
    
    public int getEpoch() {
        return epoch;
    }
    
    public byte[] getData() {
        return data;
    }
    
    public String getMetadata() {
        return metadata;
    }
    
    @Override
    public String toString() {
        return "BlockchainPayload{" +
                "operationType=" + operationType +
                ", epoch=" + epoch +
                ", dataSize=" + (data != null ? data.length : 0) +
                ", metadata='" + metadata + '\'' +
                '}';
    }
}