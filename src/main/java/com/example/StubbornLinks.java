package com.example;
import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.*;

/*TODO: Write function description for each function */

public class StubbornLinks {
    private final DatagramSocket socket;
    private final InetAddress destAddress;
    private final int destPort;
    private final ConcurrentHashMap<String, Boolean> ackReceived;
    private final MessageCallback callback;
    
    public StubbornLinks(String destIP, int destPort, int hostPort, MessageCallback callback) throws Exception {
        this.socket = new DatagramSocket(hostPort);
        this.destAddress = InetAddress.getByName(destIP);
        this.destPort = destPort;
        this.ackReceived = new ConcurrentHashMap<>();
        this.callback = callback;
        
        // Start the thread to listen for incoming packets
        receiveAcknowledgment();
    }
    
    public void sp2pSend(Message message) {
        try {
            // Serialize the Message object to a byte array
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(message);
            byte[] messageBytes = byteArrayOutputStream.toByteArray();
            
            String messageID = message.getMessageID();
            ackReceived.put(messageID, false);
            
            new Thread(() -> {
                try {
                    DatagramPacket packet = new DatagramPacket(messageBytes, messageBytes.length, destAddress, destPort);
                    int attempts = 0;
                    
                    while (!ackReceived.getOrDefault(messageID, false)) {
                        socket.send(packet);
                        attempts++;
                        System.out.println("[STUBBORN] Sent: " + message + " (Attempt " + attempts + ")");
                        Thread.sleep(5000); // Retransmit every 5 seconds
                    }
                    
                    System.out.println("[STUBBORN] Message successfully acknowledged: " + message.getMessageID());
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
            // Deserialize the byte array back to an AuthenticatedMessage object
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(messageBytes);
            ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
            AuthenticatedMessage authMessage = (AuthenticatedMessage) objectInputStream.readObject();
            
            // Trigger the callback
            if (callback != null) {
                callback.onMessageReceived(authMessage); 
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
    
    public void receiveAcknowledgment() {
        new Thread(() -> {
            try {
                byte[] buffer = new byte[1024];
                
                while (true) {
                    // Reset the buffer for each new packet
                    Arrays.fill(buffer, (byte) 0);
                    
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    
                    try {
                        String receivedData = new String(packet.getData(), 0, packet.getLength());
                        
                        if (receivedData.startsWith("ACK:")) {
                            String messageID = receivedData.substring(4);
                            if (ackReceived.containsKey(messageID)) {
                                ackReceived.put(messageID, true);
                                System.out.println("[STUBBORN] Acknowledged: " + messageID);
                            }
                        } else {
                            try {
                                // Try to deserialize as a Message object
                                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
                                ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
                                Message message = (Message) objectInputStream.readObject();
                                
                                // Deliver the message
                                sp2pDeliver(packet.getData());
                                
                                // Send acknowledgment using the message's ID
                                String messageID = message.getMessageID();
                                String ackMessage = "ACK:" + messageID;
                                byte[] ackBuffer = ackMessage.getBytes();
                                DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length, packet.getAddress(), packet.getPort());
                                socket.send(ackPacket);
                                System.out.println("[STUBBORN] Sent ACK: " + messageID);
                            } catch (StreamCorruptedException | ClassNotFoundException | OptionalDataException e) {
                                System.out.println("[STUBBORN] Received unreadable data, ignoring: " + e.getMessage());
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("[STUBBORN] Error processing packet: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
