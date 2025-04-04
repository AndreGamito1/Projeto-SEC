package com.depchain.consensus;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import com.depchain.blockchain.Block;
import com.depchain.utils.*;

/**
 * Byzantine implementation of the Conditional Collect primitive
 * Can exhibit various Byzantine behaviors based on configuration
 */
public class ByzantineConditionalCollect extends ConditionalCollect {
    
    public enum ByzantineBehavior {
        NORMAL,     // Non-Byzantine behavior (follows protocol)
        YES_MAN,    // Always votes yes on leader's value
        NO_MAN,     // Always aborts when receiving proposals
        RANDOM,     // Randomly says yes or no to proposals
        OMIT        // Sometimes omits votes entirely
    }
    
    private final ByzantineBehavior behavior;
    private final MemberManager memberManager;
    private final ByzantineEpochConsensus epochConsensus;
    private Map<String, EpochState> collected = new HashMap<>();
    private Map<String, String> writeAcks = new HashMap<>();
    private Map<String, String> acceptAcks = new HashMap<>();
    private boolean isCollected = false;
    private boolean writeAcked = false;
    private boolean acceptAcked = false;
    private final String name;
    private final Random random = new Random();
    
    /**
     * Creates a new ByzantineConditionalCollect instance with specified Byzantine behavior
     * 
     * @param memberManager The manager to handle communications with members
     * @param epochConsensus The Byzantine epoch consensus instance
     * @param behaviorString The Byzantine behavior to exhibit as a string
     */
    public ByzantineConditionalCollect(MemberManager memberManager, ByzantineEpochConsensus epochConsensus, String behaviorString) {
        super(memberManager, epochConsensus);
        this.memberManager = memberManager;
        this.epochConsensus = epochConsensus;
        
        // Convert string to enum
        ByzantineBehavior parsedBehavior;
        try {
            parsedBehavior = ByzantineBehavior.valueOf(behaviorString.toUpperCase());
        } catch (IllegalArgumentException e) {
            Logger.log(Logger.CONDITIONAL_COLLECT, "Invalid behavior string: " + behaviorString + ". Defaulting to NORMAL behavior.");
            parsedBehavior = ByzantineBehavior.NORMAL;
        }
        
        this.behavior = parsedBehavior;
        this.name = memberManager.getName();
        Logger.log(Logger.CONDITIONAL_COLLECT, "Created ByzantineConditionalCollect with behavior: " + behavior);
    }
    
    @Override
    public void setCollected(String payload) {
        if (behavior == ByzantineBehavior.NORMAL) {
            super.setCollected(payload);
            return;
        }
        
        if (behavior == ByzantineBehavior.NO_MAN) {
            // NoMan immediately aborts when receiving a collected message
            Logger.log(Logger.CONDITIONAL_COLLECT, "NO_MAN behavior: aborting on collected message");
            abort();
            return;
        }
        
        // For other behaviors, process normally but prepare for Byzantine actions later
        collected = parseCollectedPayload(payload);
        isCollected = true;
        
        // Random behavior might randomly abort here
        if (behavior == ByzantineBehavior.RANDOM && random.nextBoolean()) {
            Logger.log(Logger.CONDITIONAL_COLLECT, "RANDOM behavior: decided to abort on collected message");
            abort();
        }
    }
    
    @Override
    public void appendState(String statePayload) {
        if (behavior == ByzantineBehavior.NORMAL) {
            super.appendState(statePayload);
            return;
        }
        
        // For Byzantine behaviors, we might manipulate the state or ignore it
        switch (behavior) {
            case NO_MAN:
                // NoMan doesn't append anything, effectively ignoring the state
                Logger.log(Logger.CONDITIONAL_COLLECT, "NO_MAN behavior: ignoring append state");
                break;
                
            case OMIT:
                // Omit behavior sometimes ignores the state
                if (random.nextDouble() < 0.5) {
                    Logger.log(Logger.CONDITIONAL_COLLECT, "OMIT behavior: ignoring append state");
                } else {
                    Map<String, EpochState> state = parseCollectedPayload(statePayload);
                    collected.putAll(state);
                    if (checkQuorum(collected)) {
                        isCollected = true;
                    }
                }
                break;
                
            case RANDOM:
                // Random behavior might manipulate the state or process normally
                if (random.nextBoolean()) {
                    Map<String, EpochState> state = parseCollectedPayload(statePayload);
                    collected.putAll(state);
                    if (checkQuorum(collected)) {
                        isCollected = true;
                    }
                } else {
                    Logger.log(Logger.CONDITIONAL_COLLECT, "RANDOM behavior: manipulating state");
                    // Potentially corrupt the state by adding a fake entry
                    collected.put("fake-member-" + random.nextInt(100), new EpochState(0, "fake-value"));
                }
                break;
                
            case YES_MAN:
                // YesMan processes normally but will always agree later
                Map<String, EpochState> state = parseCollectedPayload(statePayload);
                collected.putAll(state);
                if (checkQuorum(collected)) {
                    isCollected = true;
                }
                break;
        }
    }
    
    @Override
    public void appendAck(String ackPayload, String ackType) {
        if (behavior == ByzantineBehavior.NORMAL) {
            super.appendAck(ackPayload, ackType);
            return;
        }
        
        // For Byzantine behaviors, we might manipulate acknowledgments
        switch (behavior) {
            case NO_MAN:
                // NoMan doesn't append any acks
                Logger.log(Logger.CONDITIONAL_COLLECT, "NO_MAN behavior: ignoring " + ackType + " ack");
                break;
                
            case OMIT:
                // Omit behavior sometimes ignores acks
                if (random.nextDouble() < 0.5) {
                    Logger.log(Logger.CONDITIONAL_COLLECT, "OMIT behavior: ignoring " + ackType + " ack");
                } else {
                    processAck(ackPayload, ackType);
                }
                break;
                
            case RANDOM:
                // Random behavior might manipulate acks or process normally
                if (random.nextBoolean()) {
                    processAck(ackPayload, ackType);
                } else {
                    Logger.log(Logger.CONDITIONAL_COLLECT, "RANDOM behavior: manipulating " + ackType + " ack");
                    // Corrupt the ack by adding fake entries
                    Map<String, String> ack = parseAck(ackPayload);
                    if (ackType.equals("ACCEPT")) {
                        acceptAcks.putAll(ack);
                        acceptAcks.put("fake-member-" + random.nextInt(100), "fake-ack");
                    } else if (ackType.equals("WRITE")) {
                        writeAcks.putAll(ack);
                        writeAcks.put("fake-member-" + random.nextInt(100), "fake-ack");
                    }
                }
                break;
                
            case YES_MAN:
                // YesMan always processes acks normally
                processAck(ackPayload, ackType);
                break;
        }
    }
    
    private void processAck(String ackPayload, String ackType) {
        if (ackType.equals("ACCEPT")) {
            if (acceptAcked) { return; }
            Map<String, String> ack = parseAck(ackPayload);
            acceptAcks.putAll(ack);
        } else if (ackType.equals("WRITE")) {
            if (writeAcked) { return; }
            Map<String, String> ack = parseAck(ackPayload);
            writeAcks.putAll(ack);
        }
    }
    
    @Override
    public void input(EpochState message) {
        if (behavior == ByzantineBehavior.NORMAL) {
            super.input(message);
            return;
        }
        
        try {
            // For Byzantine behaviors, we might modify input behavior
            switch (behavior) {
                case NO_MAN:
                    // NoMan pretends to participate but will abort later
                    if (memberManager.isLeader()) {
                        collected.put(memberManager.getName(), message);
                        // Send READ message to all members
                        for (String member : memberManager.getMemberLinks().keySet()) {
                            memberManager.sendToMember(member, "", "READ");
                        }
                        // Wait for replies but plan to abort later
                        Thread collectionThread = new Thread(() -> {
                            waitForStates();
                        });
                        collectionThread.start();
                    } else {
                        // Respond to leader with manipulated state
                        Logger.log(Logger.CONDITIONAL_COLLECT, "NO_MAN behavior: sending manipulated state to leader");
                        memberManager.sendToMember(memberManager.getLeaderName(), "fake-state", "STATE");
                        Thread collectionThread = new Thread(() -> {
                            // Wait briefly, then abort
                            try {
                                Thread.sleep(2000);
                                abort();
                            } catch (InterruptedException e) {
                                Logger.log(Logger.CONDITIONAL_COLLECT, "Error in NoMan wait: " + e.getMessage());
                            }
                        });
                        collectionThread.start();
                    }
                    break;
                    
                case OMIT:
                    // Omit behavior sometimes doesn't send any message
                    if (random.nextDouble() < 0.5) {
                        Logger.log(Logger.CONDITIONAL_COLLECT, "OMIT behavior: omitting input response");
                        // Do nothing - omit the response
                    } else {
                        // Behave normally
                        normalInput(message);
                    }
                    break;
                    
                case RANDOM:
                    // Random behavior might input correct state or corrupted state
                    if (random.nextBoolean()) {
                        normalInput(message);
                    } else {
                        Logger.log(Logger.CONDITIONAL_COLLECT, "RANDOM behavior: sending corrupted input");
                        // Create corrupted message
                        EpochState corruptedMessage = new EpochState(message.getTimeStamp(), "corrupted-value");
                        normalInput(corruptedMessage);
                    }
                    break;
                    
                case YES_MAN:
                    // YesMan always behaves normally for input
                    normalInput(message);
                    break;
            }
        } catch (Exception e) {
            Logger.log(Logger.CONDITIONAL_COLLECT, "Error in ByzantineConditionalCollect.input: " + e.getMessage());
        }
    }
    
    private void normalInput(EpochState message) {
        if (memberManager.isLeader()) {
            collected.put(memberManager.getName(), message);
            // Send READ message to all members
            for (String member : memberManager.getMemberLinks().keySet()) {
                memberManager.sendToMember(member, "", "READ");
            }
            // Wait for the STATE replies from each member
            Thread collectionThread = new Thread(() -> {
                waitForStates();
            });
            collectionThread.start();
        } else {
            // Send to leader the STATE message
            String statePayload = createCollectedPayload(collected);
            Logger.log(Logger.CONDITIONAL_COLLECT, "Sending state to leader");
            memberManager.sendToMember(memberManager.getLeaderName(), statePayload, "STATE");
            
            // Start waitForCollected in a new thread
            Thread collectionThread = new Thread(() -> {
                waitForStates();
            });
            collectionThread.start();
        }
    }


    private String createCollectedPayload(Map<String, EpochState> collected) {
    StringBuilder payload = new StringBuilder();
    boolean first = true;

    for (Map.Entry<String, EpochState> entry : collected.entrySet()) {
    if (!first) {
        payload.append(", ");
    } else {
        first = false;
    }

    payload.append(entry.getKey())
        .append(": [");
        
    // Check if the value is null
    if (entry.getValue() != null) {
        payload.append(entry.getValue().toString());
    }

    payload.append("]");
    }

    if (payload.toString().isEmpty()) {
    return memberManager.getName() + ": []";
    }

    return payload.toString();
    }

    @Override
    public Map<String, String> parseAck(String ackPayload) {
    Map<String, String> result = new HashMap<>();

    if (ackPayload != null && !ackPayload.isEmpty()) {
    // Split the payload by newline to handle multiple entries
    String[] entries = ackPayload.split("\n");

    for (String entry : entries) {
        // Check if the entry contains a colon
        if (entry.contains(":")) {
            // Split by the first colon to separate member name and value
            String[] parts = entry.split(":", 2);
            
            if (parts.length == 2) {
                String memberName = parts[0].trim();
                String value = parts[1].trim();
                
                // Extract the value from between square brackets if present
                if (value.startsWith("[") && value.endsWith("]")) {
                    value = value.substring(1, value.length() - 1);
                }
                
                result.put(memberName, value);
            }
        }
    }
    }

    return result;
    }

    @Override
    public String createAck(EpochState value) {
    // Format as "membername: [value]"
    StringBuilder builder = new StringBuilder();
    builder.append(name).append(": [");

    // Add the value if it's not null
    if (value != null) {
    builder.append(value.toString());
    }

    builder.append("]");

    return builder.toString();
    }
    }