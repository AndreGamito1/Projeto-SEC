import java.io.Serializable;
import java.util.UUID;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    private String payload;
    private String command;
    private String messageID;

    // Constructor for backward compatibility
    public Message(String payload) {
        this.payload = payload;
        this.command = null;  // No command
        this.messageID = UUID.randomUUID().toString();
    }
    
    // New constructor with command
    public Message(String payload, String command) {
        this.payload = payload;
        this.command = command;
        this.messageID = UUID.randomUUID().toString();
    }

    public String getPayload() {
        return payload;
    }
    
    public String getCommand() {
        return command;
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