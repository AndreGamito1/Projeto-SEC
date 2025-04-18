package com.depchain.utils;


/**
 * Class representing epoch state with value, timestamp, and writeset.
 */
public class EpochState {
    private final long timestamp;
    private final String value;
    private final TimestampValue[] writeSet;
    
    public EpochState(long timestamp, String value, TimestampValue[] writeSet) {
        this.timestamp = timestamp;
        this.value = value;
        this.writeSet = writeSet;
    }
    
    //--- Getters and Setters ---

    public long getTimestamp() {
        return timestamp;
    }
    
    public String getValue() {
        return value;
    }
    
    public TimestampValue[] getWriteSet() {
        return writeSet;
    }
    
}

