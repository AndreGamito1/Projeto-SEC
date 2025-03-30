package com.depchain.blockchain;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects; // For null checks

/**
 * Represents the state of an account in the blockchain
 */
public class AccountState {
    private final String address; // Address should be immutable once set
    private final String publicKeyPath; // Path should be immutable once set
    private final String privateKeyPath; // Path should be immutable once set
    private final String name;
    private String balance; // Balance changes
    private String code; // Code can be set for contracts, usually immutable after deploy
    private Map<String, String> storage; // Storage changes

    /**
     * Creates a new account state.
     *
     * @param address        Address of the account (non-null).
     * @param publicKeyPath  Path to the public key file (non-null).
     * @param privateKeyPath Path to the private key file (non-null).
     * @param balance        Account balance as String (non-null, use "0" if none).
     */
    public AccountState(String address, String publicKeyPath, String privateKeyPath, String balance, String name) {
        // Add null checks for critical immutable fields
        this.name = name;
        this.address = Objects.requireNonNull(address, "Address cannot be null");
        this.publicKeyPath = Objects.requireNonNull(publicKeyPath, "Public key path cannot be null");
        this.privateKeyPath = Objects.requireNonNull(privateKeyPath, "Private key path cannot be null");
        this.balance = Objects.requireNonNull(balance, "Balance cannot be null");
        this.storage = new HashMap<>(); // Initialize storage map
    }

     /**
      * Copy constructor for deep copying AccountState.
      * @param other The AccountState to copy.
      */
     public AccountState(AccountState other) {
         Objects.requireNonNull(other, "Cannot copy null AccountState");
         this.address = other.address; // Strings are immutable
         this.publicKeyPath = other.publicKeyPath; // Strings are immutable
         this.privateKeyPath = other.privateKeyPath; // Strings are immutable
         this.balance = other.balance; // Strings are immutable
         this.code = other.code; // Strings are immutable
         this.name = other.name; // Strings are immutable
         // Deep copy the storage map
         if (other.storage != null) {
             this.storage = new HashMap<>(other.storage);
         } else {
             this.storage = new HashMap<>(); // Initialize if source was null
         }
     }


    // --- Getters ---

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

    public BigInteger getBalanceAsBigInteger() {
        // Consider caching BigInteger if performance is critical and balance changes infrequently
         try {
             return new BigInteger(this.balance);
         } catch (NumberFormatException e) {
             System.err.println("Error parsing balance string: " + this.balance + " for address " + this.address);
             // Return zero or throw a runtime exception depending on desired handling
             return BigInteger.ZERO;
         }
    }

    public boolean isContract() {
        return code != null && !code.isEmpty();
    }

    public String getCode() {
        return code;
    }

    public Map<String, String> getStorage() {
        // Return a defensive copy if you want to prevent external modification?
        // return new HashMap<>(storage);
        // For now, returning direct reference:
        return storage;
    }

    public String getStorageValue(String key) {
        return storage.get(key);
    }


    // --- Setters ---

    public void setBalance(String balance) {
        this.balance = Objects.requireNonNull(balance, "Balance cannot be set to null");
    }

    public void setBalance(BigInteger balance) {
         Objects.requireNonNull(balance, "Balance cannot be set to null");
         this.balance = balance.toString();
    }


    public void setCode(String code) {
        // Typically, code is set once during contract deployment.
        // Maybe add logic to prevent changing it later?
        this.code = code; // Allow null to unset?
    }

    public void setStorage(Map<String, String> storage) {
        // Replace the entire map. Consider null handling.
        this.storage = (storage == null) ? new HashMap<>() : storage;
    }

    public void setStorageValue(String key, String value) {
         Objects.requireNonNull(key, "Storage key cannot be null");
         if (value == null) {
             // Decide how to handle null values: remove the key or store null?
             // Storing null might differ from key not existing.
             // EVM usually sets storage to zero/empty bytes if value is zero.
             // Let's remove the key for simplicity here if value is null.
             storage.remove(key);
         } else {
             storage.put(key, value);
         }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("AccountState{")
          .append("address='").append(address).append('\'')
          .append(", balance='").append(balance).append('\'');

        if (isContract()) {
            sb.append(", isContract=true")
              // Limit storage output size if it can be very large
              .append(", storage=").append(storage.size() > 10 ? storage.size() + " items" : storage);
        }

        sb.append('}');
        return sb.toString();
    }

     // Optional: Implement equals() and hashCode() if AccountState objects
     // are stored in sets or used as keys in maps directly.
     // Be careful with mutable fields (balance, storage) in hashCode/equals.
     // Often, only the immutable 'address' is used for equality checks.
     @Override
     public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;
         AccountState that = (AccountState) o;
         return Objects.equals(address, that.address); // Equality based on address only
     }

     @Override
     public int hashCode() {
         return Objects.hash(address); // Hash code based on address only
     }
}