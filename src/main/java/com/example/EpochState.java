package com.example;

public class EpochState {

    int epoch;
    String proposedValue;
    boolean decided = false;
    boolean aborted = false;

    EpochState(int epoch) {
        this.epoch = epoch;
    }

    public void setProposedValue(String proposedValue) {
        this.proposedValue = proposedValue;
    }

    @Override
    public String toString() {
        return "(" + epoch + ", " + (proposedValue != null ? proposedValue : "null") + ")";
    }
}
