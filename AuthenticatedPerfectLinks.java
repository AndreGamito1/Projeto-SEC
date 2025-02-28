import java.io.*;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

public class AuthenticatedPerfectLinks {
    private final StubbornLinks stubbornLinks;
    private final Set<String> delivered;

    public AuthenticatedPerfectLinks(String destIP, int destPort, int hostPort) throws Exception {
        this.stubbornLinks = new StubbornLinks(destIP, destPort, hostPort);
        this.delivered = new HashSet<>();
    }

    // Send Message object
    public void alp2pSend(Message message) {
        try {
            // Serialize the message object
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            ObjectOutputStream objectStream = new ObjectOutputStream(byteStream);
            objectStream.writeObject(message);
            byte[] serializedMessage = byteStream.toByteArray();

            // Generate authentication tag for the serialized message
            String authTag = authenticate(serializedMessage);

            // Append the authentication tag to the message (authentication done on the serialized byte array)
            Message fullMessage = new Message(new String(serializedMessage), authTag);

            // Send using stubborn links
            stubbornLinks.sp2pSend(fullMessage);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Receive and deliver Message object
    public void alp2pDeliver(String src, byte[] serializedMessage) {
        try {
            // Deserialize the message object
            ByteArrayInputStream byteStream = new ByteArrayInputStream(serializedMessage);
            ObjectInputStream objectStream = new ObjectInputStream(byteStream);
            Message message = (Message) objectStream.readObject();

            System.out.println("[ALP2P] Delivered from " + src + ": " + message);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void receiveMessages() {
        new Thread(() -> {
            stubbornLinks.receiveAcknowledgment(); // Listens for incoming messages
        }).start();
    }

    private String authenticate(byte[] message) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(message);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Authentication error", e);
        }
    }

    private boolean verifyAuth(byte[] message, String receivedAuthTag) {
        return authenticate(message).equals(receivedAuthTag);
    }
}
