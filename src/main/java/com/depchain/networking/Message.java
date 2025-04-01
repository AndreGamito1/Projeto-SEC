package com.depchain.networking;
import java.io.Serializable;
import java.util.UUID;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    private String payload;
    private String command;
    private String authString;
    private String messageID;
    private String secretKey;


    public Message(String payload, String command, String key) {
        this.payload = payload;
        this.command = command;
        this.secretKey = key;
        this.messageID = UUID.randomUUID().toString();
    }

    public String getPayload() {
        return payload;
    }
    
    public String getAesKey() {
        return secretKey;
    }
    
    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getMessageID() {
        return messageID;
    }
    
    public boolean hasCommand() {
        return command != null && !command.isEmpty();
    }
    
    @Override
    public String toString() {
        if (command == null || command.isEmpty()) {
            return "Message[payload=" + payload + ", messageID=" + messageID + "]";
        } else {
            return "Message[payload=" + payload + ", command=" + command + ", messageID=" + messageID + "]";
        }
    }
}