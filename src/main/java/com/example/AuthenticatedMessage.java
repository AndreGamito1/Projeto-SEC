package com.example;

public class AuthenticatedMessage extends Message {
    private String authString;  // Add an authentication string

    // Constructor that takes a Message object and an authString
    public AuthenticatedMessage(Message message, String authString) {
        super(message.getPayload(), message.getCommand());  // Copy payload and command from the existing message
        this.authString = authString;
    }

    public String getAuthString() {
        return authString;
    }

    public void setAuthString(String authString) {
        this.authString = authString;
    }

    @Override
    public String toString() {
        // Include authString in the string representation
        return super.toString() + ", authString=" + authString;
    }
}
