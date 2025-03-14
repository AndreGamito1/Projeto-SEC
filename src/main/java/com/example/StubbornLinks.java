package com.example;
import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.*;

/**
 * StubbornLinks implements a reliable UDP-based communication protocol that guarantees
 * message delivery through acknowledgments and retransmissions.
 *
 */
public class StubbornLinks {
    private final DatagramSocket socket;         
    private final InetAddress destAddress;       
    private final int destPort;                  
    private final ConcurrentHashMap<String, Boolean> ackReceived;  
    private final MessageCallback callback;      
    
    /**
     * Constructs a StubbornLinks instance to handle reliable message delivery.
     * 
     * @param destIP     Destination IP address where messages will be sent
     * @param destPort   Destination port number where messages will be sent
     * @param hostPort   Local port to bind the socket to for sending/receiving
     * @param callback   Callback interface to be invoked when messages are received
     * @throws Exception If socket creation or address resolution fails
     */
    public StubbornLinks(String destIP, int destPort, int hostPort, MessageCallback callback) throws Exception {
        this.socket = new DatagramSocket(hostPort);
        this.destAddress = InetAddress.getByName(destIP);
        this.destPort = destPort;
        this.ackReceived = new ConcurrentHashMap<>();
        this.callback = callback;
        
        receiveAcknowledgment();
    }
    
    /**
     * Sends a message with guaranteed delivery (Stubborn Point-to-Point Send).
     * This method serializes the message, then repeatedly sends it until an acknowledgment
     * is received, ensuring delivery even on unreliable networks.
     * 
     * @param message The Message object to be sent
     */
    public void sp2pSend(Message message) {
        try {
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
                        Logger.log(Logger.STUBBORN_LINKS, "Sent: " + message + " (Attempt " + attempts + ")");

                        Thread.sleep(5000); // Retransmit every 5 seconds
                    }
                    Logger.log(Logger.STUBBORN_LINKS, "Message successfully acknowledged: " + message.getMessageID());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Delivers a received message (Stubborn Point-to-Point Deliver).
     * This method deserializes the received byte array into an AuthenticatedMessage
     * and passes it to the registered callback.
     * 
     * @param messageBytes The serialized message bytes to be delivered
     */
    public void sp2pDeliver(byte[] messageBytes) {
        try {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(messageBytes);
            ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
            AuthenticatedMessage authMessage = (AuthenticatedMessage) objectInputStream.readObject();
            
            if (callback != null) {
                callback.onMessageReceived(authMessage); 
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Starts a background thread that continuously listens for incoming packets.
     * This method handles both acknowledgments for sent messages and new messages
     * that need to be delivered.
     * 
     * When a new message is received, it automatically sends an acknowledgment
     * back to the sender.
     */
    public void receiveAcknowledgment() {
        new Thread(() -> {
            try {
                byte[] buffer = new byte[1024];
                
                while (true) {
                    Arrays.fill(buffer, (byte) 0);
                    
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    
                    try {
                        String receivedData = new String(packet.getData(), 0, packet.getLength());
                        
                        if (receivedData.startsWith("ACK:")) {
                            String messageID = receivedData.substring(4);
                            if (ackReceived.containsKey(messageID)) {
                                ackReceived.put(messageID, true);
                                Logger.log(Logger.STUBBORN_LINKS, "Acknowledged: " + messageID);
                            }
                        } else {
                            try {
                                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
                                ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
                                Message message = (Message) objectInputStream.readObject();
                                
                                sp2pDeliver(packet.getData());
                                
                                String messageID = message.getMessageID();
                                String ackMessage = "ACK:" + messageID;
                                byte[] ackBuffer = ackMessage.getBytes();
                                DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length, packet.getAddress(), packet.getPort());
                                socket.send(ackPacket);
                                Logger.log(Logger.STUBBORN_LINKS,  "Sent ACK: " + messageID);
                            } catch (StreamCorruptedException | ClassNotFoundException | OptionalDataException e) {
                                Logger.log(Logger.STUBBORN_LINKS, "Received unreadable data, ignoring: " + e.getMessage());
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