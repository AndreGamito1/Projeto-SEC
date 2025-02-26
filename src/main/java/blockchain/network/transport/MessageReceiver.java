package src.main.java.blockchain.network.transport;

import src.main.java.blockchain.network.Message;
import src.main.java.blockchain.network.links.UdpChannel;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MessageReceiver is responsible for receiving and processing incoming messages.
 */
public class MessageReceiver implements UdpChannel.MessageReceiver {
    private static final Logger LOGGER = Logger.getLogger(MessageReceiver.class.getName());
    
    private final UdpChannel channel;
    private final MessageSender sender;
    private final ExecutorService processingThreadPool;
    private final ConcurrentHashMap<UUID, Boolean> receivedMessages;
    private MessageHandler messageHandler;
    
    /**
     * Interface for handling received messages after processing
     */
    public interface MessageHandler {
        void onMessageReceived(Message message, int senderId);
        void onError(Exception e);
    }
    
    /**
     * Creates a message receiver for the specified UDP channel
     * 
     * @param channel The UDP channel to receive messages from
     * @param sender The message sender for sending acknowledgments
     */
    public MessageReceiver(UdpChannel channel, MessageSender sender) {
        this.channel = channel;
        this.sender = sender;
        this.processingThreadPool = Executors.newFixedThreadPool(4);
        this.receivedMessages = new ConcurrentHashMap<>();
        
        // Register this receiver with the channel
        channel.setMessageReceiver(this);
    }
    
    /**
     * Sets the message handler
     * 
     * @param handler The handler implementation
     */
    public void setMessageHandler(MessageHandler handler) {
        this.messageHandler = handler;
    }
    
    @Override
    public void onMessageReceived(Message message, InetSocketAddress sender) {
        processingThreadPool.submit(() -> processMessage(message, sender));
    }
    
    @Override
    public void onError(Exception e) {
        LOGGER.log(Level.WARNING, "Error in UDP channel", e);
        if (messageHandler != null) {
            messageHandler.onError(e);
        }
    }
    
    /**
     * Processes a received message
     * 
     * @param message The received message
     * @param sender The sender's address
     */
    private void processMessage(Message message, InetSocketAddress sender) {
        try {
            // Check message type
            if (message.getType() == Message.MessageType.ACK) {
                // Process acknowledgment
                String ackIdStr = new String(message.getPayload());
                UUID ackId = UUID.fromString(ackIdStr);
                sender.processAcknowledgment(ackId);
                return;
            }
            
            // Check for duplicate messages
            if (isDuplicate(message.getMessageId())) {
                LOGGER.fine("Received duplicate message: " + message.getMessageId());
                // Still send ACK for duplicates
                sendAcknowledgment(message, sender);
                return;
            }
            
            // Mark message as received to detect future duplicates
            markAsReceived(message.getMessageId());
            
            // Send acknowledgment
            sendAcknowledgment(message, sender);
            
            // Deliver message to handler
            if (messageHandler != null) {
                messageHandler.onMessageReceived(message, message.getSenderId());
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error processing message", e);
            if (messageHandler != null) {
                messageHandler.onError(e);
            }
        }
    }
    
    /**
     * Sends an acknowledgment for a received message
     * 
     * @param originalMessage The message to acknowledge
     * @param recipient The recipient of the acknowledgment
     */
    private void sendAcknowledgment(Message originalMessage, InetSocketAddress recipient) {
        try {
            // Create ACK message
            Message ackMessage = new Message(
                    channel.getProcessId(),
                    originalMessage.getMessageId().toString().getBytes(),
                    Message.MessageType.ACK);
            
            // Send ACK (directly through channel to avoid buffering)
            channel.sendMessage(ackMessage, recipient);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send acknowledgment", e);
        }
    }
    
    /**
     * Checks if a message with the given ID has been received before
     * 
     * @param messageId The message ID to check
     * @return true if the message is a duplicate
     */
    private boolean isDuplicate(UUID messageId) {
        return receivedMessages.containsKey(messageId);
    }
    
    /**
     * Marks a message as received to detect future duplicates
     * 
     * @param messageId The message ID to mark
     */
    private void markAsReceived(UUID messageId) {
        receivedMessages.put(messageId, Boolean.TRUE);
        
        // In a production system, you would need a cleanup mechanism
        // to prevent the receivedMessages map from growing indefinitely.
        // This could be:
        // 1. A background thread that removes old entries
        // 2. A time-based expiration mechanism
        // 3. A size-based limit with LRU eviction
    }
    
    /**
     * Shuts down the message receiver
     */
    public void shutdown() {
        processingThreadPool.shutdown();
    }
}