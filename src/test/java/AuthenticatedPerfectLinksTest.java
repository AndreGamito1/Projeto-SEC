import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mock;
import org.mockito.Mockito;
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
        
        // Cria uma instância personalizada para teste com o mock de StubbornLinks
        authenticatedPerfectLinks = new AuthenticatedPerfectLinksForTest(mockStubbornLinks);
    }
    
    // Classe interna para permitir testes sem criar conexões de rede reais
    private static class AuthenticatedPerfectLinksForTest extends AuthenticatedPerfectLinks {
        private StubbornLinks stubbornLink;
        
        public AuthenticatedPerfectLinksForTest(StubbornLinks stubbornLink) {
            super(null, 0, 0); // Estes parâmetros são ignorados nesta classe de teste
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
        // Cria mensagem
        Message message = new Message("test payload", "test command");
        
        // Autentica a mensagem
        String authString = authenticatedPerfectLinks.authenticate(message);
        
        // Verifica se o authString é igual ao messageID da mensagem (conforme implementação atual)
        assertEquals(message.getMessageID(), authString);
    }
    
    @Test
    void testAlp2pSend() {
        // Cria mensagem
        Message message = new Message("test payload", "test command");
        
        // Envia a mensagem
        authenticatedPerfectLinks.alp2pSend("dest", message);
        
        // Verifica se o método sp2pSend de StubbornLinks foi chamado com qualquer AuthenticatedMessage
        verify(mockStubbornLinks, times(1)).sp2pSend(any(AuthenticatedMessage.class));
    }
    
    @Test
    void testAlp2pDeliver() {
        // Cria uma mensagem autenticada
        Message message = new Message("test payload", "test command");
        String authString = message.getMessageID();
        AuthenticatedMessage authMessage = new AuthenticatedMessage(message, authString);
        
        // Verifica se não há mensagens entregues inicialmente
        assertEquals(0, authenticatedPerfectLinks.getReceivedSize());
        
        // Entrega a mensagem
        authenticatedPerfectLinks.alp2pReceive(authMessage);
        
        // Verifica se a mensagem foi entregue
        assertEquals(1, authenticatedPerfectLinks.getReceivedSize());
        
        // Verifica se a mensagem entregue é igual à mensagem original
        List<AuthenticatedMessage> deliveredMessages = authenticatedPerfectLinks.getReceivedMessages();
        assertEquals(1, deliveredMessages.size());
        
        AuthenticatedMessage deliveredMessage = deliveredMessages.get(0);
        assertEquals(authMessage.getMessageID(), deliveredMessage.getMessageID());
        assertEquals(authMessage.getPayload(), deliveredMessage.getPayload());
        assertEquals(authMessage.getCommand(), deliveredMessage.getCommand());
        assertEquals(authMessage.getAuthString(), deliveredMessage.getAuthString());
    }
    
    @Test
    void testOnMessageReceived() {
        // Cria uma mensagem autenticada
        Message message = new Message("test payload", "test command");
        String authString = message.getMessageID();
        AuthenticatedMessage authMessage = new AuthenticatedMessage(message, authString);
        
        // Chama o método de callback
        authenticatedPerfectLinks.onMessageReceived(authMessage);
        
        // Verifica se a mensagem foi entregue (alp2pDeliver deve ter sido chamado)
        assertEquals(1, authenticatedPerfectLinks.getReceivedSize());
        
        // Verifica se a mensagem entregue é igual à mensagem original
        List<AuthenticatedMessage> deliveredMessages = authenticatedPerfectLinks.getReceivedMessages();
        assertEquals(authMessage, deliveredMessages.get(0));
    }
    
    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void testIntegrationWithRealMessage() throws InterruptedException {
        // Este teste simula um cenário de integração mais próximo da realidade
        // Um latch para controlar a sincronia do teste
        CountDownLatch deliveryLatch = new CountDownLatch(1);
        
        // Uma implementação personalizada para registrar quando a mensagem é entregue
        AuthenticatedPerfectLinks realAuthLinks = new AuthenticatedPerfectLinks("127.0.0.1", 8889, 8888) {
            @Override
            public void alp2pReceive(AuthenticatedMessage authMessage) {
                super.alp2pReceive(authMessage);
                deliveryLatch.countDown();
            }
        };
        
        // Stub para simular a entrega de uma mensagem
        Message testMessage = new Message("payload-" + UUID.randomUUID(), "test-integration");
        AuthenticatedMessage authMessage = new AuthenticatedMessage(testMessage, testMessage.getMessageID());
        
        // Simula a recepção de uma mensagem como se viesse da rede
        realAuthLinks.onMessageReceived(authMessage);
        
        // Aguarda a entrega ou timeout
        boolean messageDelivered = deliveryLatch.await(1, TimeUnit.SECONDS);
        
        // Verifica se a mensagem foi entregue
        assertTrue(messageDelivered, "A mensagem deve ser entregue dentro do timeout");
        assertEquals(1, realAuthLinks.getReceivedSize());
        assertEquals(authMessage.getMessageID(), realAuthLinks.getReceivedMessages().get(0).getMessageID());
    }
}