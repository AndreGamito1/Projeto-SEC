package com.depchain.consensus;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;

import org.ietf.jgss.MessageProp;
import org.json.JSONArray;
import org.json.JSONObject;


import com.depchain.utils.*;
import com.depchain.networking.*;

/**
 * Implements a member node in the Byzantine consensus system
 */
public class Member {
    protected String name;
    protected int port;
    protected int leaderPort;
    protected Map<String, AuthenticatedPerfectLinks> memberLinks = new HashMap<>();
    protected AuthenticatedPerfectLinks leaderLink;
    protected EpochState currentEpoch;
    protected List<EpochState> writeSet;    
    private static final String RESOURCES_FILE = "shared/resources.json";
    private final ConditionalCollect conditionalCollect;
    protected List<String> blockchain;
    protected Map<String, String> connections = new HashMap<>();
    protected KeyManager keyManager;
    private int lastProcessedIndex = 0;
    
    // Constants for commands used in messages
    private static final String CMD_PROPOSE = "EPOCH_PROPOSE";
    private static final String CMD_ABORT = "EPOCH_ABORT";
    private static final String CMD_DECIDE = "EPOCH_DECIDE";
    private static final String CMD_KEY_EXCHANGE = "KEY_EXCHANGE";
    private static final String CMD_KEY_ACK = "KEY_ACK";
    private static final String CMD_KEY_OK = "KEY_OK";
    private static final String CMD_COLLECTED = "CMD_COLLECTED";
    private static final String CMD_STATE = "CMD_STATE";
    private static final String CMD_READ = "CMD_READ";
    private static final String CMD_WRITE = "CMD_WRITE";
    private static final String CMD_ACCEPT = "CMD_ACCEPT";


    /**
     * Constructor for Member.
     * 
     * @param name The name of this member
     * @throws Exception If initialization fails
     */
    public Member(String name) throws Exception {
        this.name = name;
        this.currentEpoch = new EpochState(0, "0", new TimestampValue[0]);
        keyManager = new KeyManager(name);

        if(!name.equals("leader")){
            setupMemberLinks();
        }
        AuthenticatedPerfectLinks[] memberLinksArray = memberLinks.values().toArray(new AuthenticatedPerfectLinks[0]);
        this.conditionalCollect = new ConditionalCollect(
            this.name,
            "leader",
            4,
            1,
            memberLinksArray
        );
        
        this.blockchain = new ArrayList<>();
        connections.put("leader", "");
        
        Logger.log(Logger.MEMBER, "Initialized member: " + name + " on port " + port);
    }

    /**
     * Sets up perfect links to all members.
     * 
     * @throws Exception If setup fails
     */
    protected void setupMemberLinks() throws Exception {
        String content = new String(Files.readAllBytes(Paths.get(RESOURCES_FILE)));
        JSONObject json = new JSONObject(content);
           
        JSONObject memberJson = json.getJSONObject(this.name);
        JSONArray connections = memberJson.getJSONArray("connections");
        String memberID = "0";
        if (!this.name.equals("leader")){
            memberID = this.name.replace("member", "");
        }
           
        for (int i = 0; i < connections.length(); i++) {
            String targetName = connections.getString(i);
            String targetID = targetName.equals("leader") ? "0" : targetName.replace("member", "");
               
            if (!memberID.equals(targetID)) { 
                String targetIP = "127.0.0.1"; 
                int portToTarget;
                int portFromTarget;
                
                if (targetName.equals("clientLibrary")) {
                    portToTarget = 5005;
                    portFromTarget = 6005;
                } else {
                    portToTarget = Integer.parseInt("70" + memberID + targetID);
                    portFromTarget = Integer.parseInt("70" + targetID + memberID);
                }
                   
                System.out.println("Establishing link from " + this.name + " to " + targetName +
                        " at " + targetIP + ":" + portToTarget + ", back at " + targetIP + ":" + portFromTarget);
                AuthenticatedPerfectLinks link = new AuthenticatedPerfectLinks(targetIP, portToTarget, portFromTarget, targetName);
                if (targetName.equals("leader")) {
                    leaderLink = link; 
                } else {
                    System.out.println("ADDING LINK TO LINKS LIST: " + targetName);
                    memberLinks.put(targetName, link);
                }
                   
                Logger.log(Logger.LEADER_ERRORS, "Established link from " + this.name + " to " + targetName +
                        " at " + targetIP + ":" + portToTarget + ", back at " + targetIP + ":" + portFromTarget);
            }
        }
    }


    public String printWriteSet() {
        if (writeSet == null || writeSet.isEmpty()) {
            return "[]";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < writeSet.size(); i++) {
            sb.append(writeSet.get(i).toString());
            
            if (i < writeSet.size() - 1) {
                sb.append(" -> ");
            }
        }
        
        return sb.toString();
    }



    /**
     * Starts the member service and begins listening for messages.
     */
    public void start() {
        System.out.println("Starting member service on port " + port);
        Logger.log(Logger.MEMBER, "Starting member service on port " + port);
        System.out.println("Generating RSA key pair for member: " + name);
        
        
        try {
            Thread statusThread = new Thread(this::printBlockchainStatus);
            statusThread.setDaemon(true);
            statusThread.start();
            
            while (true) {
                processMessages();
                Thread.sleep(100);
            }
        } catch (Exception e) {
            Logger.log(Logger.MEMBER, "Error in member service: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Prints the current blockchain status every 3 seconds.
     */
    private void printBlockchainStatus() {
        try {
            while (true) {
                System.out.println("\n========== " + name + " BLOCKCHAIN STATUS ==========");
                System.out.println(blockchain);
                System.out.println("=================================================\n");
                
                Thread.sleep(3000);
            }
        } catch (InterruptedException e) {
            System.err.println("Blockchain status thread interrupted: " + e.getMessage());
        }
    }
    
    /**
     * Processes incoming messages from the leader.
     * 
     * @throws Exception If processing fails
     */
    private void processMessages() throws Exception {
        List<AuthenticatedMessage> messages = new ArrayList<>();
    
        messages.addAll(leaderLink.getReceivedMessages());
        for (AuthenticatedPerfectLinks link : memberLinks.values()) {
            messages.addAll(link.getReceivedMessages());
        }
    
        int messageCount = messages.size();
        while (lastProcessedIndex < messageCount) {
            AuthenticatedMessage authMessage = messages.get(lastProcessedIndex);
            
            String command = authMessage.getCommand();
            String payload = authMessage.getPayload();
            
            System.out.println("Processing message: " + command + " with payload: " + payload);
            switch (command) {
                case CMD_KEY_EXCHANGE:
                    handleKeyExchange(authMessage);
                    break;
                    
                case CMD_KEY_OK:
                    Logger.log(Logger.MEMBER, "Key exchange completed successfully");
                    break;
                    
                case CMD_READ:
                    handleReadRequest(payload);
                    break;
                case CMD_WRITE:
                    handleWriteRequest(authMessage);
                    break;

                case CMD_ACCEPT:
                    handleAcceptRequest(authMessage);
                    break;
                    
                case CMD_PROPOSE:
                    break;
                    
                case CMD_ABORT:
                    break;
                    
                case CMD_DECIDE:
                    handleDecide(authMessage);
                    break;
    
                case CMD_COLLECTED:
                    conditionalCollect.processCollected(authMessage);
                    break;
    
                case "TEST_MESSAGE":
                    Logger.log(Logger.MEMBER, "Received test message: " + payload);
                    sendToLeader("Received: " + payload, "TEST_REPLY");
                    break;
                    
                case "LEADER_BROADCAST":
                    Logger.log(Logger.MEMBER, "Received broadcast: " + payload);
                    break;
                    
                default:
                    Logger.log(Logger.MEMBER, "Unknown command: " + command);
                    break;
            }
            
            lastProcessedIndex++;
        }
    }
    
    
    /**
     * Handles a key exchange message from the leader.
     * 
     * @param authMessage The authenticated message
     * @throws Exception If handling fails
     */
    private void handleKeyExchange(AuthenticatedMessage authMessage) throws Exception {
        try {
            Logger.log(Logger.MEMBER, "Received KEY_EXCHANGE from leader");
            
            PrivateKey privateKey = keyManager.getPrivateKey(name);
            if (privateKey == null) {
                throw new Exception("No private key found for " + name);
            }
            
            String encryptedKey = authMessage.getPayload();
            String aesKeyStr = AuthenticatedPerfectLinks.decryptWithRsa(encryptedKey, privateKey);
            
            connections.put("leader", aesKeyStr);
            
            sendToLeader("ACK", CMD_KEY_ACK);
            Logger.log(Logger.MEMBER, "Sent KEY_ACK to leader");
            
            SecretKey aesKey = AuthenticatedPerfectLinks.stringToAesKey(aesKeyStr);
            leaderLink.changeKey(aesKey);
            
            Logger.log(Logger.MEMBER, "Changed to AES encryption for leader communications");
            
        } catch (Exception e) {
            Logger.log(Logger.MEMBER, "Error handling KEY_EXCHANGE: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Handles a read request from the leader.
     * 
     * @param payload The epoch number
     * @throws Exception If sending fails
     */
    private void handleReadRequest(String payload) throws Exception {
        System.out.println("Received READ request");
        System.out.println("Current blockchain: " + printWriteSet());
    
        
        System.out.println("Sending STATE TO LEADER:");
        System.out.println(this.name + "|" + null);
        sendToLeader(this.name + "|" + null, CMD_STATE);

    }

    private void handleWriteRequest(AuthenticatedMessage message) throws Exception {
        System.out.println("Received WRITE request");

        if (conditionalCollect.receiveWrite(message)) {
            System.out.println("Received enough writes to proceed to consensus");
            return;
        };
        return;
    }

    private void handleAcceptRequest(AuthenticatedMessage message) throws Exception {
        System.out.println("Received WRITE request");

        if (conditionalCollect.receiveAccept(message)) {
            System.out.println("Received enough accepts to proceed to consensus");

            System.out.println("Current blockchain: " + blockchain);
            blockchain.add(message.getPayload());
            System.out.println("Updated blockchain: " + blockchain);
            return;
        };
        return;
    }


    private void handleDecide(AuthenticatedMessage message) throws Exception {
        System.out.println("Received DECIDE request");
        conditionalCollect.reset();
    }

    /**
     * Sends a message to the leader.
     * 
     * @param payload The message payload
     * @param command The command to execute
     * @throws Exception If sending fails
     */
    protected void sendToLeader(String payload, String command) throws Exception {
        Message message = new Message(payload, command);
        leaderLink.alp2pSend("leader", message);
    }
    
    /**
     * Gets the connections map containing symmetric keys.
     * 
     * @return The connections map
     */
    public Map<String, String> getConnections() {
        return connections;
    }
    
    /**
     * Sets a symmetric key for a connection.
     * 
     * @param entityName The name of the entity (e.g., "leader" or another member)
     * @param symmetricKey The symmetric key to use
     */
    public void setConnectionKey(String entityName, String symmetricKey) {
        connections.put(entityName, symmetricKey);
    }
    
    /**
     * Gets the name of this member.
     * 
     * @return The member name
     */
    public String getName() {
        return name;
    }
    


    /**
     * Main method to start a member instance.
     * 
     * @param args Command line arguments (member name required)
     * @throws Exception If initialization fails
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Member <memberName>");
            return;
        }
        
        String memberName = args[0];
        Logger.initFromArgs("--log=none");
        
        Member member = new Member(memberName);
        System.out.println("Starting member: " + memberName);
        member.start();
    }
}