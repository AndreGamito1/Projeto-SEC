import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
        if (verifyauth(authMessage)) {
            System.out.println("[ALP2P] Message verified: " + authMessage.getMessageID());
            delivered.add(authMessage);
            System.out.println("[ALP2P] Received Authenticated Message with ID: " + authMessage.getMessageID());
            System.out.println("[ALP2P] Auth String: " + authMessage.getAuthString());
            System.out.println("[ALP2P] Original Message: " + authMessage.toString()); // Print the original message
        }
    }
    /*TODO: Alter so it uses the received key */
   public String authenticate(Message message) {
        try {
            String content = message.getPayload();
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes());

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }

            return hexString.toString(); 
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null; 
        }
    }
    

    public boolean verifyauth(AuthenticatedMessage authMessage) {
        for (AuthenticatedMessage deliveredMessage : delivered) {
            if (deliveredMessage.getMessageID().equals(authMessage.getMessageID())) {
                return false; 
            }
        }
        // Check if the auth string is correct (for now, just a simple check)
        String expectedAuthString = authenticate(authMessage);
        return authMessage.getAuthString().equals(expectedAuthString);
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