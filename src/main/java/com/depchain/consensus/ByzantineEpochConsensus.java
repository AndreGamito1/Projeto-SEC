package com.depchain.consensus;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

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

}
