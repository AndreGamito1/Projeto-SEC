package com.depchain.networking;

public class AuthenticatedMessage extends Message {
    private String authString;  
    private String messageID;  
    
    /**
     * Constructor that takes a Message object and an authString.
     * Generates a unique messageID.
     * 
     * @param message The original message
     * @param authString The authentication string
     */
    public AuthenticatedMessage(Message message, String authString) {
        super(message.getPayload(), message.getCommand());  
        this.authString = authString;
        this.messageID = generateMessageID();
    }
    
    /**
     * Constructor that takes a Message object, an authString, and a messageID.
     * Used when creating a new authenticated message with a specific messageID.
     * 
     * @param message The original message
     * @param authString The authentication string
     * @param messageID The message ID to use
     */
    public AuthenticatedMessage(Message message, String authString, String messageID) {
        super(message.getPayload(), message.getCommand()); 
        this.authString = authString;
        this.messageID = messageID;
    }
    
    /**
     * Generates a unique message ID based on the payload, command, and a timestamp.
     * 
     * @return A unique message ID string
     */
    private String generateMessageID() {
        // Combine payload, command, and current timestamp to create a unique ID
        String idBase = getPayload() + getCommand() + System.currentTimeMillis();
        return String.valueOf(idBase.hashCode());
    }
    
    /**
     * Gets the authentication string.
     * 
     * @return The authentication string
     */
    public String getAuthString() {
        return authString;
    }
    
    /**
     * Sets the authentication string.
     * 
     * @param authString The authentication string to set
     */
    public void setAuthString(String authString) {
        this.authString = authString;
    }
    
    /**
     * Gets the message ID.
     * 
     * @return The message ID
     */
    public String getMessageID() {
        return messageID;
    }
    
    /**
     * Sets the message ID.
     * 
     * @param messageID The message ID to set
     */
    public void setMessageID(String messageID) {
        this.messageID = messageID;
    }
    
    @Override
    public String toString() {
        return super.toString() + ", authString=" + authString + ", messageID=" + messageID;
    }
}