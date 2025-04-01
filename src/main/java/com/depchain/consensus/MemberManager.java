package com.depchain.consensus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.*;

import javax.crypto.SecretKey;

import org.json.JSONObject;

import com.depchain.utils.*;
import com.depchain.networking.*;

public class MemberManager {
    private List<String> members = new ArrayList<>();                             //Array containing the names of all the members
    private Map<String, Integer> memberPorts = new HashMap<>();                   //The port number for each member
    private Map<String, AuthenticatedPerfectLinks> memberLinks = new HashMap<>(); //The authenticated links to each member
    private Map<String, Integer> memberIndices = new HashMap<>();                 

    private String leaderName;
    private String name;                                                            //The name of the member
    private KeyManager keyManager;                                                  //Holds the information about the public and private keys of the members
    private String setupFilePath = "src/main/resources/setup.json";                 // Shared Resources Path
    private int clientLibraryPort;

    public MemberManager(String name) {
        try {
            this.keyManager = new KeyManager(name);
            this.name = name;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Map<String, AuthenticatedPerfectLinks> getMemberLinks() {
        return memberLinks;
    }

    public String getLeaderName() {
        return leaderName;
    }

    public String getName() {
        return name;
    }

    public boolean isLeader() {
        return name.equals(leaderName);
    }

    public Map<String, Integer> getMemberPorts() {
        return memberPorts;
    }


    public int getClientLibraryPort() {
        return clientLibraryPort;
    }

    public void setupMemberLinks() throws Exception {
        members = JsonToList();
        System.out.println("Members: " + members);
        int currentLocalPort = clientLibraryPort;
        // Set up links to each member
        if (!name.equals("clientLibrary")) {
            currentLocalPort = memberPorts.get(this.name);
        }

        for (String member : members) {
            if (!member.equals(this.name)) {
                    int remotePort = memberPorts.get(member);     
                    Logger.log(Logger.MEMBER, "Setting up link to " + member + " on port " + remotePort + "from port " + currentLocalPort);
                    memberLinks.put(member, new AuthenticatedPerfectLinks("localhost", remotePort, currentLocalPort, member, 
                    keyManager.getPublicKey(member),
                    keyManager.getPrivateKey(this.name)));
                    currentLocalPort++;
            }
        }
    }

    /**
     * Sends a message to a specific member.
     * 
     * @param memberName The name of the member
     * @param payload The message payload
     * @param command The command to execute
     * @throws Exception If sending fails
     */
    public void sendToMember(String memberName, String payload, String command){
        try {
            if (!memberLinks.containsKey(memberName)) {
                Logger.log(Logger.MEMBER, "No link to member " + memberName);
                return;
            }
    
            memberLinks.get(memberName).sendMessage(payload, command);
            Logger.log(Logger.MEMBER, "Sent message to " + memberName + ": command=\"" + command + "\"");
        } catch (Exception e) {
            Logger.log(Logger.MEMBER, "Error sending message to " + memberName + ": " + e.getMessage());
        }
    }

    public Message handleNewMessage(String sourceId, AuthenticatedMessage authMessage) {
     try {
            String encryptedPayload = authMessage.getPayload();
            String encryptedCommand = authMessage.getCommand();
            Message decryptedMessage;

            PrivateKey privateKey = keyManager.getPrivateKey(sourceId);
            String decryptedPayload = Encryption.decryptWithRsa(encryptedPayload, privateKey);
            String decryptedCommand = Encryption.decryptWithRsa(encryptedCommand, privateKey);
            String decryptedAesKey = Encryption.decryptWithRsa(authMessage.getAesKey(), privateKey);
            decryptedMessage = new Message(decryptedPayload, decryptedCommand, decryptedAesKey);

            Logger.log(Logger.MEMBER, "Received message from " + sourceId + ": command=\"" + decryptedMessage.getCommand() + "\"");
            return decryptedMessage;
        } catch (Exception e) {
            Logger.log(Logger.MEMBER, "Error handling message from " + sourceId + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public int getQuorumSize(){ 
       int quorumSize = (members.size() / 2) + 1;
       return quorumSize;
    }

    private List<String> JsonToList() {
        List<String> nodes = new ArrayList<>();
        try {
            String content = new String(Files.readAllBytes(Paths.get(setupFilePath)));
            JSONObject jsonObject = new JSONObject(content).getJSONObject("setup");
            clientLibraryPort = jsonObject.getJSONObject("clientLibrary").getInt("port");
            leaderName = jsonObject.getString("leader");
            Logger.log(Logger.MEMBER, "Leader is " + leaderName);
            for (String key : jsonObject.keySet()) {
                if (!(key.equals("clientLibrary") || key.equals("leader"))) {
                    JSONObject memberData = jsonObject.getJSONObject(key);
                    nodes.add(key);
                    memberPorts.put(key, memberData.getInt("port"));
            }
        }
        } catch (IOException e) {
            System.out.println("Error reading setup file: " + e.getMessage());
        }
        return nodes;
    }


  
}
