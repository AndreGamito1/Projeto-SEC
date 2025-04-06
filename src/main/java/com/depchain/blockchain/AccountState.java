package com.depchain.blockchain;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects; 

/**
 * Represents the state of an account in the blockchain
 */
public class AccountState {
    private final String address;                  
    private final String publicKeyPath;              
    private final String privateKeyPath;             
    private final String name;
    private String balance;                          
    private String code;                             
    private Map<String, String> storage;             

    /**
     * Creates a new account state.
     *
     * @param address        Address of the account 
     * @param publicKeyPath  Path to the public key file 
     * @param privateKeyPath Path to the private key file 
     * @param balance        Account balance as String
     * @param balance        Account name
     */
    public AccountState(String address, String publicKeyPath, String privateKeyPath, String balance, String name) {
        this.address = Objects.requireNonNull(address, "Address cannot be null"); 
        this.publicKeyPath = publicKeyPath;
        this.privateKeyPath = privateKeyPath;
    
    
        this.balance = (balance == null || balance.isEmpty()) ? "0" : balance;
        this.name = name; 
        this.code = null; 
        this.storage = new HashMap<>(); 
    }

    /**
     * Copy constructor for deep copying AccountState.
    * @param other The AccountState to copy.
    */
    public AccountState(AccountState other) {
        Objects.requireNonNull(other, "Cannot copy null AccountState");
        this.address = other.address; 
        this.publicKeyPath = other.publicKeyPath; 
        this.privateKeyPath = other.privateKeyPath; 
        this.balance = other.balance; 
        this.code = other.code; 
        this.name = other.name; 
        if (other.storage != null) {
            this.storage = new HashMap<>(other.storage);
        } else {
            this.storage = new HashMap<>(); 
        }
    }

 
    /**
     * Determines if the account represents a contract.
     * 
     * @return true if the account has associated code and is not empty,
     *         indicating it is a contract; false otherwise.
     */
    public boolean isContract() {
        return code != null && !code.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("AccountState{")
          .append("address='").append(address).append('\'')
          .append(", balance='").append(balance).append('\'');

        if (isContract()) {
            sb.append(", isContract=true")
              .append(", storage=").append(storage.size() > 10 ? storage.size() + " items" : storage);
        }

        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;
         AccountState that = (AccountState) o;
         return Objects.equals(address, that.address); 
     }


     // --- Getters  & Setters---

    public String getAddress() {
        return address;
    }

    public String getPublicKeyPath() {
        return publicKeyPath;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public String getBalance() {
        return balance;
    }

    public String getName() {
        return name;
    }   

    public String getCode() {
        return code;
    }

    public Map<String, String> getStorage() {
        return storage;
    }

    public String getStorageValue(String key) {
        return storage.get(key);
    }
   
    public void setBalance(String balance) {
        this.balance = Objects.requireNonNull(balance, "Balance cannot be set to null");
    }

    public void setBalance(BigInteger balance) {
         Objects.requireNonNull(balance, "Balance cannot be set to null");
         this.balance = balance.toString();
    }

    public void setCode(String code) {
        this.code = code; 
    }

    public void setStorage(Map<String, String> storage) {
        this.storage = (storage == null) ? new HashMap<>() : storage;
    }

    public void setStorageValue(String key, String value) {
         Objects.requireNonNull(key, "Storage key cannot be null");
         if (value == null) {
             storage.remove(key);
         } else {
             storage.put(key, value);
         }
    }

}