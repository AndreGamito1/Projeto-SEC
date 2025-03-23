package com.depchain.consensus;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import com.depchain.networking.AuthenticatedMessage;
import com.depchain.networking.Message;
import com.depchain.utils.*;

public class ByzantineEpochConsensus {
    private ConditionalCollect conditionalCollect;
    private Member member;
    private boolean working;
    private EpochState epochState;
    private List<EpochState> writeset = new ArrayList<>();



    public ByzantineEpochConsensus(Member member, MemberManager memberManager, EpochState epochState, List<EpochState> writeset) {
        this.member = member;
        this.epochState = epochState;
        this.writeset = writeset;
        this.working = false;
        this.conditionalCollect = new ConditionalCollect(memberManager, this);
    }

    public ByzantineEpochConsensus(Member member, MemberManager memberManager, List<EpochState> writeset) {
        this.member = member;
        this.epochState = new EpochState(0, null);
        this.writeset = writeset;
        this.working = false;
        this.conditionalCollect = new ConditionalCollect(memberManager, this);
    }

    
    public void start() {
        Logger.log(Logger.MEMBER, "Byzantine Epoch Consensus running!");
    }

    public void setWorking(boolean state) {
        working = state;
    }

    public boolean isWorking() {
        return working;
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

    public void handleProposeMessage(Message message) {
        if(working) { 
            Logger.log(Logger.EPOCH_CONSENSUS, "I AM WORKING I CANT HANDLE PROPOSE MESSAGE");
            return ; }
        int currentTimestamp = epochState.getTimeStamp();
        int nextTimestamp;
        nextTimestamp = currentTimestamp + 1;
        String value = message.getPayload();
        EpochState proposedState = new EpochState(nextTimestamp, value);
        setWorking(true);
        conditionalCollect.input(proposedState);
    }

    public void handleAckMessage(Message message) {
        Logger.log(Logger.MEMBER, "Received ACK message: " + message.getPayload());
        conditionalCollect.appendAck(message.getPayload(), message.getCommand());;
    }

    public void handleCollectedMessage(AuthenticatedMessage message) {
        conditionalCollect.setCollected(message.getPayload());
    }

    public void decide(EpochState value){
        Logger.log(Logger.MEMBER, "Deciding on value: " + value);
        setWorking(false);
        member.addToBlockchain(value);
    }


    public void handleStateMessage(AuthenticatedMessage message) {
        Logger.log(Logger.LEADER_ERRORS, "Received state message: " + message.getPayload());
        conditionalCollect.appendState(message.getPayload());
    }
}
