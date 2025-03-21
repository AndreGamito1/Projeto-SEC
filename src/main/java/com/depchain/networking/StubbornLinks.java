package com.depchain.networking;
import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.*;

import com.depchain.utils.Logger;

/**
 * StubbornLinks implements a reliable UDP-based communication protocol that guarantees
 * message delivery through acknowledgments and retransmissions.
 * It only requires the messageId for acknowledgment purposes.
 */
public class StubbornLinks {
    private final DatagramSocket socket;         
    private final InetAddress destAddress;       
    private final int destPort;                  
    private final ConcurrentHashMap<String, Boolean> ackReceived;  
    private final MessageCallback callback;
    private static final int MAX_PACKET_SIZE = 16384; // Large buffer for encrypted payloads
    
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
        
        startReceiver();
    }
    
    /**
     * Sends a message with guaranteed delivery (Stubborn Point-to-Point Send).
     * This method serializes the message, then repeatedly sends it until an acknowledgment
     * is received, ensuring delivery even on unreliable networks.
     * 
     * @param message The AuthenticatedMessage object to be sent
     */
    public void sp2pSend(AuthenticatedMessage message) {
        try {
            // Extract message ID before serialization
            String messageID = message.getMessageID();
            ackReceived.put(messageID, false);
            
            // Serialize the message
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(message);
            objectOutputStream.flush();
            objectOutputStream.close();
            
            byte[] messageBytes = byteArrayOutputStream.toByteArray();
            
            if (messageBytes.length > MAX_PACKET_SIZE) {
                Logger.log(Logger.STUBBORN_LINKS, "Warning: Message size " + messageBytes.length + 
                          " exceeds recommended UDP packet size of " + MAX_PACKET_SIZE);
            }
            
            // Start a separate thread for retransmission
            new Thread(() -> {
                try {
                    DatagramPacket packet = new DatagramPacket(messageBytes, messageBytes.length, destAddress, destPort);
                    int attempts = 0;
                    int maxAttempts = 20; // Limit maximum attempts to avoid infinite retries
                    
                    while (!ackReceived.getOrDefault(messageID, false) && attempts < maxAttempts) {
                        socket.send(packet);
                        attempts++;
                        Logger.log(Logger.STUBBORN_LINKS, "Sent message ID: " + messageID + " (Attempt " + attempts + 
                                  ", Size: " + messageBytes.length + " bytes)");

                        // Wait for acknowledgment
                        Thread.sleep(5000); // Retransmit every 5 seconds
                    }
                    
                    if (ackReceived.getOrDefault(messageID, false)) {
                        Logger.log(Logger.STUBBORN_LINKS, "Message successfully acknowledged: " + messageID);
                    } else {
                        Logger.log(Logger.STUBBORN_LINKS, "Failed to get acknowledgment for message: " + messageID + 
                                  " after " + maxAttempts + " attempts");
                    }
                    
                    // Clean up
                    ackReceived.remove(messageID);
                } catch (Exception e) {
                    Logger.log(Logger.STUBBORN_LINKS, "Error sending message: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();
        } catch (IOException e) {
            Logger.log(Logger.STUBBORN_LINKS, "Error serializing message: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Delivers a received message to the callback.
     * 
     * @param data The serialized message bytes to be delivered
     */
    private void sp2pDeliver(byte[] data) {
        try {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(data);
            ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
            AuthenticatedMessage authMessage = (AuthenticatedMessage) objectInputStream.readObject();
            objectInputStream.close();
            
            Logger.log(Logger.STUBBORN_LINKS, "Successfully deserialized message: " + authMessage.getMessageID());
            
            if (callback != null) {
                callback.onMessageReceived(authMessage);
                Logger.log(Logger.STUBBORN_LINKS, "Message delivered to callback: " + authMessage.getMessageID());
            }
        } catch (Exception e) {
            Logger.log(Logger.STUBBORN_LINKS, "Error delivering message: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Starts a background thread that continuously listens for incoming packets.
     * This method handles both acknowledgments for sent messages and new messages
     * that need to be delivered.
     */
    private void startReceiver() {
        new Thread(() -> {
            try {
                byte[] buffer = new byte[MAX_PACKET_SIZE];
                
                Logger.log(Logger.STUBBORN_LINKS, "Receiver started, waiting for messages...");
                
                while (true) {
                    Arrays.fill(buffer, (byte) 0);
                    
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    
                    // Extract the actual data with exact length
                    byte[] receivedData = Arrays.copyOf(packet.getData(), packet.getLength());
                    
                    try {
                        // Check if this is a text-based ACK message
                        if (packet.getLength() < 100) { // ACK messages are short
                            String textData = new String(receivedData);
                            
                            if (textData.startsWith("ACK:")) {
                                String messageID = textData.substring(4).trim();
                                if (ackReceived.containsKey(messageID)) {
                                    ackReceived.put(messageID, true);
                                    Logger.log(Logger.STUBBORN_LINKS, "Received ACK for message: " + messageID);
                                } else {
                                    Logger.log(Logger.STUBBORN_LINKS, "Received ACK for unknown message: " + messageID);
                                }
                                continue; // Skip further processing for ACK messages
                            }
                        }
                        
                        // Try to deserialize the message to get its ID
                        String messageID = null;
                        try {
                            ByteArrayInputStream bis = new ByteArrayInputStream(receivedData);
                            ObjectInputStream ois = new ObjectInputStream(bis);
                            Object obj = ois.readObject();
                            
                            if (obj instanceof AuthenticatedMessage) {
                                AuthenticatedMessage message = (AuthenticatedMessage) obj;
                                messageID = message.getMessageID();
                                ois.close();
                            }
                        } catch (Exception e) {
                            Logger.log(Logger.STUBBORN_LINKS, "Failed to deserialize message: " + e.getMessage());
                        }
                        
                        if (messageID != null) {
                            // Send acknowledgment
                            String ackMessage = "ACK:" + messageID;
                            byte[] ackBuffer = ackMessage.getBytes();
                            DatagramPacket ackPacket = new DatagramPacket(
                                ackBuffer, ackBuffer.length, packet.getAddress(), packet.getPort()
                            );
                            socket.send(ackPacket);
                            Logger.log(Logger.STUBBORN_LINKS, "Sent ACK for message: " + messageID);
                            
                            // Process the message in a separate thread to avoid blocking
                            final byte[] messageData = receivedData;
                            new Thread(() -> sp2pDeliver(messageData)).start();
                        } else {
                            Logger.log(Logger.STUBBORN_LINKS, "Received unidentifiable data, length: " + receivedData.length);
                        }
                    } catch (Exception e) {
                        Logger.log(Logger.STUBBORN_LINKS, "Error processing packet: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                Logger.log(Logger.STUBBORN_LINKS, "Fatal error in receiver: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }
}