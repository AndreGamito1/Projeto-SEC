import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;

public class StubbornLinks {
    private final DatagramSocket socket;
    private final InetAddress destAddress;
    private final int destPort;
    private final ConcurrentHashMap<String, Boolean> ackReceived;

    public StubbornLinks(String destIP, int destPort, int hostPort) throws Exception {
        this.socket = new DatagramSocket(hostPort);
        this.destAddress = InetAddress.getByName(destIP);
        this.destPort = destPort;
        this.ackReceived = new ConcurrentHashMap<>();
    }

    public void sp2pSend(String message) {
        String messageID = String.valueOf(message.hashCode());
        ackReceived.put(messageID, false);

        new Thread(() -> {
            try {
                String fullMessage = messageID + ":" + message;
                byte[] buffer = fullMessage.getBytes();
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, destAddress, destPort);

                while (!ackReceived.get(messageID)) { 
                    socket.send(packet);
                    System.out.println("[SP2P] Sent: " + message);
                    Thread.sleep(1000); // Retransmit every second
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void sp2pDeliver(String message) {
        System.out.println("[SP2P] Delivered: " + message);
    }

    public void receiveAcknowledgment() {
        new Thread(() -> {
            try {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                while (true) {
                    socket.receive(packet);
                    String receivedData = new String(packet.getData(), 0, packet.getLength());

                    if (receivedData.startsWith("ACK:")) {
                        String messageID = receivedData.substring(4);
                        ackReceived.put(messageID, true);
                        System.out.println("[SP2P] Acknowledged: " + messageID);
                    } else {
                        // Deliver the message
                        sp2pDeliver(receivedData);

                        // Send acknowledgment
                        String messageID = receivedData.split(":")[0];
                        String ackMessage = "ACK:" + messageID;
                        byte[] ackBuffer = ackMessage.getBytes();
                        DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length, packet.getAddress(), packet.getPort());
                        socket.send(ackPacket);
                        System.out.println("[SP2P] Sent ACK: " + messageID);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
