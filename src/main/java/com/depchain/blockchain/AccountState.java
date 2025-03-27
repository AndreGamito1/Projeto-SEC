package com.depchain.blockchain;


import java.util.Arrays;
import java.util.HashMap;
import java.security.PublicKey;
import java.util.Map;

/**
 * Represents the state of an account in a blockchain.  This includes the account's
 * balance, nonce, code (for smart contracts), and storage.
 */
public class AccountState {

    private PublicKey address;           // Using PublicKey as the account address
    private double balance;
    private long nonce;                  // Account nonce (transaction count)
    private byte[] code;                 // Smart contract code (if applicable)
    private Map<String, String> storage; // Key-value storage for contracts

    /**
     * Constructs a new AccountState object.
     *
     * @param address The public key of the account.
     * @param balance The account's balance.
     * @param nonce   The account's nonce.
     * @param code    The smart contract code (if applicable).
     * @param storage The key-value storage for the account.
     */
    public AccountState(PublicKey address, double balance, long nonce, byte[] code, Map<String, String> storage) {
        this.address = address;
        this.balance = balance;
        this.nonce = nonce;
        this.code = code;
        this.storage = (storage != null) ? new HashMap<>(storage) : new HashMap<>(); // Defensive copy
    }

     /**
      * Returns the address of the account.
      *
      * @return The account's address (PublicKey).
      */
    public PublicKey getAddress() {
        return address;
    }

    /**
     * Sets the address of the account.
     *
     * @param address The new account address.
     */
    public void setAddress(PublicKey address) {
        this.address = address;
    }


    /**
     * Returns the balance of the account.
     *
     * @return The account's balance.
     */
    public double getBalance() {
        return balance;
    }

    /**
     * Sets the balance of the account.
     *
     * @param balance The new account balance.
     */
    public void setBalance(double balance) {
        this.balance = balance;
    }

    /**
     * Returns the nonce of the account.
     *
     * @return The account's nonce.
     */
    public long getNonce() {
        return nonce;
    }

    /**
     * Sets the nonce of the account.
     *
     * @param nonce The new account nonce.
     */
    public void setNonce(long nonce) {
        this.nonce = nonce;
    }

    /**
     * Returns the smart contract code associated with the account.
     *
     * @return The smart contract code.
     */
    public byte[] getCode() {
        return code;
    }

    /**
     * Sets the smart contract code associated with the account.
     *
     * @param code The new smart contract code.
     */
    public void setCode(byte[] code) {
        this.code = code;
    }

   /**
    * Returns a *defensive copy* of the account's storage.
    *
    * @return A new map containing the account's storage.
    */
    public Map<String, String> getStorage() {
        return new HashMap<>(storage); // Defensive copy
    }

    /**
     * Sets the account's storage, creating a *defensive copy* to protect
     * the account's internal state.
     *
     * @param storage The new account storage.
     */
    public void setStorage(Map<String, String> storage) {
        this.storage = (storage != null) ? new HashMap<>(storage) : new HashMap<>(); // Defensive copy
    }


    /**
     * Returns a string representation of the account state. Useful for debugging.
     *
     * @return A string representation of the account state.
     */
    @Override
    public String toString() {
        return "AccountState{" +
                "address=" + address +
                ", balance=" + balance +
                ", nonce=" + nonce +
                ", code=" + Arrays.toString(code) +
                ", storage=" + storage +
                '}';
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     * Equality is based on the account's address (PublicKey).
     *
     * @param o The object to compare with.
     * @return {@code true} if this object is the same as the {@code o}
     * argument; {@code false} otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AccountState that = (AccountState) o;

        return address.equals(that.address);
    }

    /**
     * Returns a hash code value for the object. The hash code is based on the
     * account's address (PublicKey).
     *
     * @return A hash code value for this object.
     */
    @Override
    public int hashCode() {
        return address.hashCode();
    }
}