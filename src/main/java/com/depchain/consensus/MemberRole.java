package com.depchain.consensus;

import com.depchain.utils.*;

import java.util.List;

import com.depchain.blockchain.Block;
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
                switch (message.getCommand()) {
                    case "CMD_KEY_EXCHANGE":
                        break;
                    case "CMD_KEY_EXCHANGE_ACK":
                        break;  
                    case "COLLECTED":
                        handleCollectedMessage(message);
                        break;
                    case "WRITE":
                        handleAckMessage(message);
                        break;
                    case "ACCEPT":
                        handleAckMessage(message);
                        break;
                    case "READ":
                        handleReadMessage(message);
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
        member.getConsensus().handleCollectedMessage(message);
    }

    @Override
    public void handleStateMessage(AuthenticatedMessage message) {
        // Member-specific state message handling
    }

    @Override
    public void ProposeBlock(Block block) {
        // Member-specific block proposal handling
    }

    @Override
    public void handleReadMessage(Message message) {
        member.getConsensus().handleProposeMessage(message.getPayload());
    }

    @Override
    public void handleAckMessage(Message message) {
        Logger.log(Logger.MEMBER, "Received ACK message");
        member.getConsensus().handleAckMessage(message);
    }

    @Override
    public void handleAbortMessage(Message message) {
        Logger.log(Logger.MEMBER, "Received ABORT message");
    }

    @Override
    public void handleDecideMessage(Message message) {
        Logger.log(Logger.MEMBER, "Received DECIDE message");
    }
}