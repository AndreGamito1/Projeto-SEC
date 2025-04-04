package com.depchain.consensus;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.depchain.blockchain.WorldState;
import com.depchain.networking.AuthenticatedMessage;
import com.depchain.networking.Message;
import com.depchain.utils.*;

public class ByzantineEpochConsensus {
    private ConditionalCollect conditionalCollect;
    private MemberManager memberManager;
    private Member member;
    private EpochState epochState;
    private List<EpochState> writeset = new ArrayList<>();
    private WorldState worldState;
    private List<String> balanceList = new ArrayList<>();
    private boolean balancesColletected = false;



    public ByzantineEpochConsensus(Member member, MemberManager memberManager, EpochState epochState, WorldState worldState) {
        this.member = member;
        this.epochState = epochState;
        this.writeset = new ArrayList<>();
        this.memberManager = memberManager;
        this.conditionalCollect = null;
        this.worldState = worldState;
    }

    public ByzantineEpochConsensus(Member member, MemberManager memberManager, WorldState worldState) {
        this.member = member;
        this.epochState = new EpochState(0, null);
        this.writeset = new ArrayList<>();
        this.conditionalCollect = null;
        this.memberManager = memberManager;
        this.worldState = worldState;
    }

    
    public void start() {
        Logger.log(Logger.MEMBER, "Byzantine Epoch Consensus running!");
    }
    
    public void setState(EpochState state) {
        epochState = state;
    }

    public EpochState getState() {
        return epochState;
    }

    public void appendToWriteset(EpochState state) {
        writeset.add(state);
    }

    public void handleProposeMessage(String serializedBlock) {
        this.conditionalCollect = new ConditionalCollect(memberManager, this);
        System.out.println("---------------------- STARTED COND COLLECT ----------------------");
        int currentTimestamp = epochState.getTimeStamp();
        Logger.log(Logger.EPOCH_CONSENSUS, "Current timestamp: " + currentTimestamp);
        int nextTimestamp;
        nextTimestamp = currentTimestamp + 1;
        EpochState proposedState = new EpochState(nextTimestamp, serializedBlock);
        conditionalCollect.input(proposedState);
    }

    public void handleAckMessage(Message message) {
        if (conditionalCollect != null){
            Logger.log(Logger.EPOCH_CONSENSUS, "Received ACK message: " + message.getCommand());
            conditionalCollect.appendAck(message.getPayload(), message.getCommand());;
        }
        else { System.out.println("Received Ack but conditional collect is null"); }
    }

    public void handleCollectedMessage(AuthenticatedMessage message) {
        if (conditionalCollect != null) {
            conditionalCollect.setCollected(message.getPayload());
        }
        else { System.out.println("Received Collected but conditional collect is null"); }
    }

    public void decide(EpochState state){
        Logger.log(Logger.EPOCH_CONSENSUS, "Deciding on value: " + state);
        conditionalCollect = null; // reset the conditional collect
        System.out.println("---------------------- CLOSED COND COLLECT ----------------------");
        member.addToBlockchain(state.getValue());
        setState(state);
    }


    public void handleStateMessage(AuthenticatedMessage message) {
        if (conditionalCollect != null) {
            Logger.log(Logger.LEADER_ERRORS, "Received state message: " + message.getPayload());
            conditionalCollect.appendState(message.getPayload());
        }
        else { System.out.println("Received State but conditional collect is null"); }
    }

    public void abort() {
        Logger.log(Logger.EPOCH_CONSENSUS, "Aborting consensus process");
        conditionalCollect = null; // reset the conditional collect
        System.out.println("---------------------- ABORTED COND COLLECT ----------------------");
    }

    public void setWorldState(WorldState worldState) {
        this.worldState = worldState;
    }
    public WorldState getWorldState() {
        return worldState;
    }
    
    public void addToBalanceList(String balance) {
        this.balanceList.add(balance);
        if (balanceList.size() >= memberManager.getQuorumSize()) {
            balancesColletected = true;
        }
    }

    public List<String> getBalanceList() {
        return balanceList;
    }

    public void clearBalanceList() {
        this.balanceList.clear();
    }

    
    public void getBalanceConsensus(String senderId) {
        try {
            for (String member : memberManager.getMemberLinks().keySet()) {
                memberManager.sendToMember(member, senderId, "GET_BALANCE");
            }
            waitForBalances(senderId);
        } catch (Exception e) {
            System.out.println("Error getting world state consensus: " + e.getMessage());
            return;
        }
    }

    public void waitForBalances(String senderId){
        for (int i = 0; i < 12; i++) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (balancesColletected) { break; }
        }
        if (balancesColletected) {
            String consensusBalance = checkConsensus(balanceList);
            if (consensusBalance != null) {
                memberManager.sendToClientLibrary(consensusBalance, "BALANCE");
            } else {
                System.out.println("No consensus reached on world state.");
            }
        } else {
            System.out.println("World states not collected in time.");
        }
    }

        // Helper method to check if there is consensus among the collected balances
    private String checkConsensus(List<String> balances) {
        if (balances == null || balances.isEmpty()) {
            return null;
        }
        
        // Group identical balances and count them
        Map<String, Integer> balanceCounts = new HashMap<>();
        
        for (String balance : balances) {
            balanceCounts.put(balance, balanceCounts.getOrDefault(balance, 0) + 1);
        }
        
        // Find the balance with the most votes
        String consensusBalance = null;
        int maxCount = 0;
        
        for (Map.Entry<String, Integer> entry : balanceCounts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                consensusBalance = entry.getKey();
            }
        }
        
        // Return the consensus balance if it meets the quorum threshold
        if (maxCount >= memberManager.getQuorumSize()) {
            System.out.println("Consensus reached on balance: " + consensusBalance);
            return consensusBalance;
        } else {
            return null; // No consensus reached
        }
    }

}
