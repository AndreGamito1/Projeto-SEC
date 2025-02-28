import java.io.Serializable;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    private String payload;
    private String command;

    // Constructor for backward compatibility
    public Message(String payload) {
        this.payload = payload;
        this.command = null;  // No command
    }
    
    // New constructor with command
    public Message(String payload, String command) {
        this.payload = payload;
        this.command = command;
    }
    
    public String getPayload() {
        return payload;
    }
    
    public String getCommand() {
        return command;
    }
    
    public boolean hasCommand() {
        return command != null && !command.isEmpty();
    }
    
    @Override
    public String toString() {
        if (command == null || command.isEmpty()) {
            return "Message[payload=" + payload + "]";
        } else {
            return "Message[payload=" + payload + ", command=" + command + "]";
        }
    }
}