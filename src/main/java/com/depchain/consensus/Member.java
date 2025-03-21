package com.depchain.consensus;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.crypto.SecretKey;

import com.depchain.networking.*;
import com.depchain.utils.*;

public class Member {
    private Role currentRole;
    protected Map<String, SecretKey> memberKeys;
    protected Map<String, AuthenticatedPerfectLinks> memberLinks;
    private MemberManager memberManager;
    private String name;
    private ConditionalCollect conditionalCollect;
    

    public Member(String name) throws Exception {
        this.name = name; 
        System.out.println("Member created: " + name);
        this.memberManager = new MemberManager(name);
        setupMemberLinks();

        if (memberManager.isLeader()) {
            currentRole = new LeaderRole(this);
        }
        else {
            currentRole = new MemberRole(this);
        }
        
        this.conditionalCollect = new ConditionalCollect(memberManager);
        start();
        Logger.getLogger(Member.class.getName()).info("Member " + name + " creation finished");
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
                            System.out.println("Received message: " + message.getPayload());
                            processMessage(link.getDestinationEntity(), message);
                        }
                    }
                }
                catch (Exception e) {
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
        // Send a test message to each member
        for (String member : memberLinks.keySet()) {
            memberManager.sendToMember(member, "Hello friend", "TEST");
        }
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

    public ConditionalCollect getConditionalCollect() {
        return conditionalCollect;
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

}