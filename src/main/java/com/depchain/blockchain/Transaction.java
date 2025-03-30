package com.depchain.blockchain;

import java.io.*; // Import necessary IO classes
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Base64; // Import Base64

/**
 * Represents a transaction in a blockchain.
 * Includes static methods for serialization to/from Base64 String.
 */
public class Transaction implements Serializable {

    private static final long serialVersionUID = 20250328L; // Example version ID

    private PublicKey sender;
    private PublicKey receiver;
    private double value;
    private String data;
    private long nonce;
    private byte[] signature; // Digital signature of the transaction


    /**
     * Constructs a new Transaction object.
     *
     * @param sender    The public key of the sender.
     * @param receiver  The public key of the receiver.
     * @param value     The amount of value being transferred.
     * @param data      Arbitrary data associated with the transaction.
     * @param nonce     The nonce for this transaction.
     * @param signature The digital signature of the transaction.
     */
    public Transaction(PublicKey sender, PublicKey receiver, double value, String data, long nonce, byte[] signature) {
        this.sender = sender;
        this.receiver = receiver;
        this.value = value;
        this.data = data;
        this.nonce = nonce;
        this.signature = signature;
    }

    // --- Getters and Setters ---

    public PublicKey getSender() {
        return sender;
    }

    public void setSender(PublicKey sender) {
        this.sender = sender;
    }

    public PublicKey getReceiver() {
        return receiver;
    }

    public void setReceiver(PublicKey receiver) {
        this.receiver = receiver;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public long getNonce() {
        return nonce;
    }

    public void setNonce(long nonce) {
        this.nonce = nonce;
    }

    public byte[] getSignature() {
        return (signature == null) ? null : signature.clone();
    }

    public void setSignature(byte[] signature) {
        this.signature = (signature == null) ? null : signature.clone();
    }


    @Override
    public String toString() {
        String senderStr = (sender != null) ? sender.getClass().getSimpleName() + "@" + Integer.toHexString(sender.hashCode()) : "null";
        String receiverStr = (receiver != null) ? receiver.getClass().getSimpleName() + "@" + Integer.toHexString(receiver.hashCode()) : "null";
        return "Transaction{" +
                "sender=" + senderStr + 
                ", receiver=" + receiverStr +
                ", value=" + value +
                ", data='" + data + '\'' +
                ", nonce=" + nonce +
                ", signature=" + ((signature != null) ? Base64.getEncoder().encodeToString(signature) : "null") + // Show signature as Base64
                '}';
    }

    // --- Static Serialization / Deserialization Methods ---

    /**
     * Serializes the given Transaction object into a Base64 encoded String.
     *
     * @param tx The Transaction object to serialize.
     * @return A Base64 encoded String representing the serialized object.
     * @throws IOException If an I/O error occurs during serialization.
     */
    public static String serializeToString(Transaction tx) throws IOException {
        if (tx == null) {
            return null;
        }
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        try (ObjectOutputStream objOut = new ObjectOutputStream(byteOut)) {
            objOut.writeObject(tx);
        } 
        return Base64.getEncoder().encodeToString(byteOut.toByteArray());
    }

    /**
     * Deserializes a Transaction object from a Base64 encoded String.
     *
     * @param base64String The Base64 encoded String. 
     * @return The deserialized Transaction object.
     * @throws IOException            If an I/O error occurs during deserialization.
     * @throws ClassNotFoundException If the Transaction class definition cannot be found.
     * @throws IllegalArgumentException If the input string is null, empty, or not valid Base64.
     */
    public static Transaction deserializeFromString(String base64String) throws IOException, ClassNotFoundException {
        if (base64String == null || base64String.isEmpty()) {
             throw new IllegalArgumentException("Input Base64 string cannot be null or empty.");
        }
        byte[] bytes;
        try {
             bytes = Base64.getDecoder().decode(base64String);
        } catch (IllegalArgumentException e) {
             throw new IllegalArgumentException("Input string is not valid Base64.", e);
        }

        ByteArrayInputStream byteIn = new ByteArrayInputStream(bytes);
        try (ObjectInputStream objIn = new ObjectInputStream(byteIn)) {
            Object obj = objIn.readObject();
            if (obj instanceof Transaction) {
                return (Transaction) obj;
            } else {
                throw new ClassCastException("Deserialized object is not of type Transaction: " + obj.getClass().getName());
            }
        } 
    }

}