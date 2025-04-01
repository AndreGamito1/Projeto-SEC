package com.depchain.consensus;

import com.depchain.utils.Logger;
import com.depchain.networking.Message;
import com.depchain.blockchain.Transaction;
import com.depchain.blockchain.Block;
import com.depchain.networking.AuthenticatedMessage;
import java.util.LinkedList;
import java.util.Queue;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Timer;
import java.util.TimerTask;
import java.io.IOException;

public class LeaderRole implements Role {
    private Member member;
    private Queue<Block> blockQueue;
    private List<Transaction> transactionPool;
    private Timer blockTimer;
    private static final int MAX_TRANSACTIONS_PER_BLOCK = 3;
    private static final long BLOCK_TIMEOUT_MS = 12000; // 12 seconds
    private boolean timerRunning = false;

    public LeaderRole(Member member) {
        this.member = member;
        this.blockQueue = new LinkedList<>();
        this.transactionPool = new ArrayList<>();
        this.blockTimer = new Timer("BlockTimer", true);
    }

    @Override
    public void start() {
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Sleep interrupted: " + e.getMessage());
        }
    }

    @Override
    public void processMessage(String sourceId, AuthenticatedMessage message) {
        System.out.println("Processing message with payload: " + message.getPayload());
        switch (message.getCommand()) {
            case "STATE":
                handleStateMessage(message);
                break;
            case "WRITE":
                System.out.println("Processing WRITE Message: " + message.getCommand() + " from " + sourceId);
                handleAckMessage(message);
                break;
            case "ACCEPT":
                System.out.println("Processing ACCEPT Message: " + message.getCommand() + " from " + sourceId);
                handleAckMessage(message);
                break;
            case "DECIDE":
                System.out.println("Processing DECIDE Message: " + message.getCommand() + " from " + sourceId);
                handleDecideMessage(message);
                break;
            case "ABORT":
                System.out.println("Processing ABORT Message: " + message.getCommand() + " from " + sourceId);
                handleAbortMessage(message);
                break;
            case "TRANSACTION":
                System.out.println("Processing TRANSACTION Message: " + message.getCommand() + " from " + sourceId);
                Transaction transaction;
                String serializedTransaction;
                try {
                    serializedTransaction = message.getPayload();
                    // Ensure the serializedTransaction string is valid
                    if (serializedTransaction == null || serializedTransaction.isEmpty()) {
                        throw new IOException("Serialized transaction is null or empty.");
                    }

                    // Use the correct deserialization method
                    transaction = Transaction.deserializeFromString(serializedTransaction);

                    if (transaction == null) {
                        throw new IOException("Deserialization resulted in null object.");
                    }
                    System.out.println("Received transaction: " + transaction);
                    addTransactionToPool(transaction);
                } catch (Exception e) {
                    Logger.log(Logger.CLIENT_LIBRARY, "Error: Failed to deserialize transaction: " + e.getMessage());
                }
                break;
            default:
                System.out.println("Unknown command: " + message.getCommand());
                break;
        }
    }

    /**
     * Adds a transaction to the pool and starts a timer if needed.
     * If the pool reaches MAX_TRANSACTIONS_PER_BLOCK, a block is created immediately.
     */
    private synchronized void addTransactionToPool(Transaction transaction) {
        // Add the transaction to the pool
        transactionPool.add(transaction);
        Logger.log(Logger.LEADER_ERRORS, "Added transaction to pool. Current pool size: " + transactionPool.size());
        
        // Start timer if it's not already running and this is the first transaction
        if (!timerRunning && transactionPool.size() == 1) {
            startBlockTimer();
        }
        
        // If we have enough transactions, create a block immediately
        if (transactionPool.size() >= MAX_TRANSACTIONS_PER_BLOCK) {
            Logger.log(Logger.LEADER_ERRORS, "Transaction pool reached max size (" + MAX_TRANSACTIONS_PER_BLOCK + "). Creating block...");
            createAndProposeBlock();
        }
    }

    /**
     * Starts a timer that will create a block after BLOCK_TIMEOUT_MS milliseconds
     * if the pool hasn't reached MAX_TRANSACTIONS_PER_BLOCK by then.
     */
    private void startBlockTimer() {
        timerRunning = true;
        Logger.log(Logger.LEADER_ERRORS, "Starting block timer for " + BLOCK_TIMEOUT_MS + "ms");
        
        blockTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (LeaderRole.this) {
                    // Only create a block if there are transactions in the pool
                    if (!transactionPool.isEmpty()) {
                        Logger.log(Logger.LEADER_ERRORS, "Block timer expired. Creating block with " + transactionPool.size() + " transactions");
                        createAndProposeBlock();
                    } else {
                        Logger.log(Logger.LEADER_ERRORS, "Block timer expired but transaction pool is empty");
                        timerRunning = false;
                    }
                }
            }
        }, BLOCK_TIMEOUT_MS);
    }

    /**
     * Creates a block with the transactions in the pool and proposes it to the blockchain.
     */
    private synchronized void createAndProposeBlock() {
        if (transactionPool.isEmpty()) {
            Logger.log(Logger.LEADER_ERRORS, "Cannot create block: transaction pool is empty");
            timerRunning = false;
            return;
        }
        
        try {
            // Cancel the timer if it's running
            timerRunning = false;
            
            // Get the current blockchain state
            String previousHash = "";
            previousHash = member.getPreviousHash();
            
            // Create a new block with the transactions from the pool
            Block block = new Block(previousHash, new ArrayList<>(transactionPool));
            
            // Clear the transaction pool
            transactionPool.clear();
            
            
            
            // Propose the block to the blockchain
            Logger.log(Logger.LEADER_ERRORS, "Proposing block: " + block.toString());
            ProposeBlock(block);
        } 
        catch (Exception e) {
            Logger.log(Logger.LEADER_ERRORS, "Error creating block: " + e.getMessage());
        }
    }

    @Override
    public void processClientCommand(String command, String payload) {
        // Leader-specific client command processing
    }

    @Override
    public void processMemberMessage(String memberName, String command, String payload, AuthenticatedMessage message) {
        // Leader-specific member message processing
    }

    @Override
    public void logReceivedMessagesStatus() {
        // Leader-specific logging
    }

    @Override
    public void handleCollectedMessage(AuthenticatedMessage message) {
        // Leader-specific message handling
    }

    @Override
    public void handleReadMessage(Message message) {
        // Leader-specific read message handling
    }

    @Override
    public void handleStateMessage(AuthenticatedMessage message) {
        member.getConsensus().handleStateMessage(message);
    }

    @Override
    public void ProposeBlock(Block block) {
        Logger.log(Logger.LEADER_ERRORS, "PROPOSING BLOCK ------------------------ " + block.toString());
        
        // If the member is already working on a message, queue this one
        if (member.isWorking()) {
            Logger.log(Logger.LEADER_ERRORS, "Already working on a message, queueing: " + block.toString());
            blockQueue.add(block);
        } else {
            try {
                // Serialize the block
                String serializedBlock = block.toBase64String();
                // Otherwise, start working on this message
                member.setWorking(true);
                member.getConsensus().handleProposeMessage(serializedBlock);
            } catch (IOException e) {
                Logger.log(Logger.LEADER_ERRORS, "Error serializing block: " + e.getMessage());
            }
        }
    }

    @Override
    public void handleAckMessage(Message message) {
        member.getConsensus().handleAckMessage(message);
    }

    @Override
    public void handleDecideMessage(Message message) {
        Logger.log(Logger.MEMBER, "Received DECIDE message: " + message.getPayload());
        member.getQuorumDecideMessages().add(message);
        Logger.log(Logger.MEMBER, "Quorum size for DECIDE's: " + member.getQuorumSize());
        
        if (member.getQuorumDecideMessages().size() == member.getQuorumSize()) {
            Logger.log(Logger.MEMBER, "Received quorum of DECIDE messages");
            
            // Clear the decided messages
            member.getQuorumDecideMessages().clear();
            Logger.log(Logger.LEADER_ERRORS, "BLOCKCHAIN: " + member.getBlockchain());
            
            // Check if there are any queued messages
            if (!blockQueue.isEmpty()) {
                // Take the next message from the queue and process it
                Block nextBlock = blockQueue.poll();
                Logger.log(Logger.LEADER_ERRORS, "Processing next queued message: " + nextBlock.toString());
                
                try {
                    // Continue working on the next message
                    String serializedBlock = nextBlock.toBase64String();
                    member.getConsensus().handleProposeMessage(serializedBlock);
                } catch (IOException e) {
                    Logger.log(Logger.LEADER_ERRORS, "Error serializing block: " + e.getMessage());
                    member.setWorking(false);
                }
            } else {
                // No more messages to process, set working to false
                member.setWorking(false);
                Logger.log(Logger.LEADER_ERRORS, "No more messages in queue, setting working to false");
            }
        }
    }

    @Override
    public void handleAbortMessage(Message message) {
        Logger.log(Logger.MEMBER, "Received ABORT message: " + message.getPayload());
        member.getQuorumAbortMessages().add(message);
        Logger.log(Logger.MEMBER, "Quorum size for ABORTS's: " + member.getQuorumSize());
        
        if (member.getQuorumAbortMessages().size() == member.getQuorumSize()) {
            Logger.log(Logger.MEMBER, "Received quorum of ABORT messages");
            
            // Clear the abort messages
            member.getQuorumAbortMessages().clear();
            Logger.log(Logger.LEADER_ERRORS, "BLOCKCHAIN: " + member.getBlockchain());
            
            // Check if there are any queued messages
            if (!blockQueue.isEmpty()) {
                // Take the next message from the queue and process it
                Block nextBlock = blockQueue.poll();
                Logger.log(Logger.LEADER_ERRORS, "Processing next queued message: " + nextBlock.toString());
                
                try {
                    // Continue working on the next message
                    String serializedBlock = nextBlock.toBase64String();
                    member.getConsensus().handleProposeMessage(serializedBlock);
                } catch (IOException e) {
                    Logger.log(Logger.LEADER_ERRORS, "Error serializing block: " + e.getMessage());
                    member.setWorking(false);
                }
            } else {
                // No more messages to process, set working to false
                member.setWorking(false);
                Logger.log(Logger.LEADER_ERRORS, "No more messages in queue, setting working to false");
            }
        }
    }

    // Helper methods for testing/debugging
    
    public synchronized int getTransactionPoolSize() {
        return transactionPool.size();
    }
    
    public int getQueueSize() {
        return blockQueue.size();
    }
    
    public boolean isTimerRunning() {
        return timerRunning;
    }
    
    /**
     * Shut down the timer when the leader role is no longer needed
     * This should be called when the node changes roles or shuts down
     */
    public void shutdown() {
        if (blockTimer != null) {
            blockTimer.cancel();
        }
    }
}
