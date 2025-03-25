package com.depchain.consensus;

import com.depchain.utils.Logger;
import com.depchain.networking.Message;
import com.depchain.networking.AuthenticatedMessage;
import java.util.LinkedList;
import java.util.Queue;

public class LeaderRole implements Role {
    private Member member;
    private Queue<Message> messageQueue;

    public LeaderRole(Member member) {
        this.member = member;
        this.messageQueue = new LinkedList<>();
    }

    @Override
    public void start() {
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Sleep interrupted: " + e.getMessage());
        }

        Thread thread = new Thread(() -> {
            try {
                Message message = new Message("genesis", "PROPOSE");
                Logger.log(Logger.LEADER_ERRORS, "Proposing GENESIS message: " + message.getPayload());
                handleProposeMessage(message);

                Thread.sleep(100);
                Message message2 = new Message("eren", "PROPOSE");
                Logger.log(Logger.LEADER_ERRORS, "Proposing Eren message: " + message2.getPayload());
                handleProposeMessage(message2);

                Thread.sleep(100);
                Message message3 = new Message("yeager", "PROPOSE");
                Logger.log(Logger.LEADER_ERRORS, "Proposing Yeager message: " + message3.getPayload());
                handleProposeMessage(message3);
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Sleep interrupted: " + e.getMessage());
            }
        });

        // Start the thread
        thread.start();
        System.out.println("Saiiiiiiiii");
    }

    @Override
    public void processMessage(String sourceId, AuthenticatedMessage message) {
        System.out.println("Processing message with payload: " + message.getPayload());
        switch (message.getCommand()) {
            case "PROPOSE":
                handleProposeMessage(message);
                break;
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
            default:
                System.out.println("Unknown command: " + message.getCommand());
                break;
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
    public void handleStateMessage(AuthenticatedMessage message) {
        member.getConsensus().handleStateMessage(message);
    }

    @Override
    public void handleProposeMessage(Message message) {
        Logger.log(Logger.LEADER_ERRORS, "PROPOSING MESSAGE ------------------------ " + message.getPayload());
        
        // If the member is already working on a message, queue this one
        if (member.isWorking()) {
            Logger.log(Logger.LEADER_ERRORS, "Already working on a message, queueing: " + message.getPayload());
            messageQueue.add(message);
        } else {
            // Otherwise, start working on this message
            member.setWorking(true);
            member.getConsensus().handleProposeMessage(message);
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
            if (!messageQueue.isEmpty()) {
                // Take the next message from the queue and process it
                Message nextMessage = messageQueue.poll();
                Logger.log(Logger.LEADER_ERRORS, "Processing next queued message: " + nextMessage.getPayload());
                
                // Continue working on the next message
                member.getConsensus().handleProposeMessage(nextMessage);
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
            if (!messageQueue.isEmpty()) {
                // Take the next message from the queue and process it
                Message nextMessage = messageQueue.poll();
                Logger.log(Logger.LEADER_ERRORS, "Processing next queued message after ABORT: " + nextMessage.getPayload());
                
                // Continue working on the next message
                member.getConsensus().handleProposeMessage(nextMessage);
            } else {
                // No more messages to process, set working to false
                member.setWorking(false);
                Logger.log(Logger.LEADER_ERRORS, "No more messages in queue after ABORT, setting working to false");
            }
        }
    }

    // Helper method to get the queue size (useful for testing/logging)
    public int getQueueSize() {
        return messageQueue.size();
    }
}