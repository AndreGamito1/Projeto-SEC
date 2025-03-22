package com.depchain.consensus;

import com.depchain.utils.*;

import java.util.List;

import com.depchain.networking.*;

public class MemberRole implements Role {
    private Member member;

    public MemberRole(Member member) {
        this.member = member;
    }

    @Override
    public void start() {
        Logger.log(Logger.MEMBER, "Member role running " + member.getName() + "!");
        }

    @Override
    public void processMessage(String sourceId, AuthenticatedMessage message) throws Exception {
            System.out.println("Processing message with payload: " + message.getPayload());
                switch (message.getCommand()) {
                    case "CMD_KEY_EXCHANGE":
                        break;
                    case "CMD_KEY_EXCHANGE_ACK":
                        break;  
                    case "COLLECTED":
                        handleCollectedMessage(message);
                        break;

                    case "WRITE":
                        System.out.println("Processing WRITE Message: " + message.getCommand() + " from " + sourceId);
                        handleAckMessage(message);
                        break;
                    case "ACCEPT":
                        System.out.println("Processing ACCEPT Message: " + message.getCommand() + " from " + sourceId);
                        handleAckMessage(message);
                        break;

                    case "READ":
                        System.out.println("Processing READ Message: " + message.getCommand() + " from " + sourceId);
                        handleProposeMessage(message);
                        break;
                    default:
                        System.out.println("Unknown command: " + message.getCommand());
                        break;

                }
            }
        
           
        
    @Override
    public void processClientCommand(String command, String payload) {
        // Member-specific client command processing
    }

    @Override
    public void processMemberMessage(String memberName, String command, String payload, AuthenticatedMessage message) {
        // Member-specific member message processing
    }

    @Override
    public void logReceivedMessagesStatus() {
        // Member-specific logging
    }

    @Override
    public void handleCollectedMessage(AuthenticatedMessage message) {
        member.getConditionalCollect().setCollected(message.getPayload());
    }

    @Override
    public void handleStateMessage(AuthenticatedMessage message) {
        // Member-specific state message handling
    }

    @Override
    public void handleProposeMessage(Message message) {
        member.getConditionalCollect().input(message.getPayload());
    }

    @Override
    public void handleAckMessage(Message message) {
        Logger.log(Logger.MEMBER, "Received ACK message: " + message.getPayload());
        member.getConditionalCollect().appendAck(message.getPayload(), message.getCommand());;
    }
}