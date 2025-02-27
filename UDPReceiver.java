
import java.net.*;

public class UDPReceiver {
    private DatagramSocket socket;

    public UDPReceiver(int listenPort) throws Exception {
        this.socket = new DatagramSocket(listenPort);
    }

    public void listen() {
        new Thread(() -> {
            try {
                while (true) {
                    byte[] buffer = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String receivedData = new String(packet.getData(), 0, packet.getLength());
                    String[] parts = receivedData.split(":", 2);
                    String messageID = parts[0];
                    String message = parts[1];

                    System.out.println("[Receiver] Received: " + message);

                    // Send acknowledgment
                    String ackMessage = "ACK:" + messageID;
                    byte[] ackBuffer = ackMessage.getBytes();
                    DatagramPacket ackPacket = new DatagramPacket(
                        ackBuffer, ackBuffer.length, packet.getAddress(), packet.getPort()
                    );
                    socket.send(ackPacket);
                    System.out.println("[Receiver] Sent ACK for: " + message);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
