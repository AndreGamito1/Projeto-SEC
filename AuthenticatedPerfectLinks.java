import java.util.ArrayList;
import java.util.List;

interface MessageCallback {
    void onMessageReceived(AuthenticatedMessage authMessage);
}

public class AuthenticatedPerfectLinks implements MessageCallback {
    private List<AuthenticatedMessage> delivered;
    private StubbornLinks stubbornLink;
    
    public AuthenticatedPerfectLinks(String destIP, int destPort, int hostPort) {
        try {
            this.stubbornLink = new StubbornLinks(destIP, destPort, hostPort, this);
             this.delivered = new ArrayList<>();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void alp2pSend(String dest, Message message) {
        String authString = authenticate(message);
        AuthenticatedMessage authMessage = new AuthenticatedMessage(message, authString);
        stubbornLink.sp2pSend(authMessage);
    }
    
    public void alp2pDeliver(AuthenticatedMessage authMessage) {
        delivered.add(authMessage);
        System.out.println("[ALP2P] Received Authenticated Message with ID: " + authMessage.getMessageID());
        System.out.println("[ALP2P] Auth String: " + authMessage.getAuthString());
        System.out.println("[ALP2P] Original Message: " + authMessage.toString()); // Print the original message
    }
    
    /*TODO Make an actual authenticated method */
    public String authenticate(Message message) {
        return message.getMessageID();  
    }
    
    public int getDeliveredSize() {
        return delivered.size();
    }

    public List<AuthenticatedMessage> getDeliveredMessages() {
        return delivered;
    }


    @Override
    public void onMessageReceived(AuthenticatedMessage authMessage) {
        alp2pDeliver(authMessage);
    }
}