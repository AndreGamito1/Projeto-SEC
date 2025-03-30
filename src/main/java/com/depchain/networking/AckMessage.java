package com.depchain.networking;

import java.io.Serializable;

// Simple class specifically for ACKs, ensuring it's Serializable
public class AckMessage implements Serializable {
    // Recommended: Add a serialVersionUID for version compatibility
    private static final long serialVersionUID = 1L; 

    private final String ackedMessageID;

    public AckMessage(String ackedMessageID) {
        if (ackedMessageID == null || ackedMessageID.trim().isEmpty()) {
            throw new IllegalArgumentException("Acked Message ID cannot be null or empty");
        }
        this.ackedMessageID = ackedMessageID;
    }

    public String getAckedMessageID() {
        return ackedMessageID;
    }

    @Override
    public String toString() {
        return "AckMessage{" +
               "ackedMessageID='" + ackedMessageID + '\'' +
               '}';
    }
}