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

                String blockchain = getBlockchain();

                String response = "{\"blockchain\":" + JSONObject.quote(blockchain) + "}";
                sendResponse(exchange, 200, response);

            } catch (Exception e) {
                String response = "{\"error\":\"" + e.getMessage() + "\"}";
                sendResponse(exchange, 500, response);
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
        PublicKey senderKey = null;
        PublicKey receiverKey = null;

        // 1. Get Public Keys
        try {
            senderKey = clientManager.getPublicKey(senderName);
            receiverKey = clientManager.getPublicKey(receiverName);
        } catch (Exception e) {
            System.err.println("ClientLib: Error retrieving public keys: " + e.getMessage());
            throw new Exception("Failed to get public keys", e);
        }
        if (senderKey == null) {
            Logger.log(Logger.CLIENT_LIBRARY, "Error: Sender PublicKey not found for " + senderName);
            throw new Exception("Could not find PublicKey for sender: " + senderName);
        }
        if (receiverKey == null) {
            Logger.log(Logger.CLIENT_LIBRARY, "Error: Receiver PublicKey not found for " + receiverName);
            throw new Exception("Could not find PublicKey for receiver: " + receiverName);
        }

        // 2. Prepare other Transaction fields
        String transactionData = "";
        long nonce = System.currentTimeMillis();
        String signature = senderSignature;

        // 3. Create the Transaction Object
        Transaction transaction = new Transaction(
                senderKey,
                receiverKey,
                amount,
                transactionData, 
                nonce,
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
            Logger.log(Logger.CLIENT_LIBRARY, "Sent PROPOSE request for transaction. Nonce: " + nonce); 
            return true;

        } catch (Exception e) {
            Logger.log(Logger.CLIENT_LIBRARY, "Error: Failed sending proposal: " + e.getMessage());
            throw new Exception("Failed to send proposal to leader", e);
        }
    }

  

    /**
     * Gets the current blockchain with improved message handling.
     * 
     * @return The blockchain as a formatted string
     * @throws Exception If getting fails
     */
    public String getBlockchain() throws Exception {
        leaderLink.clearReceivedMessages();

        sendToLeader("", "GET_BLOCKCHAIN");
        Logger.log(Logger.CLIENT_LIBRARY, "Sent get blockchain request");

        final int MAX_RETRIES = 5;
        final int RETRY_DELAY_MS = 1000;

        for (int retry = 0; retry < MAX_RETRIES; retry++) {
            Thread.sleep(RETRY_DELAY_MS);

            List<AuthenticatedMessage> messages = leaderLink.getReceivedMessages();
            for (AuthenticatedMessage authMsg : messages) {
                Message message = authMsg;
                Logger.log(Logger.CLIENT_LIBRARY, "Received message: " + message);
                if (message.getCommand().equals("BLOCKCHAIN_RESULT")) {
                    return message.getPayload();
                }
            }

            if (retry < MAX_RETRIES - 1) {
                Logger.log(Logger.CLIENT_LIBRARY, "No response yet, retrying...");
            }
        }

        Logger.log(Logger.CLIENT_LIBRARY, "No blockchain data received after " + MAX_RETRIES + " attempts");
        return "No blockchain data received after timeout. Please try again.";
    }

    /**
     * Sends a test message to the leader.
     * 
     * @param times Number of times to run the test
     * @throws Exception If sending fails
     */
    public void sendTestMessage(int times) throws Exception {
        sendToLeader(String.valueOf(times), "TEST");
        Logger.log(Logger.CLIENT_LIBRARY, "Sent test message for " + times + " run(s)");
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
}