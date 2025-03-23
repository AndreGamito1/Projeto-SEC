package com.depchain.consensus;

import com.depchain.utils.Logger;
import com.depchain.networking.Message;
import com.depchain.networking.AuthenticatedMessage;

public class LeaderRole implements Role {
    private Member member;

    public LeaderRole(Member member) {
        this.member = member;
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
                AuthenticatedMessage authenticatedMessage = new AuthenticatedMessage(message, "genesis");
                Logger.log(Logger.LEADER_ERRORS, "Proposing GENESIS message: " + message.getPayload());
                handleProposeMessage(message);

                Message message2 = new Message("eren", "PROPOSE");
                AuthenticatedMessage authenticatedMessage2 = new AuthenticatedMessage(message2, "eren");
                Logger.log(Logger.LEADER_ERRORS, "Proposing Eren message: " + message2.getPayload());
                member.insertMessageForTesting("member2", authenticatedMessage2);

                Message message3 = new Message("yeager", "PROPOSE");
                AuthenticatedMessage authenticatedMessage3 = new AuthenticatedMessage(message3, "yeager");
                Logger.log(Logger.LEADER_ERRORS, "Proposing Eren message: " + message3.getPayload());
                member.insertMessageForTesting("member3", authenticatedMessage3);
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
        Logger.log(Logger.LEADER_ERRORS, "Received state message: " + message.getPayload());
        member.getConditionalCollect().appendState(message.getPayload());
    }

    @Override
    public void handleProposeMessage(Message message) {
        member.setWorking(true);
        member.getConditionalCollect().input(message.getPayload());
    }

    @Override
    public void handleAckMessage(Message message) {
        member.getConditionalCollect().appendAck(message.getPayload(), message.getCommand());
    }

    @Override
    public void handleDecideMessage(Message message) {
        Logger.log(Logger.MEMBER, "Received DECIDE message: " + message.getPayload());
        member.getQuorumDecideMessages().add(message);
        Logger.log(Logger.MEMBER,"Quorum size for DECIDE's: " + member.getQuorumSize());
        if (member.getQuorumDecideMessages().size() == member.getQuorumSize()) {
            Logger.log(Logger.MEMBER, "Received quorum of DECIDE messages");
            member.setWorking(false);
            member.getQuorumDecideMessages().clear();
            Logger.log(Logger.LEADER_ERRORS, "BLOCKCHAIN: "+ member.getBlockchain());
        }
    }

    @Override
    public void handleAbortMessage(Message message) {
        Logger.log(Logger.MEMBER, "Received ABORT message: " + message.getPayload());
        member.getQuorumAbortMessages().add(message);
        Logger.log(Logger.MEMBER,"Quorum size for ABORTS's: " + member.getQuorumSize());
        if (member.getQuorumAbortMessages().size() == member.getQuorumSize()) {
            Logger.log(Logger.MEMBER, "Received quorum of ABORT messages");
            member.setWorking(false);
            Logger.log(Logger.LEADER_ERRORS, "BLOCKCHAIN: "+ member.getBlockchain());
        }
    }

}