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
        
        authenticatedPerfectLinks = new AuthenticatedPerfectLinksForTest(mockStubbornLinks);
    }
    
    private static class AuthenticatedPerfectLinksForTest extends AuthenticatedPerfectLinks {
        private StubbornLinks stubbornLink;
        
        public AuthenticatedPerfectLinksForTest(StubbornLinks stubbornLink) {
            super(null, 0, 0);
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
        Message message = new Message("test payload", "test command");
        
        String authString = authenticatedPerfectLinks.authenticate(message);
        
        assertNotNull(authString);
        assertFalse(authString.isEmpty());
        
        assertEquals(64, authString.length());
        
        Message sameContentMessage = new Message("test payload", "test command");
        String secondAuthString = authenticatedPerfectLinks.authenticate(sameContentMessage);
        assertEquals(authString, secondAuthString);
    }
    
    @Test
    void testAlp2pSend() {
        Message message = new Message("test payload", "test command");
        
        authenticatedPerfectLinks.alp2pSend("dest", message);
        
        verify(mockStubbornLinks, times(1)).sp2pSend(any(AuthenticatedMessage.class));
    }
    
    @Test
    void testAlp2pDeliver() {
        Message message = new Message("test payload", "test command");
        String authString = authenticatedPerfectLinks.authenticate(message);
        AuthenticatedMessage authMessage = new AuthenticatedMessage(message, authString);
        
        assertEquals(0, authenticatedPerfectLinks.getReceivedSize());
        
        authenticatedPerfectLinks.alp2pReceive(authMessage);
        
        assertEquals(1, authenticatedPerfectLinks.getReceivedSize());
        
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
        Message message = new Message("test payload", "test command");
        AuthenticatedMessage authMessage = new AuthenticatedMessage(message, "invalid_auth_string");
        
        assertEquals(0, authenticatedPerfectLinks.getReceivedSize());
        
        authenticatedPerfectLinks.alp2pReceive(authMessage);
        
        assertEquals(0, authenticatedPerfectLinks.getReceivedSize());
    }
    
    @Test
    void testDuplicateMessageRejection() {
        Message message = new Message("test payload", "test command");
        String authString = authenticatedPerfectLinks.authenticate(message);
        AuthenticatedMessage authMessage = new AuthenticatedMessage(message, authString);
        
        authenticatedPerfectLinks.alp2pReceive(authMessage);
        assertEquals(1, authenticatedPerfectLinks.getReceivedSize());
        
        authenticatedPerfectLinks.alp2pReceive(authMessage);
        
        assertEquals(1, authenticatedPerfectLinks.getReceivedSize());
    }
    
    @Test
    void testOnMessageReceived() {
        Message message = new Message("test payload", "test command");
        String authString = authenticatedPerfectLinks.authenticate(message);
        AuthenticatedMessage authMessage = new AuthenticatedMessage(message, authString);
        
        authenticatedPerfectLinks.onMessageReceived(authMessage);
        
        assertEquals(1, authenticatedPerfectLinks.getReceivedSize());
        
        List<AuthenticatedMessage> deliveredMessages = authenticatedPerfectLinks.getReceivedMessages();
        assertEquals(authMessage, deliveredMessages.get(0));
    }
    
    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void testIntegrationWithRealMessage() throws InterruptedException {
        CountDownLatch deliveryLatch = new CountDownLatch(1);
        
        AuthenticatedPerfectLinks realAuthLinks = new AuthenticatedPerfectLinks("127.0.0.1", 8889, 8888) {
            @Override
            public void alp2pReceive(AuthenticatedMessage authMessage) {
                super.alp2pReceive(authMessage);
                deliveryLatch.countDown();
            }
        };
        
        Message testMessage = new Message("payload-" + UUID.randomUUID(), "test-integration");
        
        String authString = realAuthLinks.authenticate(testMessage);
        AuthenticatedMessage authMessage = new AuthenticatedMessage(testMessage, authString);
        
        realAuthLinks.onMessageReceived(authMessage);
        
        boolean messageDelivered = deliveryLatch.await(1, TimeUnit.SECONDS);
        
        assertTrue(messageDelivered, "Message should be delivered within the timeout");
        assertEquals(1, realAuthLinks.getReceivedSize());
        assertEquals(authMessage.getMessageID(), realAuthLinks.getReceivedMessages().get(0).getMessageID());
    }
}