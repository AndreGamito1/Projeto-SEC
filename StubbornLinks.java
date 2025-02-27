
import java.net.*;
import java.util.concurrent.*;

public class StubbornLinks {
    private DatagramSocket socket;
    private InetAddress destAddress;
    private int destPort;
    private final ConcurrentHashMap<String, Boolean> ackReceived; // Track acknowledgments

    public StubbornLinks(String destIP, int destPort) throws Exception {
        this.socket = new DatagramSocket(); // Random available port
        this.destAddress = InetAddress.getByName(destIP);
        this.destPort = destPort;
        this.ackReceived = new ConcurrentHashMap<>();
    }

    public void sp2pSend(String message) {
        String messageID = String.valueOf(message.hashCode()); // Unique ID for message tracking
        ackReceived.put(messageID, false);
        
        Runnable sendTask = () -> {
            while (!ackReceived.get(messageID)) { // Keep sending until acknowledged
                try {
                    String fullMessage = messageID + ":" + message;
                    byte[] buffer = fullMessage.getBytes();
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, destAddress, destPort);
                    socket.send(packet);
                    System.out.println("[SP2P] Sent: " + message);
                    
                    Thread.sleep(1000); // Wait before retransmission
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        new Thread(sendTask).start();
    }

    public void sp2pDeliver(String message) {
        System.out.println("[SP2P] Delivered: " + message);
    }

    public void receiveAcknowledgment() {
        new Thread(() -> {
            try {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                
                String receivedData = new String(packet.getData(), 0, packet.getLength());
                if (receivedData.startsWith("ACK:")) {
                    String messageID = receivedData.substring(4);
                    ackReceived.put(messageID, true);
                    System.out.println("[SP2P] Acknowledged: " + messageID);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
 
    

