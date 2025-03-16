package com.depchain.utils;

/**
 * Class representing a timestamped value.
 */
public class TimestampValue {
    private final long timestamp;
    private final String value;
    
    public TimestampValue(long timestamp, String value) {
        this.timestamp = timestamp;
        this.value = value;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public String getValue() {
        return value;
    }
}
