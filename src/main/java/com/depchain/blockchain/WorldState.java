package com.depchain.blockchain;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    /**
     * Loads the initial world state from a genesis JSON file located on the
     * classpath.
     *
     * @param genesisResourcePath   The path to the genesis file within the
     *                              classpath (e.g., "genesis.json").
     * @param addressToPublicKeyMap A map to resolve string addresses from the JSON
     *                              to PublicKey objects.
     * @return A new WorldState instance initialized from the genesis file.
     * @throws IOException      If the genesis file cannot be found or read.
     * @throws RuntimeException If the JSON parsing fails or data is invalid.
     */
    public static WorldState loadFromGenesis(String genesisResourcePath, Map<String, PublicKey> addressToPublicKeyMap)
            throws IOException {
        Objects.requireNonNull(genesisResourcePath, "Genesis resource path cannot be null");
        Objects.requireNonNull(addressToPublicKeyMap, "Address-to-PublicKey map cannot be null");

        ObjectMapper mapper = new ObjectMapper();
        WorldState genesisWorldState = new WorldState();

        InputStream inputStream = WorldState.class.getClassLoader().getResourceAsStream(genesisResourcePath);
        if (inputStream == null) {
            throw new FileNotFoundException("Cannot find genesis file in classpath: " + genesisResourcePath);
        }

        try (inputStream) { 
            TypeReference<Map<String, Object>> typeRef = new TypeReference<>() {
            };
            Map<String, Object> root = mapper.readValue(inputStream, typeRef);

            @SuppressWarnings("unchecked") 
            Map<String, Map<String, Object>> stateData = (Map<String, Map<String, Object>>) root.get("state");

            if (stateData == null) {
                throw new RuntimeException("Genesis JSON is missing the required 'state' object.");
            }

            // Iterate through each account defined in the "state"
            for (Map.Entry<String, Map<String, Object>> entry : stateData.entrySet()) {
                String addressString = entry.getKey();
                Map<String, Object> accountData = entry.getValue();

                // 1. Resolve PublicKey from the provided map
                PublicKey publicKey = addressToPublicKeyMap.get(addressString);
                if (publicKey == null) {
                    System.err.println("Warning: Address '" + addressString
                            + "' from genesis.json not found in the provided key map. Skipping account.");
                    continue; // Skip this account if we can't find its PublicKey
                }

                // 2. Parse Balance (handle potential NumberFormatException)
                double balance = 0;
                try {
                    balance = Double.parseDouble(Objects.toString(accountData.get("balance"), "0"));
                } catch (NumberFormatException e) {
                    throw new RuntimeException(
                            "Invalid balance format for address '" + addressString + "' in genesis.json", e);
                }

                // 3. Parse Nonce (default to 0 if not specified)
                long nonce = 0;
                if (accountData.containsKey("nonce")) {
                    Object nonceObj = accountData.get("nonce");
                    try {
                        if (nonceObj instanceof Number) {
                            nonce = ((Number) nonceObj).longValue();
                        } else if (nonceObj instanceof String) {
                            nonce = Long.parseLong((String) nonceObj);
                        } else if (nonceObj != null) {
                            // Attempt toString() conversion for flexibility, though less robust
                            nonce = Long.parseLong(nonceObj.toString());
                        }
                    } catch (NumberFormatException e) {
                        System.err.println(
                                "Warning: Invalid nonce format for address '" + addressString + "'. Using default 0.");
                        // Keep nonce = 0
                    }
                }

                // 4. Parse Contract Code (if present)
                byte[] code = null;
                if (accountData.containsKey("code")) {
                    String codeHex = (String) accountData.get("code");
                    if (codeHex != null && !codeHex.isEmpty()) {
                        try {
                            code = hexStringToByteArray(codeHex);
                        } catch (IllegalArgumentException e) {
                            throw new RuntimeException(
                                    "Invalid hex format for 'code' for address '" + addressString + "'", e);
                        }
                    }
                }

                // 5. Parse Contract Storage (if present)
                Map<String, String> storage = new HashMap<>();
                if (accountData.containsKey("storage")) {
                    @SuppressWarnings("unchecked") // Expecting Map<String, Object/String>
                    Map<String, Object> storageData = (Map<String, Object>) accountData.get("storage");
                    if (storageData != null) {
                        for (Map.Entry<String, Object> storageEntry : storageData.entrySet()) {
                            // Assuming storage values are expected to be strings
                            if (storageEntry.getValue() != null) {
                                storage.put(storageEntry.getKey(), storageEntry.getValue().toString());
                            } else {
                                storage.put(storageEntry.getKey(), null); // Or handle null values as needed
                            }
                        }
                    }
                }

                // 6. Create AccountState object
                AccountState accountState = new AccountState(publicKey, balance, nonce, code, storage);

                // 7. Add to the WorldState
                genesisWorldState.putAccount(publicKey, accountState);
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to parse genesis JSON from " + genesisResourcePath, e);
        }

        return genesisWorldState;
    }

    /**
     * Helper utility to convert a hexadecimal string (optionally prefixed with
     * "0x")
     * to a byte array.
     *
     * @param hex The hexadecimal string.
     * @return The corresponding byte array.
     * @throws IllegalArgumentException if the string is not valid hex.
     */
    private static byte[] hexStringToByteArray(String hex) {
        Objects.requireNonNull(hex, "Hex string cannot be null");
        String cleanedHex = hex.startsWith("0x") ? hex.substring(2) : hex;

        int len = cleanedHex.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have an even number of characters: " + hex);
        }
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            try {
                data[i / 2] = (byte) ((Character.digit(cleanedHex.charAt(i), 16) << 4)
                        + Character.digit(cleanedHex.charAt(i + 1), 16));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid hex character in string: " + hex, e);
            }
        }
        return data;
    }

}
