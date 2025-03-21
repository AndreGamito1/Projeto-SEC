package com.depchain.consensus;

import com.depchain.utils.*;
import com.depchain.networking.*;

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
        Message message = new Message("genesis", "MOCK");
        Logger.log(Logger.LEADER_ERRORS, "Proposing GENESIS message: " + message.getPayload());
        handleProposeMessage(message);
        
    }

    @Override
    public void processMessage(String sourceId, AuthenticatedMessage message) {
        System.out.println("Processing message with payload: " + message.getPayload());
        switch (message.getCommand()) {
            case "STATE":
                handleStateMessage(message);
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
        member.getConditionalCollect().input(message.getPayload());
    }

}