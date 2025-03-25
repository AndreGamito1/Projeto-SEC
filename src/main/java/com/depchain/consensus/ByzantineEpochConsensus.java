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
    private EpochState epochState;
    private List<EpochState> writeset = new ArrayList<>();



    public ByzantineEpochConsensus(Member member, MemberManager memberManager, EpochState epochState, List<EpochState> writeset) {
        this.member = member;
        this.epochState = epochState;
        this.writeset = writeset;
        this.conditionalCollect = new ConditionalCollect(memberManager, this);
    }

    public ByzantineEpochConsensus(Member member, MemberManager memberManager, List<EpochState> writeset) {
        this.member = member;
        this.epochState = new EpochState(0, null);
        this.writeset = writeset;
        this.conditionalCollect = new ConditionalCollect(memberManager, this);
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

    public void handleProposeMessage(Message message) {
        int currentTimestamp = epochState.getTimeStamp();
        Logger.log(Logger.MEMBER, "Current timestamp: " + currentTimestamp);
        int nextTimestamp;
        nextTimestamp = currentTimestamp + 1;
        String value = message.getPayload();
        EpochState proposedState = new EpochState(nextTimestamp, value);
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
        member.addToBlockchain(value);
        setState(value);
    }


    public void handleStateMessage(AuthenticatedMessage message) {
        Logger.log(Logger.LEADER_ERRORS, "Received state message: " + message.getPayload());
        conditionalCollect.appendState(message.getPayload());
    }
}
