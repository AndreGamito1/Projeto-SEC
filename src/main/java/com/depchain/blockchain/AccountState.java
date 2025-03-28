package com.depchain.blockchain;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the state of an account in the blockchain
 */
public class AccountState {
    private String address;
    private String publicKeyPath;
    private String privateKeyPath;
    private String balance;
    private String code; // For contract accounts
    private Map<String, String> storage; // For contract accounts

    /**
     * Create a new account state
     * 
     * @param address Address of the account
     * @param publicKeyPath Path to the public key file
     * @param privateKeyPath Path to the private key file
     * @param balance Account balance
     */
    public AccountState(String address, String publicKeyPath, String privateKeyPath, String balance) {
        this.address = address;
        this.publicKeyPath = publicKeyPath;
        this.privateKeyPath = privateKeyPath;
        this.balance = balance;
        this.storage = new HashMap<>();
    }

    /**
     * @return the account address
     */
    public String getAddress() {
        return address;
    }

    /**
     * @return the path to the public key file
     */
    public String getPublicKeyPath() {
        return publicKeyPath;
    }

    /**
     * @return the path to the private key file
     */
    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    /**
     * @return the account balance as a string
     */
    public String getBalance() {
        return balance;
    }

    /**
     * @return the account balance as a BigInteger
     */
    public BigInteger getBalanceAsBigInteger() {
        return new BigInteger(balance);
    }

    /**
     * Set the account balance
     * 
     * @param balance New balance
     */
    public void setBalance(String balance) {
        this.balance = balance;
    }

    /**
     * @return true if this is a contract account
     */
    public boolean isContract() {
        return code != null && !code.isEmpty();
    }

    /**
     * @return the contract code or null if not a contract
     */
    public String getCode() {
        return code;
    }

    /**
     * Set the contract code
     * 
     * @param code Contract bytecode
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * @return the contract storage map
     */
    public Map<String, String> getStorage() {
        return storage;
    }

    /**
     * Set the contract storage
     * 
     * @param storage Contract storage map
     */
    public void setStorage(Map<String, String> storage) {
        this.storage = storage;
    }

    /**
     * Get a value from contract storage
     * 
     * @param key Storage key
     * @return Storage value or null if not found
     */
    public String getStorageValue(String key) {
        return storage.get(key);
    }

    /**
     * Set a value in contract storage
     * 
     * @param key Storage key
     * @param value Storage value
     */
    public void setStorageValue(String key, String value) {
        storage.put(key, value);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("AccountState{")
          .append("address='").append(address).append('\'')
          .append(", balance='").append(balance).append('\'');
        
        if (isContract()) {
            sb.append(", isContract=true")
              .append(", storage=").append(storage);
        }
        
        sb.append('}');
        return sb.toString();
    }
}