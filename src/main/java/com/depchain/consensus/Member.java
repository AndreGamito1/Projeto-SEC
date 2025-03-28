package com.depchain.consensus;

import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.security.PublicKey;
import java.util.ArrayList;
import javax.crypto.SecretKey;

import com.depchain.networking.*;
import com.depchain.utils.*;
import com.depchain.blockchain.*;

public class Member {
    private Role currentRole;
    protected Map<String, SecretKey> memberKeys;
    protected Map<String, AuthenticatedPerfectLinks> memberLinks;
    private MemberManager memberManager;
    private String name;

    private List<EpochState> blockchain;
    private List<Block> new_blockchain;
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
        this.epochConsensus = new ByzantineEpochConsensus(this, memberManager, blockchain);
        this.new_blockchain = new ArrayList<>();
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
            worldState.loadGenesisState();
            
            // Print loaded accounts
            System.out.println("Loaded accounts from genesis block:");
            for (AccountState account : worldState.getAccounts().values()) {
                System.out.println(account);
            }


            // --- Optional: Initialize the blockchain list with the Genesis Block ---
            Block genesisBlock = createGenesisBlockObject(this.worldState);
            if (genesisBlock != null) {
                this.new_blockchain.add(genesisBlock);
            }

            System.out.println("Blockchain state: " + this.new_blockchain);

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

    private Block createGenesisBlockObject(WorldState worldState) {
        int blockNumber = 0; // Genesis block number
        String previousHash = "0"; // No previous hash for the genesis block
        List<Transaction> transactions = new ArrayList<>(); // No transactions in the genesis block
        String blockHash = "genesis_hash"; // Placeholder hash

        return new Block(blockNumber, previousHash, transactions, blockHash);
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

    public String getName() {
        return name;
    }

    public ByzantineEpochConsensus getConsensus() {
        return epochConsensus;
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
    public void addToBlockchain(EpochState state) {
        blockchain.add(state);
        setWorking(false);
        Logger.log(Logger.MEMBER, "Updated blockchain: " + blockchain);
    }

    public void handleDecideMessage(AuthenticatedMessage message) throws Exception {
        currentRole.handleDecideMessage(message);
    }  
    
    public void handleAbortMessage(AuthenticatedMessage message) throws Exception {
        currentRole.handleAbortMessage(message);
    }   

    public String getBlockchain() {
        return blockchain.toString();
    }
    public List<Message> getQuorumDecideMessages() {
        return quorumDecideMessages;
    }
    
    public List<Message> getQuorumAbortMessages() {
        return quorumAbortMessages;
    }
    
    public void startConsensus() {
        // If blockchain is empty, pass null or create an initial state
        if(working) {
            Logger.log(Logger.MEMBER, "Already working on consensus");
            return;
        }

        if (blockchain.isEmpty()) {
            this.epochConsensus = new ByzantineEpochConsensus(this, memberManager, blockchain);

        }
        else {
            this.epochConsensus = new ByzantineEpochConsensus(this, memberManager, blockchain.get(blockchain.size() - 1), blockchain);
        }
    }

    public void setWorking(boolean working) {
        this.working = working;
        Logger.log(Logger.MEMBER, "Working: " + working);
    }

    public boolean isWorking() {
        return working;
    }


    public int getQuorumSize() {
        return memberManager.getQuorumSize();
    }

    private boolean isTransactionValid(Transaction tx, WorldState state) {
        // Aqui deves verificar:
        // - Assinatura válida
        // - Sender existe
        // - Saldo suficiente
        // - Nonce correto (se usares nonce)
        
        AccountState sender = state.getAccount(tx.getSender().toString());
        AccountState receiver = state.getAccount(tx.getReceiver().toString());
    
        if (sender == null || receiver == null) return false;
    
        try {
            double balance = new java.math.BigDecimal(sender.getBalance()).doubleValue();
            return balance >= tx.getValue(); // saldo suficiente
        } catch (Exception e) {
            return false;
        }
    }
    
    private void applyTransaction(Transaction tx, WorldState state) {
        AccountState sender = state.getAccount(tx.getSender().toString());
        AccountState receiver = state.getAccount(tx.getReceiver().toString());
    
        if (sender == null || receiver == null) return;
    
        java.math.BigDecimal value = java.math.BigDecimal.valueOf(tx.getValue());
        java.math.BigDecimal senderBalance = new java.math.BigDecimal(sender.getBalance());
        java.math.BigDecimal receiverBalance = new java.math.BigDecimal(receiver.getBalance());
    
        sender.setBalance(senderBalance.subtract(value).toString());
        receiver.setBalance(receiverBalance.add(value).toString());
    } 
    
    public boolean areAllTransactionsValid(Block block) {
        WorldState copyWorldState = WorldState.deepCopy(this.worldState); // cópia profunda do estado atual da worldstate
    
        for (Transaction tx : block.getTransactions()) {
            if (!isTransactionValid(tx, copyWorldState)) {
                return false;
            }
            applyTransaction(tx, copyWorldState); // aplica a transação à cópia para atualizar o estado
        }
    
        return true;
    }

}