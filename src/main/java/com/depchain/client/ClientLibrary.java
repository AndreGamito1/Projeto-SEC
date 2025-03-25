package com.depchain.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
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
    }
    
    /**
     * Constructor for ClientLibrary with specific port and HTTP port.
     * 
     * @param clientPort The port to use for this client
     * @param httpPort The port for the REST API
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
                String data = requestJson.optString("data", "");
                
                if (data.isEmpty()) {
                    String response = "{\"error\":\"Missing data parameter\"}";
                    sendResponse(exchange, 400, response);
                    return;
                }
                
                boolean success = appendToBlockchain(data);
                
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
    public boolean appendToBlockchain(String data) throws Exception {
        sendToLeader(data, "PROPOSE");
        Logger.log(Logger.CLIENT_LIBRARY, "Sent append request: " + data);
        return true;
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
        Logger.log(Logger.CLIENT_LIBRARY,"Leader is "+memberManager.getLeaderName());
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
            }
            else if (args.length == 1) {
                int clientPort = Integer.parseInt(args[0]);
                library = new ClientLibrary(clientPort);
                System.out.println("Started ClientLibrary with specified client port:");
            }
            else {
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