package src.main.java.blockchain.network.transport;

import src.main.java.blockchain.network.Message;
import src.main.java.blockchain.network.links.UdpChannel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MessageSender is responsible for sending messages and managing acknowledgments.
 */
public class MessageSender implements MessageBuffer.RetransmissionHandler {
    private static final Logger LOGGER = Logger.getLogger(MessageSender.class.getName());
    
    private final UdpChannel channel;
    private final MessageBuffer messageBuffer;
    private final ExecutorService senderThreadPool;
    private final ConcurrentHashMap<UUID, MessageCallback> callbacks;
    
    /**
     * Callback interface for message sending status
     */
    public interface MessageCallback {
        void onAcknowledged(Message message);
        void onFailure(Message message, Exception e);
    }
    
    /**
     * Creates a message sender using the specified UDP channel
     * 
     * @param channel The UDP channel to use for sending messages
     */
    public MessageSender(UdpChannel channel) {
        this.channel = channel;
        this.messageBuffer = new MessageBuffer(this);
        this.senderThreadPool = Executors.newFixedThreadPool(2);
        this.callbacks = new ConcurrentHashMap<>();
    }
    
    /**
     * Sends a message to a specific target process ID
     * 
     * @param message The message to send
     * @param targetProcessId The ID of the target process
     * @param callback Optional callback for send status
     */
    public void sendMessage(Message message, int targetProcessId, MessageCallback callback) {
        senderThreadPool.submit(() -> {
            try {
                if (callback != null) {
                    callbacks.put(message.getMessageId(), callback);
                }
                
                // Send the message
                channel.sendMessage(message, targetProcessId);
                
                // Only buffer messages that require acknowledgment (not ACKs themselves)
                if (message.getType() != Message.MessageType.ACK) {
                    // Get the target address from the channel
                    InetSocketAddress targetAddress = getAddressForProcess(targetProcessId);
                    if (targetAddress != null) {
                        messageBuffer.addMessage(message, targetAddress);
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to send message to process " + targetProcessId, e);
                if (callback != null) {
                    callback.onFailure(message, e);
                    callbacks.remove(message.getMessageId());
                }
            }
        });
    }
    
    /**
     * Sends a message to a specific socket address
     * 
     * @param message The message to send
     * @param targetAddress The target socket address
     * @param callback Optional callback for send status
     */
    public void sendMessage(Message message, InetSocketAddress targetAddress, MessageCallback callback) {
        senderThreadPool.submit(() -> {
            try {
                if (callback != null) {
                    callbacks.put(message.getMessageId(), callback);
                }
                
                // Send the message
                channel.sendMessage(message, targetAddress);
                
                // Only buffer messages that require acknowledgment (not ACKs themselves)
                if (message.getType() != Message.MessageType.ACK) {
                    messageBuffer.addMessage(message, targetAddress);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to send message to " + targetAddress, e);
                if (callback != null) {
                    callback.onFailure(message, e);
                    callbacks.remove(message.getMessageId());
                }
            }
        });
    }
    
    /**
     * Processes an acknowledgment for a message
     * 
     * @param ackMessageId The ID of the original message being acknowledged
     * @return true if the message was pending and has been acknowledged
     */
    public boolean processAcknowledgment(UUID ackMessageId) {
        boolean result = messageBuffer.acknowledgeMessage(ackMessageId);
        
        if (result) {
            // Notify callback if registered
            MessageCallback callback = callbacks.remove(ackMessageId);
            if (callback != null) {
                // We don't have the original message anymore, but the ID is enough
                Message dummyMessage = new Message(-1, new byte[0], Message.MessageType.DATA);
                callback.onAcknowledged(dummyMessage);
            }
        }
        
        return result;
    }
    
    @Override
    public void retransmit(Message message, InetSocketAddress target) {
        try {
            channel.sendMessage(message, target);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to retransmit message " + message.getMessageId(), e);
        }
    }
    
    @Override
    public void onMaxRetransmissionsReached(Message message, InetSocketAddress target) {
        MessageCallback callback = callbacks.remove(message.getMessageId());
        if (callback != null) {
            callback.onFailure(message, new IOException("Max retransmissions reached"));
        }
    }
    
    /**
     * Gets the socket address for a process ID
     * 
     * @param processId The process ID
     * @return The socket address or null if not found
     */
    private InetSocketAddress getAddressForProcess(int processId) {
        // This would need to be implemented based on how your UdpChannel tracks processes
        // For now, we'll return null, but in a real implementation, you would lookup
        // the address from the channel or a registry
        return null;
    }
    
    /**
     * Shuts down the message sender
     */
    public void shutdown() {
        senderThreadPool.shutdown();
    }
}