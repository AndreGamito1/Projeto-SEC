package src.main.java.blockchain.network.utils;

import src.main.java.blockchain.network.Message;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * MessageBuffer manages outgoing messages that require acknowledgment.
 * It handles retransmission of unacknowledged messages.
 */
public class MessageBuffer {
    private static final Logger LOGGER = Logger.getLogger(MessageBuffer.class.getName());
    
    // Maximum number of retransmissions before giving up
    private static final int MAX_RETRANSMISSIONS = 5;
    
    // Initial timeout for retransmission (in milliseconds)
    private static final long INITIAL_TIMEOUT = 1000;
    
    // Maximum timeout for retransmission (in milliseconds)
    private static final long MAX_TIMEOUT = 8000;
    
    // Delay queue for scheduled retransmissions
    private final DelayQueue<PendingMessage> retransmissionQueue;
    
    // Map of message IDs to pending messages
    private final Map<UUID, PendingMessage> pendingMessages;
    
    // Callback interface for message retransmission
    public interface RetransmissionHandler {
        void retransmit(Message message, InetSocketAddress target);
        void onMaxRetransmissionsReached(Message message, InetSocketAddress target);
    }
    
    private final RetransmissionHandler handler;
    
    /**
     * Creates a message buffer with the specified retransmission handler
     * 
     * @param handler The handler for retransmitting messages
     */
    public MessageBuffer(RetransmissionHandler handler) {
        this.retransmissionQueue = new DelayQueue<>();
        this.pendingMessages = new ConcurrentHashMap<>();
        this.handler = handler;
        
        // Start the retransmission thread
        Thread retransmissionThread = new Thread(this::processRetransmissions);
        retransmissionThread.setDaemon(true);
        retransmissionThread.setName("MessageBuffer-Retransmission");
        retransmissionThread.start();
    }
    
    /**
     * Adds a message to the buffer for potential retransmission
     * 
     * @param message The message to buffer
     * @param target The target address
     */
    public void addMessage(Message message, InetSocketAddress target) {
        PendingMessage pendingMessage = new PendingMessage(message, target, INITIAL_TIMEOUT);
        pendingMessages.put(message.getMessageId(), pendingMessage);
        retransmissionQueue.add(pendingMessage);
        
        LOGGER.fine("Added message " + message.getMessageId() + " to buffer");
    }
    
    /**
     * Acknowledges receipt of a message, removing it from the buffer
     * 
     * @param messageId The ID of the message to acknowledge
     * @return true if the message was pending and has been removed
     */
    public boolean acknowledgeMessage(UUID messageId) {
        PendingMessage removed = pendingMessages.remove(messageId);
        if (removed != null) {
            LOGGER.fine("Acknowledged message " + messageId);
            return true;
        }
        return false;
    }
    
    /**
     * Main processing loop for retransmissions
     */
    private void processRetransmissions() {
        while (true) {
            try {
                PendingMessage message = retransmissionQueue.take();
                
                // Check if the message is still pending (not acknowledged)
                if (pendingMessages.containsKey(message.getMessage().getMessageId())) {
                    // Increment retry count and check if we've reached the maximum
                    message.incrementRetryCount();
                    
                    if (message.getRetryCount() < MAX_RETRANSMISSIONS) {
                        // Retransmit the message
                        LOGGER.fine("Retransmitting message " + message.getMessage().getMessageId() + 
                                " (attempt " + message.getRetryCount() + ")");
                        
                        handler.retransmit(message.getMessage(), message.getTarget());
                        
                        // Schedule next retransmission with exponential backoff
                        message.updateNextRetransmissionTime();
                        retransmissionQueue.add(message);
                    } else {
                        // Max retransmissions reached, give up
                        LOGGER.warning("Max retransmissions reached for message " + 
                                message.getMessage().getMessageId());
                        
                        pendingMessages.remove(message.getMessage().getMessageId());
                        handler.onMaxRetransmissionsReached(message.getMessage(), message.getTarget());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.warning("Error in retransmission thread: " + e.getMessage());
            }
        }
    }
    
    /**
     * Represents a message pending acknowledgment
     */
    private static class PendingMessage implements Delayed {
        private final Message message;
        private final InetSocketAddress target;
        private long nextRetransmissionTime;
        private int retryCount;
        private final long initialTimeout;
        
        public PendingMessage(Message message, InetSocketAddress target, long initialTimeout) {
            this.message = message;
            this.target = target;
            this.initialTimeout = initialTimeout;
            this.retryCount = 0;
            this.nextRetransmissionTime = System.currentTimeMillis() + initialTimeout;
        }
        
        public Message getMessage() {
            return message;
        }
        
        public InetSocketAddress getTarget() {
            return target;
        }
        
        public int getRetryCount() {
            return retryCount;
        }
        
        public void incrementRetryCount() {
            retryCount++;
        }
        
        public void updateNextRetransmissionTime() {
            // Exponential backoff: double the timeout for each retry, but cap at MAX_TIMEOUT
            long timeout = Math.min(initialTimeout * (1L << retryCount), MAX_TIMEOUT);
            nextRetransmissionTime = System.currentTimeMillis() + timeout;
        }
        
        @Override
        public long getDelay(TimeUnit unit) {
            long delay = nextRetransmissionTime - System.currentTimeMillis();
            return unit.convert(delay, TimeUnit.MILLISECONDS);
        }
        
        @Override
        public int compareTo(Delayed other) {
            if (other == this) {
                return 0;
            }
            long diff = getDelay(TimeUnit.MILLISECONDS) - other.getDelay(TimeUnit.MILLISECONDS);
            return (diff == 0) ? 0 : ((diff < 0) ? -1 : 1);
        }
    }
}
