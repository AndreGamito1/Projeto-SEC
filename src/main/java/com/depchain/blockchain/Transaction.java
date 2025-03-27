package com.depchain.blockchain;


import java.security.PublicKey;
import java.util.Arrays;

/**
 * Represents a transaction in a blockchain.  A transaction involves the transfer
 * of value or data from a sender to a receiver.
 */
public class Transaction {

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

    /**
     * Returns the public key of the sender.
     *
     * @return The sender's public key.
     */
    public PublicKey getSender() {
        return sender;
    }

    /**
     * Sets the public key of the sender.
     *
     * @param sender The new sender's public key.
     */
    public void setSender(PublicKey sender) {
        this.sender = sender;
    }

    /**
     * Returns the public key of the receiver.
     *
     * @return The receiver's public key.
     */
    public PublicKey getReceiver() {
        return receiver;
    }

    /**
     * Sets the public key of the receiver.
     *
     * @param receiver The new receiver's public key.
     */
    public void setReceiver(PublicKey receiver) {
        this.receiver = receiver;
    }

    /**
     * Returns the amount of value being transferred.
     *
     * @return The value being transferred.
     */
    public double getValue() {
        return value;
    }

    /**
     * Sets the amount of value being transferred.
     *
     * @param value The new value to be transferred.
     */
    public void setValue(double value) {
        this.value = value;
    }

    /**
     * Returns the data associated with the transaction.
     *
     * @return The transaction data.
     */
    public String getData() {
        return data;
    }

    /**
     * Sets the data associated with the transaction.
     *
     * @param data The new transaction data.
     */
    public void setData(String data) {
        this.data = data;
    }

    /**
     * Returns the nonce of the transaction.
     *
     * @return The nonce.
     */
    public long getNonce() {
        return nonce;
    }

    /**
     * Sets the nonce of the transaction.
     *
     * @param nonce The new nonce.
     */
    public void setNonce(long nonce) {
        this.nonce = nonce;
    }

    /**
     * Returns the digital signature of the transaction.
     *
     * @return The digital signature.
     */
    public byte[] getSignature() {
        return signature;
    }

    /**
     * Sets the digital signature of the transaction.
     *
     * @param signature The new digital signature.
     */
    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    /**
     * Returns a string representation of the transaction.  Useful for debugging.
     *
     * @return A string representation of the transaction.
     */
    @Override
    public String toString() {
        return "Transaction{" +
                "sender=" + sender +
                ", receiver=" + receiver +
                ", value=" + value +
                ", data='" + data + '\'' +
                ", nonce=" + nonce +
                ", signature=" + Arrays.toString(signature) +
                '}';
    }
}