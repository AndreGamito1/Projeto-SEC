package com.depchain.consensus;

import java.util.List;
import java.util.Map;
import java.io.File;
import java.io.IOException;
import java.security.PublicKey;
import java.util.ArrayList;
import javax.crypto.SecretKey;
import java.util.Iterator;

import com.depchain.networking.*;
import com.depchain.utils.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.depchain.blockchain.*;
public class Member {
    private Role currentRole;
    protected Map<String, SecretKey> memberKeys;
    protected Map<String, AuthenticatedPerfectLinks> memberLinks;
    private MemberManager memberManager;
    private String name;

    private List<Block> blockchain;
    private List<Message> quorumDecideMessages;
    private List<Message> quorumAbortMessages;
    private boolean working;
    private ByzantineEpochConsensus epochConsensus;

    private WorldState worldState;

     // Configuration file paths (consider making these constants or configurable)
     private static final String GENESIS_ACCOUNTS_FILE_PATH = "src/main/resources/genesis_accounts.json";
     private static final String GENESIS_BLOCK_RESOURCE_NAME = "src/main/resources/genesisBlock.json"; // Classpath resource

    public Member(String name) throws Exception {
        this.name = name; 
        this.memberManager = new MemberManager(name);
        this.working = false;
        this.quorumAbortMessages = new ArrayList<>();
        this.quorumDecideMessages = new ArrayList<>();
        this.blockchain = new ArrayList<>();
        System.out.println("Member created: " + name);
        setupMemberLinks();


        if (memberManager.isLeader()) {
            currentRole = new LeaderRole(this);
        }
        else {
            currentRole = new MemberRole(this);
        }
        this.blockchain = new ArrayList<>();


        try {
            System.out.println("Member " + name + " is loading genesis world state...");

            // Load the WorldState using the classpath resource name and the map
            WorldState worldState = new WorldState();
            this.worldState = worldState;
            worldState.loadGenesisState();
            
            // Print loaded accounts
            System.out.println("Loaded accounts from genesis block:");
            for (AccountState account : worldState.getAccounts().values()) {
                System.out.println(account);
            }


            // --- Optional: Initialize the blockchain list with the Genesis Block ---
            Block genesisBlock = createGenesisBlockObject();
            if (genesisBlock != null) {
                this.blockchain.add(genesisBlock);
            }

            this.epochConsensus = new ByzantineEpochConsensus(this, memberManager, worldState);

            System.out.println("Blockchain state: " + this.blockchain);

        } catch (IOException e) {
            System.err.println("FATAL: Member " + name + " could not load genesis files ("
                             + GENESIS_ACCOUNTS_FILE_PATH + " or " + GENESIS_BLOCK_RESOURCE_NAME + "): " + e.getMessage());
            throw new RuntimeException("Failed to load genesis configuration for member " + name, e); // Halt if fails
        } catch (Exception e) { // Catch other potential errors from GenesisKeyLoader or WorldState.load
            System.err.println("FATAL: Member " + name + " encountered an error during genesis initialization: " + e.getMessage());
             // Re-throwing Exception as declared by the constructor
             // or wrap in RuntimeException if you prefer unchecked exceptions here
            throw e;
        }


        start();
    }

    public Member(String name, String behavior) throws Exception {
        this.name = name; 
        this.memberManager = new MemberManager(name);
        this.working = false;
        this.quorumAbortMessages = new ArrayList<>();
        this.quorumDecideMessages = new ArrayList<>();
        this.blockchain = new ArrayList<>();
        System.out.println("Member created: " + name + " with behavior: " + behavior);
        setupMemberLinks();


        if (memberManager.isLeader()) {
            currentRole = new LeaderRole(this);
        }
        else {
            currentRole = new MemberRole(this);
        }
        this.blockchain = new ArrayList<>();


        try {
            System.out.println("Member " + name + " is loading genesis world state...");

            // Load the WorldState using the classpath resource name and the map
            WorldState worldState = new WorldState();
            this.worldState = worldState;
            worldState.loadGenesisState();
            
            // Print loaded accounts
            System.out.println("Loaded accounts from genesis block:");
            for (AccountState account : worldState.getAccounts().values()) {
                System.out.println(account);
            }


            // --- Optional: Initialize the blockchain list with the Genesis Block ---
            Block genesisBlock = createGenesisBlockObject();
            if (genesisBlock != null) {
                this.blockchain.add(genesisBlock);
            }

            this.epochConsensus = new ByzantineEpochConsensus(this, memberManager, worldState, behavior);

            System.out.println("Blockchain state: " + this.blockchain);

        } catch (IOException e) {
            System.err.println("FATAL: Member " + name + " could not load genesis files ("
                             + GENESIS_ACCOUNTS_FILE_PATH + " or " + GENESIS_BLOCK_RESOURCE_NAME + "): " + e.getMessage());
            throw new RuntimeException("Failed to load genesis configuration for member " + name, e); // Halt if fails
        } catch (Exception e) { // Catch other potential errors from GenesisKeyLoader or WorldState.load
            System.err.println("FATAL: Member " + name + " encountered an error during genesis initialization: " + e.getMessage());
             // Re-throwing Exception as declared by the constructor
             // or wrap in RuntimeException if you prefer unchecked exceptions here
            throw e;
        }


        start();
    }
    public Block createGenesisBlockObject() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            File file = new File(GENESIS_BLOCK_RESOURCE_NAME);
    
            JsonNode root = mapper.readTree(file);
    
            String blockHash = root.get("block_hash").asText();
            String previousHash = root.get("previous_block_hash").isNull()
                    ? "0".repeat(64)
                    : root.get("previous_block_hash").asText();
    
            List<Transaction> transactions = new ArrayList<>(); // Genesis block não tem txs
    
            return new Block(previousHash, transactions, blockHash); 
    
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void waitForMessages() {
        try {
            for (AuthenticatedPerfectLinks link : memberLinks.values()) {
                List<AuthenticatedMessage> receivedMessages = null;
                try {
                    receivedMessages = link.getReceivedMessages();
                    
                    // Only process messages if the list is not null
                    if (receivedMessages != null) {

                        while (!receivedMessages.isEmpty()) {
                            AuthenticatedMessage message = receivedMessages.remove(0);
                            Logger.log(Logger.MEMBER, "Received message from " + message.getSourceId());
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


    protected void setupMemberLinks() throws Exception {
        System.out.println("Setting up member links");
        memberManager.setupMemberLinks();
        memberLinks = memberManager.getMemberLinks();
    }

 

    public void start() throws Exception {
        currentRole.start();
        while (true) {
            waitForMessages();
        }
    }

    public void processMessage(String sourceId, AuthenticatedMessage message) throws Exception {
        currentRole.processMessage(sourceId, message);
    }

    public void processClientCommand(String command, String payload) throws Exception {
        currentRole.processClientCommand(command, payload);
    }

    public void processMemberMessage(String memberName, String command, String payload, AuthenticatedMessage message) throws Exception {
        currentRole.processMemberMessage(memberName, command, payload, message);
    }

    public void logReceivedMessagesStatus() throws Exception {
        currentRole.logReceivedMessagesStatus();
    }

    public void changeRole(Role newRole) {
        this.currentRole = newRole;
    }

    public Message handleNewMessage(String sourceId, AuthenticatedMessage message) {
        return memberManager.handleNewMessage(sourceId, message);
    }

    // Appends a value to the blockchain
    public void addToBlockchain(String serializedBlock) {
        Block block;
        try {
            block = Block.deserializeFromBase64(serializedBlock);
            blockchain.add(block);
            this.worldState.applyBlock(block);
            saveBlock(block);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("----------------------- BLOCKCHAIN UPDATED ----------------------");
        Logger.log(Logger.MEMBER, "Updated blockchain: " + blockchain);
        Logger.log(Logger.MEMBER, "Updated world state: " + this.worldState.toString());
        System.out.println("-----------------------  ----------------------");
    }

    public void handleDecideMessage(AuthenticatedMessage message) throws Exception {
        currentRole.handleDecideMessage(message);
    }  
    
    public void handleAbortMessage(AuthenticatedMessage message) throws Exception {
        currentRole.handleAbortMessage(message);
    }   
    
    public void startConsensus() {
        // If blockchain is empty, pass null or create an initial state
        if(working) {
            Logger.log(Logger.MEMBER, "Already working on consensus");
            return;
        }
        this.epochConsensus = new ByzantineEpochConsensus(this, memberManager, worldState);
    }

    public void setWorking(boolean working) {
        this.working = working;
        Logger.log(Logger.MEMBER, "----------------- Working: " + working);
    }

    public boolean isWorking() {
        return working;
    }

    public void saveBlock(Block block){
        try{
            currentRole.saveBlock(block);
        } catch (Exception e) {
            e.printStackTrace();
        }   
    }

    //--- Getters and Setters ---

    public String getName() {
        return name;
    }

    public WorldState getWorldState() {
        return worldState;
    }

    public MemberManager getMemberManager() {
        return memberManager;
    }
    public ByzantineEpochConsensus getConsensus() {
        return epochConsensus;
    }

    public int getQuorumSize() {
        return memberManager.getQuorumSize();
    }

    public String getPreviousHash() {
        System.out.println("#############Blockchain size: " + blockchain.size());
        return blockchain.get(blockchain.size() - 1).getHash();
    }

    public List<Block> getBlockchain() {
        return blockchain;
    }
    public List<Message> getQuorumDecideMessages() {
        return quorumDecideMessages;
    }
    
    public List<Message> getQuorumAbortMessages() {
        return quorumAbortMessages;
    }
}