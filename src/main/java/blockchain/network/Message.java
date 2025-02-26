package src.main.java.blockchain.network;

import java.io.Serializable;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/**
 * Base message class for the authenticated perfect links abstraction.
 * Provides functionality for message identification, authentication, and serialization.
 */
public class Message implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    // Unique message identifier to detect duplicates
    private final UUID messageId;
    
    // ID of the process that sent this message
    private final int senderId;
    
    // Timestamp when the message was created
    private final long timestamp;
    
    // Digital signature of the message content
    private byte[] signature;
    
    // Actual message data
    private final byte[] payload;
    
    // Type of message for handling different protocol messages
    private final MessageType type;
    
    /**
     * Enum defining different types of messages in the system
     */
    public enum MessageType {
        DATA,       // Regular data message
        ACK,        // Acknowledgment message
        HEARTBEAT   // Heartbeat message for connection monitoring
    }
    
    /**
     * Constructor for creating a new message
     * 
     * @param senderId ID of the sending process
     * @param payload The actual message data
     * @param type Type of the message
     */
    public Message(int senderId, byte[] payload, MessageType type) {
        this.messageId = UUID.randomUUID();
        this.senderId = senderId;
        this.timestamp = Instant.now().toEpochMilli();
        this.payload = payload;
        this.type = type;
    }
    
    /**
     * Signs the message using the sender's private key
     * 
     * @param privateKey Sender's private key
     * @throws Exception If signing fails
     */
    public void sign(PrivateKey privateKey) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        
        // Add all fields to the signature
        signer.update(messageId.toString().getBytes());
        signer.update(Integer.toString(senderId).getBytes());
        signer.update(Long.toString(timestamp).getBytes());
        signer.update(payload);
        signer.update(type.toString().getBytes());
        
        this.signature = signer.sign();
    }
    
    /**
     * Verifies the signature of the message using the sender's public key
     * 
     * @param publicKey Sender's public key
     * @return true if signature is valid, false otherwise
     * @throws Exception If verification fails
     */
    public boolean verifySignature(PublicKey publicKey) throws Exception {
        if (signature == null) {
            return false;
        }
        
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey);
        
        // Update with the same fields used for signing
        verifier.update(messageId.toString().getBytes());
        verifier.update(Integer.toString(senderId).getBytes());
        verifier.update(Long.toString(timestamp).getBytes());
        verifier.update(payload);
        verifier.update(type.toString().getBytes());
        
        return verifier.verify(signature);
    }
    
    /**
     * Creates an acknowledgment message for this message
     * 
     * @param receiverId ID of the process sending the acknowledgment
     * @return A new acknowledgment message
     */
    public Message createAck(int receiverId) {
        // Create an ACK message with the original message ID in its payload
        return new Message(receiverId, messageId.toString().getBytes(), MessageType.ACK);
    }
    
    // Getters
    
    public UUID getMessageId() {
        return messageId;
    }
    
    public int getSenderId() {
        return senderId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public byte[] getSignature() {
        return signature;
    }
    
    public byte[] getPayload() {
        return payload;
    }
    
    public MessageType getType() {
        return type;
    }
    
    @Override
    public String toString() {
        return "Message{" +
                "messageId=" + messageId +
                ", senderId=" + senderId +
                ", type=" + type +
                ", timestamp=" + timestamp +
                ", payloadSize=" + (payload != null ? payload.length : 0) +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return messageId.equals(message.messageId);
    }
    
    @Override
    public int hashCode() {
        return messageId.hashCode();
    }
}