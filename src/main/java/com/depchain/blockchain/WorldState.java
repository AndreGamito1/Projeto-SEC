package com.depchain.blockchain;


import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the state of the world in a blockchain.  
 * Manages a map of account states.
 */
public class WorldState {

    private final Map<PublicKey, AccountState> accounts = new HashMap<>();

    /**
     * Gets the account state for the given address.
     *
     * @param address The address of the account.
     * @return The account state, or null if the account does not exist.
     */
    public AccountState getAccount(PublicKey address) {
        return accounts.get(address);
    }

    /**
     * Puts an account state into the world state.
     *
     * @param address      The address of the account.
     * @param accountState The account state to put.
     */
    public void putAccount(PublicKey address, AccountState accountState) {
        accounts.put(address, accountState);
    }

    /**
     * Removes an account from the world state.
     *
     * @param address The address of the account to remove.
     */
    public void removeAccount(PublicKey address) {
        accounts.remove(address);
    }

    /**
     * Returns a copy of the underlying map of accounts in the world state.
     *
     * @return A copy of the accounts map.
     */
    public Map<PublicKey, AccountState> getAccounts() {
        return new HashMap<>(accounts);
    }
}
