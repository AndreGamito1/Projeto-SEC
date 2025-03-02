import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.example.AuthenticatedMessage;
import com.example.AuthenticatedPerfectLinks;
import com.example.Message;
import com.example.StubbornLinks;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthenticatedPerfectLinksTest {

    @Mock
    private StubbornLinks mockStubbornLinks;

    private AuthenticatedPerfectLinks authenticatedPerfectLinks;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Create a custom instance for testing with the mock StubbornLinks
        authenticatedPerfectLinks = new AuthenticatedPerfectLinksForTest(mockStubbornLinks);
    }
    
    // Internal class to allow testing without creating real network connections
    private static class AuthenticatedPerfectLinksForTest extends AuthenticatedPerfectLinks {
        private StubbornLinks stubbornLink;
        
        public AuthenticatedPerfectLinksForTest(StubbornLinks stubbornLink) {
            super(null, 0, 0); // These parameters are ignored in this test class
            this.stubbornLink = stubbornLink;
        }
        
        @Override
        public void alp2pSend(String dest, Message message) {
            String authString = authenticate(message);
            AuthenticatedMessage authMessage = new AuthenticatedMessage(message, authString);
            stubbornLink.sp2pSend(authMessage);
        }
    }

    @Test
    void testAuthenticate() {
        // Create message
        Message message = new Message("test payload", "test command");
        
        // Authenticate the message
        String authString = authenticatedPerfectLinks.authenticate(message);
        
        // Verify authString is not null and not empty (it should be a SHA-256 hash now)
        assertNotNull(authString);
        assertFalse(authString.isEmpty());
        
        // Basic validation for SHA-256 hash (64 hex characters)
        assertEquals(64, authString.length());
        
        // Verify consistency - same message content should produce same hash
        Message sameContentMessage = new Message("test payload", "test command");
        String secondAuthString = authenticatedPerfectLinks.authenticate(sameContentMessage);
        assertEquals(authString, secondAuthString);
    }
    
    @Test
    void testAlp2pSend() {
        // Create message
        Message message = new Message("test payload", "test command");
        
        // Send the message
        authenticatedPerfectLinks.alp2pSend("dest", message);
        
        // Verify that StubbornLinks's sp2pSend method was called with any AuthenticatedMessage
        verify(mockStubbornLinks, times(1)).sp2pSend(any(AuthenticatedMessage.class));
    }
    
    @Test
    void testAlp2pDeliver() {
        // Create an authenticated message with valid authentication
        Message message = new Message("test payload", "test command");
        String authString = authenticatedPerfectLinks.authenticate(message);
        AuthenticatedMessage authMessage = new AuthenticatedMessage(message, authString);
        
        // Verify there are no delivered messages initially
        assertEquals(0, authenticatedPerfectLinks.getReceivedSize());
        
        // Deliver the message
        authenticatedPerfectLinks.alp2pReceive(authMessage);
        
        // Verify the message was delivered
        assertEquals(1, authenticatedPerfectLinks.getReceivedSize());
        
        // Verify the delivered message equals the original message
        List<AuthenticatedMessage> deliveredMessages = authenticatedPerfectLinks.getReceivedMessages();
        assertEquals(1, deliveredMessages.size());
        
        AuthenticatedMessage deliveredMessage = deliveredMessages.get(0);
        assertEquals(authMessage.getMessageID(), deliveredMessage.getMessageID());
        assertEquals(authMessage.getPayload(), deliveredMessage.getPayload());
        assertEquals(authMessage.getCommand(), deliveredMessage.getCommand());
        assertEquals(authMessage.getAuthString(), deliveredMessage.getAuthString());
    }
    
    @Test
    void testVerifyAuthRejection() {
        // Create a message with invalid authentication
        Message message = new Message("test payload", "test command");
        AuthenticatedMessage authMessage = new AuthenticatedMessage(message, "invalid_auth_string");
        
        // Verify there are no delivered messages initially
        assertEquals(0, authenticatedPerfectLinks.getReceivedSize());
        
        // Try to deliver the message with invalid auth
        authenticatedPerfectLinks.alp2pReceive(authMessage);
        
        // Verify the message was NOT delivered due to invalid authentication
        assertEquals(0, authenticatedPerfectLinks.getReceivedSize());
    }
    
    @Test
    void testDuplicateMessageRejection() {
        // Create an authenticated message with valid authentication
        Message message = new Message("test payload", "test command");
        String authString = authenticatedPerfectLinks.authenticate(message);
        AuthenticatedMessage authMessage = new AuthenticatedMessage(message, authString);
        
        // Deliver the message for the first time
        authenticatedPerfectLinks.alp2pReceive(authMessage);
        assertEquals(1, authenticatedPerfectLinks.getReceivedSize());
        
        // Try to deliver the same message again
        authenticatedPerfectLinks.alp2pReceive(authMessage);
        
        // Verify the message was NOT delivered a second time (duplicate detection)
        assertEquals(1, authenticatedPerfectLinks.getReceivedSize());
    }
    
    @Test
    void testOnMessageReceived() {
        // Create an authenticated message with valid authentication
        Message message = new Message("test payload", "test command");
        String authString = authenticatedPerfectLinks.authenticate(message);
        AuthenticatedMessage authMessage = new AuthenticatedMessage(message, authString);
        
        // Call the callback method
        authenticatedPerfectLinks.onMessageReceived(authMessage);
        
        // Verify the message was delivered (alp2pDeliver should have been called)
        assertEquals(1, authenticatedPerfectLinks.getReceivedSize());
        
        // Verify the delivered message equals the original message
        List<AuthenticatedMessage> deliveredMessages = authenticatedPerfectLinks.getReceivedMessages();
        assertEquals(authMessage, deliveredMessages.get(0));
    }
    
    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void testIntegrationWithRealMessage() throws InterruptedException {
        // This test simulates a more realistic integration scenario
        // A latch to control test synchronization
        CountDownLatch deliveryLatch = new CountDownLatch(1);
        
        // A custom implementation to record when the message is delivered
        AuthenticatedPerfectLinks realAuthLinks = new AuthenticatedPerfectLinks("127.0.0.1", 8889, 8888) {
            @Override
            public void alp2pReceive(AuthenticatedMessage authMessage) {
                super.alp2pReceive(authMessage);
                deliveryLatch.countDown();
            }
        };
        
        // Create a test message
        Message testMessage = new Message("payload-" + UUID.randomUUID(), "test-integration");
        
        // Get proper authentication string using the actual implementation
        String authString = realAuthLinks.authenticate(testMessage);
        AuthenticatedMessage authMessage = new AuthenticatedMessage(testMessage, authString);
        
        // Simulate receiving a message as if it came from the network
        realAuthLinks.onMessageReceived(authMessage);
        
        // Wait for delivery or timeout
        boolean messageDelivered = deliveryLatch.await(1, TimeUnit.SECONDS);
        
        // Verify the message was delivered
        assertTrue(messageDelivered, "Message should be delivered within the timeout");
        assertEquals(1, realAuthLinks.getReceivedSize());
        assertEquals(authMessage.getMessageID(), realAuthLinks.getReceivedMessages().get(0).getMessageID());
    }
}