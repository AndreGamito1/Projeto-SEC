package com.example;
import java.util.ArrayList;
import java.util.List;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

interface MessageCallback {
    void onMessageReceived(AuthenticatedMessage authMessage);
}

public class AuthenticatedPerfectLinks implements MessageCallback {
    private List<AuthenticatedMessage> received;
    private StubbornLinks stubbornLink;
    
    public AuthenticatedPerfectLinks(String destIP, int destPort, int hostPort) {
        try {
            this.stubbornLink = new StubbornLinks(destIP, destPort, hostPort, this);
            this.received = new ArrayList<>();
            Logger.log(Logger.AUTH_LINKS, "AuthenticatedPerfectLinks initialized");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void alp2pSend(String dest, Message message) {
        String authString = authenticate(message);
        AuthenticatedMessage authMessage = new AuthenticatedMessage(message, authString);
        stubbornLink.sp2pSend(authMessage);
    }
    
    public void alp2pReceive(AuthenticatedMessage authMessage) {
        if (verifyauth(authMessage)) {
            Logger.log(Logger.AUTH_LINKS, "Message verified:" +authMessage.getMessageID());
            received.add(authMessage);
            Logger.log(Logger.AUTH_LINKS, "Received Authenticated Message" + authMessage.toString());
         
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
        for (AuthenticatedMessage receivedMessage : received) {
            if (receivedMessage.getMessageID().equals(authMessage.getMessageID())) {
                return false; 
            }
        }
        // Check if the auth string is correct (for now, just a simple check)
        String expectedAuthString = authenticate(authMessage);
        return authMessage.getAuthString().equals(expectedAuthString);
    }

    public int getReceivedSize() {
        return received.size();
    }

    public List<AuthenticatedMessage> getReceivedMessages() {
        return received;
    }


    @Override
    public void onMessageReceived(AuthenticatedMessage authMessage) {
        alp2pReceive(authMessage);
    }
}