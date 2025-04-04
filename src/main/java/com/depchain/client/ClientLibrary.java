package com.depchain.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.util.List;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import com.sun.net.httpserver.Headers;

import org.json.JSONObject;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.depchain.consensus.MemberManager;
import com.depchain.networking.*;
import com.depchain.utils.*;
import com.depchain.blockchain.*;

/**
 * Client library for interacting with the Byzantine consensus blockchain.
 */
public class ClientLibrary {
    private String name = "clientLibrary";
    private int port;
    private int leaderPort;
    private int httpPort;
    private AuthenticatedPerfectLinks leaderLink;
    private static final int BASE_PORT = 5005;
    private static final int PORT_RANGE = 1000;
    private HttpServer httpServer;

    protected MemberManager memberManager;
    private ClientManager clientManager;
    private String lastReceivedBalance = null;
    private boolean balanceReceived = false;
    private final Object balanceLock = new Object();

    /**
     * Constructor for ClientLibrary with default port allocation.
     * 
     * @throws Exception If initialization fails
     */
    public ClientLibrary() throws Exception {
        this(allocateDynamicPort());
    }

    /**
     * Constructor for ClientLibrary with specific port.
     * 
     * @param clientPort The port to use for this client
     * @throws Exception If initialization fails
     */
    public ClientLibrary(int clientPort) throws Exception {
        this(clientPort, clientPort + 1);
        memberManager = new MemberManager(name);
        memberManager.setupMemberLinks();
        clientManager = new ClientManager("src/main/resources/accounts.json");
        
        // Start background thread for message processing
        startMessageProcessingThread();
    }

    /**
     * Constructor for ClientLibrary with specific port and HTTP port.
     * 
     * @param clientPort The port to use for this client
     * @param httpPort   The port for the REST API
     * @throws Exception If initialization fails
     */
    public ClientLibrary(int clientPort, int httpPort) throws Exception {
        this.httpPort = httpPort;
        Logger.log(Logger.CLIENT_LIBRARY, "Initialized client library on port " + port);
        initializeRestApi();
    }

    /**
     * Starts a background thread to continuously process incoming messages.
     */
    private void startMessageProcessingThread() {
        Thread messageThread = new Thread(() -> {
            while (true) {
                try {
                    waitForMessages();
                    Thread.sleep(100); // Short sleep to prevent CPU hogging
                } catch (Exception e) {
                    Logger.log(Logger.CLIENT_LIBRARY, "Error in message thread: " + e.getMessage());
                }
            }
        });
        messageThread.setDaemon(true);
        messageThread.start();
        Logger.log(Logger.CLIENT_LIBRARY, "Message processing thread started");
    }

    /**
     * Waits for and processes incoming messages.
     * Should be called periodically or run in a separate thread.
     */
    public void waitForMessages() {
        try {
            for (AuthenticatedPerfectLinks link : memberManager.getMemberLinks().values()) {
                List<AuthenticatedMessage> receivedMessages = null;
                try {
                    receivedMessages = link.getReceivedMessages();
                    
                    // Only process messages if the list is not null
                    if (receivedMessages != null) {
                        while (!receivedMessages.isEmpty()) {
                            AuthenticatedMessage message = receivedMessages.remove(0);
                            Logger.log(Logger.CLIENT_LIBRARY, "Received message from " + message.getSourceId());
                            processMessage(link.getDestinationEntity(), message);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Processes incoming messages, routing them to the appropriate handler based on command.
     * 
     * @param sourceId The ID of the message source
     * @param authMessage The authenticated message to process
     * @throws Exception If processing fails
     */
    public void processMessage(String sourceId, AuthenticatedMessage authMessage) throws Exception {
        String command = authMessage.getCommand();
        
        Logger.log(Logger.CLIENT_LIBRARY, "Processing message from " + sourceId + " with command " + command);
        
        switch (command) {
            case "BALANCE":
                handleBalanceMessage(authMessage);
                break;
            // Add other message types as needed
            default:
                Logger.log(Logger.CLIENT_LIBRARY, "Unknown command received: " + command);
                break;
        }
    }

    /**
     * Initializes the REST API server.
     * 
     * @throws IOException If server initialization fails
     */
    private void initializeRestApi() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(httpPort), 0);

        httpServer.createContext("/blockchain/append", new AppendHandler());
        httpServer.createContext("/blockchain/get", new GetHandler());

        httpServer.setExecutor(Executors.newFixedThreadPool(10));
        httpServer.start();

        Logger.log(Logger.CLIENT_LIBRARY, "REST API server started on port " + httpPort);
        System.out.println("REST API server available at http://localhost:" + httpPort + "/blockchain/");
    }

    /**
     * Stops the HTTP server.
     */
    public void stopRestApi() {
        if (httpServer != null) {
            httpServer.stop(0);
            Logger.log(Logger.CLIENT_LIBRARY, "REST API server stopped");
        }
    }

    /**
     * Handler for the /blockchain/append endpoint.
     */
    private class AppendHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!exchange.getRequestMethod().equals("POST")) {
                    String response = "{\"error\":\"Method not allowed\"}";
                    sendResponse(exchange, 405, response);
                    return;
                }

                InputStream inputStream = exchange.getRequestBody();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                String requestBody = reader.lines().collect(Collectors.joining());

                JSONObject requestJson = new JSONObject(requestBody);

                String signature = requestJson.optString("signature", "");
                String senderName = requestJson.optString("senderName", "");
                String receiverName = requestJson.optString("receiverName", "");
                double amount = requestJson.optDouble("amount", 0.0);

                if (senderName.isEmpty()) {
                    String response = "{\"error\":\"Missing or empty senderName\"}";
                    sendResponse(exchange, 400, response);
                    return;
                }

                if (receiverName.isEmpty()) {
                    String response = "{\"error\":\"Missing or empty receiverName\"}";
                    sendResponse(exchange, 400, response);
                    return;
                }

                if (amount <= 0) {
                    String response = "{\"error\":\"Invalid or missing value. Amount must be greater than 0\"}";
                    sendResponse(exchange, 400, response);
                    return;
                }


                boolean success = appendToBlockchain(senderName, receiverName, amount, signature);
                String response = "{\"success\":" + success + "}";
                sendResponse(exchange, 200, response);

            } catch (Exception e) {
                String response = "{\"error\":\"" + e.getMessage() + "\"}";
                sendResponse(exchange, 500, response);
            }
        }
    }

    /**
     * Handles BALANCE response messages from the consensus nodes.
     * 
     * @param message The message containing the balance
     */
    public void handleBalanceMessage(Message message) {
        Logger.log(Logger.CLIENT_LIBRARY, "Received BALANCE message");
        String balance = message.getPayload();
        System.out.println("Received balance: " + balance);
        
        // Store the most recently received balance
        this.lastReceivedBalance = balance;
        
        // Notify any waiting threads that we've received the balance
        synchronized(balanceLock) {
            balanceReceived = true;
            balanceLock.notifyAll();
        }
    }

    
    /**
     * Handler for the /blockchain/get endpoint.
     */
    private class GetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!exchange.getRequestMethod().equals("GET")) {
                    String response = "{\"error\":\"Method not allowed\"}";
                    sendResponse(exchange, 405, response);
                    return;
                }

                // Extract signature from headers
                Headers headers = exchange.getRequestHeaders();
                String signature = headers.getFirst("Signature"); 
                String clientName = headers.getFirst("ClientName");
                if (signature == null) {
                    String response = "{\"error\":\"Missing signature\"}";
                    sendResponse(exchange, 400, response);
                    return;
                }
                if (clientName == null) {
                    String response = "{\"error\":\"Missing clientId\"}";
                    sendResponse(exchange, 400, response);
                    return;
                }

                // Reset the balance received flag
                synchronized(balanceLock) {
                    balanceReceived = false;
                }

                // Send request for balance
                boolean requestSent = checkBalance(clientName, signature);
                if (!requestSent) {
                    String response = "{\"error\":\"Failed to send balance request\"}";
                    sendResponse(exchange, 500, response);
                    return;
                }

                // Wait for balance response (with timeout)
                String balance = waitForBalanceResponse(12000); // 12 second timeout
                
                if (balance != null) {
                    // Send the balance as the response
                    System.out.println("Sending balance response: " + balance);
                    String response = "{\"clientName\":\"" + clientName + "\",\"balance\":\"" + balance + "\"}";
                    sendResponse(exchange, 200, response);
                } else {
                    System.out.println("Timeout waiting for balance response");
                    String response = "{\"error\":\"Timeout waiting for balance\"}";
                    sendResponse(exchange, 504, response);
                }

            } catch (Exception e) {
                String response = "{\"error\":\"" + e.getMessage() + "\"}";
                sendResponse(exchange, 500, response);
            }
        }
    }

    /**
     * Waits for a balance response from the blockchain.
     * 
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return The balance string or null if timeout occurred
     */
    private String waitForBalanceResponse(long timeoutMs) {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + timeoutMs;
        
        synchronized(balanceLock) {
            while (!balanceReceived && System.currentTimeMillis() < endTime) {
                try {
                    long waitTime = endTime - System.currentTimeMillis();
                    if (waitTime > 0) {
                        balanceLock.wait(waitTime);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            
            if (balanceReceived) {
                return lastReceivedBalance;
            } else {
                return null; // Timeout occurred
            }
        }
    }

    /**
     * Helper method to send HTTP responses.
     */
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        OutputStream outputStream = exchange.getResponseBody();
        outputStream.write(responseBytes);
        outputStream.close();
    }

    public static boolean verify_signature(String signature, String senderId) throws Exception {
        String decryptedSingature = Encryption.decryptWithPublicKey(senderId, ClientManager.getPublicKey(senderId));
        if (decryptedSingature.equals(senderId)) {
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * Allocates a dynamic port for this client instance.
     * 
     * @return A likely unused port number
     */
    private static int allocateDynamicPort() {
        Random random = new Random();
        return BASE_PORT + random.nextInt(PORT_RANGE);
    }

    /**
     * Appends a string to the blockchain.
     * 
     * @param data The string to append
     * @return true if the request was sent, false otherwise
     * @throws Exception If sending fails
     */
    public boolean appendToBlockchain(String senderName, String receiverName, double amount, String senderSignature) throws Exception {
        // 2. Prepare other Transaction fields
        String transactionData = "";
        String signature = senderSignature;

        // 3. Create the Transaction Object
        Transaction transaction = new Transaction(
                senderName,
                receiverName,
                amount,
                transactionData,
                signature
        );

        // 4. Serialize the Transaction to send
        String serializedTransaction;
        try {
            serializedTransaction = Transaction.serializeToString(transaction);
            if (serializedTransaction == null) {
                throw new IOException("Serialization resulted in null string.");
            }
        } catch (IOException e) {
            Logger.log(Logger.CLIENT_LIBRARY, "Error: Failed to serialize transaction: " + e.getMessage());

            throw new Exception("Serialization failed", e);
        }

        // 5. Send the *serialized transaction* to the leader
        try {
            sendToLeader(serializedTransaction, "TRANSACTION");
            Logger.log(Logger.CLIENT_LIBRARY, "Sent PROPOSE request for transaction."); 
            return true;

        } catch (Exception e) {
            Logger.log(Logger.CLIENT_LIBRARY, "Error: Failed sending proposal: " + e.getMessage());
            throw new Exception("Failed to send proposal to leader", e);
        }
    }

  
    public boolean checkBalance(String senderId, String signature) throws Exception {
    
        // Create a JSON object with the required data
        JSONObject payload = new JSONObject();
        payload.put("signature", signature);
        payload.put("clientName", senderId);
        System.out.println("............... Sending GET_BALANCE message: " + payload.toString());
    
        // Convert JSON object to string
        String payloadString = payload.toString();
    
       try {
            // Send the JSON payload
            sendToLeader(payloadString, "CHECK_BALANCE");
            Logger.log(Logger.CLIENT_LIBRARY, "Sent get blockchain request"); 
            return true;
        } catch (Exception e) {
            Logger.log(Logger.CLIENT_LIBRARY, "Error: Failed sending proposal: " + e.getMessage());
            throw new Exception("Failed to send proposal to leader", e);
        }
    }

 
    /**
     * Sends a message to the leader.
     * 
     * @param payload The message payload
     * @param command The command to execute
     * @throws Exception If sending fails
     */
    private void sendToLeader(String payload, String command) throws Exception {
        Logger.log(Logger.CLIENT_LIBRARY, "Sending message to leader: " + payload + " " + command);
        Logger.log(Logger.CLIENT_LIBRARY, "Leader is " + memberManager.getLeaderName());
        memberManager.sendToMember(memberManager.getLeaderName(), payload, command);
    }


    /**
     * Main method to start a ClientLibrary instance with command-line parameters.
     * Usage: java -jar clientlibrary.jar [clientPort] [httpPort]
     */
    public static void main(String[] args) {
        try {
            ClientLibrary library;

            if (args.length >= 2) {
                int clientPort = Integer.parseInt(args[0]);
                int httpPort = Integer.parseInt(args[1]);
                library = new ClientLibrary(clientPort, httpPort);
                System.out.println("Started ClientLibrary with specified ports:");
            } else if (args.length == 1) {
                int clientPort = Integer.parseInt(args[0]);
                library = new ClientLibrary(clientPort);
                System.out.println("Started ClientLibrary with specified client port:");
            } else {
                library = new ClientLibrary();
                System.out.println("Started ClientLibrary with automatic port allocation:");
            }

            System.out.println("- Client port: " + library.getPort());
            System.out.println("- REST API: http://localhost:" + library.getHttpPort() + "/blockchain/");
            System.out.println("Press Ctrl+C to stop");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down ClientLibrary...");
                library.stopRestApi();
            }));

        } catch (Exception e) {
            System.err.println("Failed to start ClientLibrary: " + e.getMessage());
            e.printStackTrace();
        }
    }

    //--- Getters and Setters ---

    /**
     * Gets the port used by this client library instance.
     * 
     * @return The port number
     */
    public int getPort() {
        return port;
    }

    /**
     * Gets the HTTP port used by the REST API.
     * 
     * @return The HTTP port number
     */
    public int getHttpPort() {
        return httpPort;
    }
}