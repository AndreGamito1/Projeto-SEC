package com.depchain.consensus;

// EPochState is a class that represents the state of the epoch
class EpochState {
    private int timeStamp;
    private String value;
    
    public EpochState(int timestamp, String value) {
        this.timeStamp = timestamp;
        this.value = value;
    }

    public int getTimeStamp() {
        return timeStamp;
    }

    public String getValue() {
        return value;
    }

    public void setTimeStamp(int timeStamp) {
        this.timeStamp = timeStamp;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return timeStamp + ", " + value;
    }
}

