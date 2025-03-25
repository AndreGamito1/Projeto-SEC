package com.depchain.consensus;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import javax.crypto.SecretKey;

import com.depchain.networking.*;
import com.depchain.utils.*;

public class Member {
    private Role currentRole;
    protected Map<String, SecretKey> memberKeys;
    protected Map<String, AuthenticatedPerfectLinks> memberLinks;
    private MemberManager memberManager;
    private String name;

    private List<EpochState> blockchain;
    private List<Message> quorumDecideMessages;
    private List<Message> quorumAbortMessages;
    private boolean working;
    private ByzantineEpochConsensus epochConsensus;
    

    public Member(String name) throws Exception {
        this.name = name; 
        this.memberManager = new MemberManager(name);
        this.working = false;
        this.quorumAbortMessages = new ArrayList<>();
        this.quorumDecideMessages = new ArrayList<>();
        this.epochConsensus = new ByzantineEpochConsensus(this, memberManager, blockchain);
        System.out.println("Member created: " + name);

        setupMemberLinks();

        if (memberManager.isLeader()) {
            currentRole = new LeaderRole(this);
        }
        else {
            currentRole = new MemberRole(this);
        }
        this.blockchain = new ArrayList<>();
        start();
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
}