package com.depchain.blockchain;


import java.util.List;
import java.util.ArrayList;

/**
 * Represents a block in a blockchain.  A block contains a set of transactions,
 * a timestamp, a reference to the previous block, and a hash of its contents.
 */
public class Block {

    private int blockNumber;
    private String previousHash;
    private List<Transaction> transactions;
    private String blockHash;

    /**
     * Constructs a new Block object.
     *
     * @param blockNumber  The block number.
     * @param previousHash The hash of the previous block.
     * @param transactions The list of transactions in the block.
     * @param blockHash    The hash of the block.
     */
    public Block(int blockNumber, String previousHash, List<Transaction> transactions, String blockHash) {
        this.blockNumber = blockNumber;
        this.previousHash = previousHash;
        this.transactions = (transactions != null) ? new ArrayList<>(transactions) : new ArrayList<>(); // Defensive copy in constructor!  Handles null gracefully
        this.blockHash = blockHash;
    }


    /**
     * Returns the block number.
     *
     * @return The block number.
     */
    public int getBlockNumber() {
        return blockNumber;
    }

    /**
     * Sets the block number.
     *
     * @param blockNumber The new block number.
     */
    public void setBlockNumber(int blockNumber) {
        this.blockNumber = blockNumber;
    }

    /**
     * Returns the hash of the previous block.
     *
     * @return The previous block's hash.
     */
    public String getPreviousHash() {
        return previousHash;
    }

    /**
     * Sets the hash of the previous block.
     *
     * @param previousHash The new previous block hash.
     */
    public void setPreviousHash(String previousHash) {
        this.previousHash = previousHash;
    }

    /**
     * Returns a *defensive copy* of the list of transactions in the block.
     * This prevents external code from modifying the block's transaction list directly.
     *
     * @return A new list containing the transactions.
     */
    public List<Transaction> getTransactions() {
        return new ArrayList<>(transactions); // Return a defensive copy!
    }

    /**
     * Sets the list of transactions in the block, creating a *defensive copy* to
     * protect the block's internal state.
     *
     * @param transactions The new list of transactions.
     */
    public void setTransactions(List<Transaction> transactions) {
        this.transactions = (transactions != null) ? new ArrayList<>(transactions) : new ArrayList<>(); // Defensive copy in setter! Handles null.
    }

    /**
     * Returns the hash of the block.
     *
     * @return The block hash.
     */
    public String getBlockHash() {
        return blockHash;
    }

    /**
     * Sets the hash of the block.
     *
     * @param blockHash The new block hash.
     */
    public void setBlockHash(String blockHash) {
        this.blockHash = blockHash;
    }

    /**
     * Returns a string representation of the block.  Useful for debugging.
     *
     * @return A string representation of the block.
     */
    @Override
    public String toString() {
        return "Block{" +
                "blockNumber=" + blockNumber +
                ", previousHash='" + previousHash + '\'' +
                ", transactions=" + transactions +
                ", blockHash='" + blockHash + '\'' +
                '}';
    }
}