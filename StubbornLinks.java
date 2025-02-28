import java.io.*;
import java.net.*;
import java.util.concurrent.*;

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

    public void sp2pSend(Message message) {
        try {
            // Serialize the Message object to a byte array
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(message);
            byte[] messageBytes = byteArrayOutputStream.toByteArray();

            String messageID = String.valueOf(message.hashCode());
            ackReceived.put(messageID, false);

            new Thread(() -> {
                try {
                    DatagramPacket packet = new DatagramPacket(messageBytes, messageBytes.length, destAddress, destPort);

                    while (!ackReceived.get(messageID)) {
                        socket.send(packet);
                        System.out.println("[SP2P] Sent: " + message);
                        Thread.sleep(1000); // Retransmit every second
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sp2pDeliver(byte[] messageBytes) {
        try {
            // Deserialize the byte array back to a Message object
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(messageBytes);
            ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
            Message message = (Message) objectInputStream.readObject();
            System.out.println("[SP2P] Delivered: " + message);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
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
                        // Deserialize the byte array back to a Message object
                        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
                        ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
                        Message message = (Message) objectInputStream.readObject();

                        // Deliver the message
                        sp2pDeliver(packet.getData());

                        // Send acknowledgment
                        String messageID = String.valueOf(message.hashCode());
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