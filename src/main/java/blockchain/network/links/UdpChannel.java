package src.main.java.blockchain.network.links;

import src.main.java.blockchain.network.Message;
import src.main.java.blockchain.network.MessageSerializer;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * UdpChannel handles the low-level UDP socket communication.
 * It provides methods for sending and receiving datagrams.
 */
public class UdpChannel {
    private static final Logger LOGGER = Logger.getLogger(UdpChannel.class.getName());
    private static final int MAX_PACKET_SIZE = 65507; // Max UDP packet size
    private static final int SOCKET_TIMEOUT = 1000; // Socket timeout in ms
    
    private final int localPort;
    private final int processId;
    private DatagramSocket socket;
    private final AtomicBoolean running;
    private final ExecutorService receiverThread;
    private final ConcurrentHashMap<Integer, InetSocketAddress> processAddresses;
    private MessageReceiver messageReceiver;
    
    /**
     * Interface for receiving messages from the UDP channel
     */
    public interface MessageReceiver {
        void onMessageReceived(Message message, InetSocketAddress sender);
        void onError(Exception e);
    }
    
    /**
     * Creates a UDP channel bound to the specified local port
     * 
     * @param processId The ID of the local process
     * @param localPort The port to bind to
     * @throws SocketException If the socket cannot be created or bound
     */
    public UdpChannel(int processId, int localPort) throws SocketException {
        this.processId = processId;
        this.localPort = localPort;
        this.socket = new DatagramSocket(localPort);
        this.socket.setSoTimeout(SOCKET_TIMEOUT);
        this.running = new AtomicBoolean(false);
        this.receiverThread = Executors.newSingleThreadExecutor();
        this.processAddresses = new ConcurrentHashMap<>();
        
        LOGGER.info("UDP channel initialized on port " + localPort + " for process " + processId);
    }
    
    /**
     * Registers a process with its network address
     * 
     * @param processId The ID of the remote process
     * @param hostname The hostname or IP address of the remote process
     * @param port The port of the remote process
     */
    public void registerProcess(int processId, String hostname, int port) {
        try {
            InetAddress address = InetAddress.getByName(hostname);
            InetSocketAddress socketAddress = new InetSocketAddress(address, port);
            processAddresses.put(processId, socketAddress);
            LOGGER.info("Registered process " + processId + " at " + hostname + ":" + port);
        } catch (UnknownHostException e) {
            LOGGER.log(Level.SEVERE, "Failed to register process " + processId, e);
        }
    }
    
    /**
     * Sets the message receiver callback
     * 
     * @param receiver The receiver implementation
     */
    public void setMessageReceiver(MessageReceiver receiver) {
        this.messageReceiver = receiver;
    }
    
    /**
     * Starts the receiving thread
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            receiverThread.submit(this::receiveLoop);
            LOGGER.info("UDP channel started on port " + localPort);
        }
    }
    
    /**
     * Stops the receiving thread and closes the socket
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            receiverThread.shutdown();
            socket.close();
            LOGGER.info("UDP channel stopped");
        }
    }
    
    /**
     * Sends a message to a specific process
     * 
     * @param message The message to send
     * @param targetProcessId The ID of the target process
     * @throws IOException If the message cannot be sent
     */
    public void sendMessage(Message message, int targetProcessId) throws IOException {
        InetSocketAddress targetAddress = processAddresses.get(targetProcessId);
        if (targetAddress == null) {
            throw new IOException("Unknown process ID: " + targetProcessId);
        }
        
        sendMessage(message, targetAddress);
    }
    
    /**
     * Sends a message to a specific socket address
     * 
     * @param message The message to send
     * @param targetAddress The socket address to send to
     * @throws IOException If the message cannot be sent
     */
    public void sendMessage(Message message, InetSocketAddress targetAddress) throws IOException {
        byte[] serializedMessage = MessageSerializer.serialize(message);
        
        if (serializedMessage.length > MAX_PACKET_SIZE) {
            throw new IOException("Message too large for UDP packet: " + serializedMessage.length + " bytes");
        }
        
        DatagramPacket packet = new DatagramPacket(
                serializedMessage, 
                serializedMessage.length,
                targetAddress.getAddress(),
                targetAddress.getPort());
        
        socket.send(packet);
        LOGGER.fine("Sent message " + message.getMessageId() + " to " + targetAddress);
    }
    
    /**
     * The main receive loop that runs in a separate thread
     */
    private void receiveLoop() {
        byte[] buffer = new byte[MAX_PACKET_SIZE];
        
        while (running.get()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                
                // This will block until a packet is received or timeout occurs
                socket.receive(packet);
                
                // Create a copy of the received data to avoid buffer reuse issues
                byte[] data = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());
                
                // Get the sender's address
                InetSocketAddress sender = new InetSocketAddress(
                        packet.getAddress(),
                        packet.getPort());
                
                // Process the received message in the same thread for now
                // In a production system, this could be delegated to a thread pool
                processReceivedData(data, sender);
                
            } catch (SocketTimeoutException e) {
                // This is expected, just continue the loop
            } catch (IOException e) {
                if (running.get()) {
                    LOGGER.log(Level.WARNING, "Error receiving UDP packet", e);
                    if (messageReceiver != null) {
                        messageReceiver.onError(e);
                    }
                }
            }
        }
    }
    
    /**
     * Processes received data by deserializing it into a Message
     * 
     * @param data The received data
     * @param sender The sender's address
     */
    private void processReceivedData(byte[] data, InetSocketAddress sender) {
        try {
            Message message = MessageSerializer.deserialize(data);
            LOGGER.fine("Received message " + message.getMessageId() + " from " + sender);
            
            if (messageReceiver != null) {
                messageReceiver.onMessageReceived(message, sender);
            }
        } catch (IOException | ClassNotFoundException e) {
            LOGGER.log(Level.WARNING, "Failed to deserialize message from " + sender, e);
            if (messageReceiver != null) {
                messageReceiver.onError(e);
            }
        }
    }
    
    /**
     * Gets the local port this channel is bound to
     * 
     * @return The local port
     */
    public int getLocalPort() {
        return localPort;
    }
    
    /**
     * Gets the process ID associated with this channel
     * 
     * @return The process ID
     */
    public int getProcessId() {
        return processId;
    }
}