package com.depchain.blockchain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class WorldState {
    private Map<String, AccountState> accounts;
    private static final String GENESIS_BLOCK_PATH = "src/main/resources/genesisBlock.json";
    private static final String ACCOUNTS_FILE_PATH = "src/main/resources/accounts.json";
    private static final String KEYS_DIRECTORY = "src/main/resources/generated_keys";

    public WorldState() {
        this.accounts = new HashMap<>();
    }

    public Map<String, AccountState> getAccounts() {
        return accounts;
    }

    public AccountState getAccount(String address) {
        return accounts.get(address);
    }

    public void addAccount(AccountState accountState) {
        accounts.put(accountState.getAddress(), accountState);
    }

    /**
     * Loads the genesis state from the genesisBlock.json file
     * and initializes the WorldState with the account data.
     */
    public void loadGenesisState() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        
        // Ensure the keys directory exists
        File keysDir = new File(KEYS_DIRECTORY);
        if (!keysDir.exists()) {
            keysDir.mkdirs();
        }
        
        // Load the accounts file if it exists
        Map<String, AccountInfo> accountsMap = loadAccountsFile();
        
        // Load the genesis block
        File genesisFile = new File(GENESIS_BLOCK_PATH);
        if (!genesisFile.exists()) {
            throw new IOException("Genesis block file not found at: " + GENESIS_BLOCK_PATH);
        }
        
        JsonNode rootNode = mapper.readTree(genesisFile);
        JsonNode stateNode = rootNode.get("state");
        
        if (stateNode == null || !stateNode.isObject()) {
            throw new IOException("Invalid genesis block format: 'state' field is missing or not an object");
        }
        
        // Process each account in the genesis state
        Iterator<Map.Entry<String, JsonNode>> fields = stateNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String address = entry.getKey();
            JsonNode accountData = entry.getValue();
            
            // Get balance
            String balance = "0";
            if (accountData.has("balance")) {
                balance = accountData.get("balance").asText();
            }
            
            // Check if this account already exists in accounts.json
            AccountInfo accountInfo = accountsMap.get(address);
        
            
            // Create account state
            AccountState accountState = new AccountState(
                address,
                accountInfo.publicKeyPath,
                accountInfo.privateKeyPath,
                balance,
                accountInfo.name
            );
            
            // Add contract-specific data if present
            if (accountData.has("code")) {
                accountState.setCode(accountData.get("code").asText());
                
                if (accountData.has("storage") && accountData.get("storage").isObject()) {
                    JsonNode storageNode = accountData.get("storage");
                    Map<String, String> storage = new HashMap<>();
                    
                    Iterator<Map.Entry<String, JsonNode>> storageFields = storageNode.fields();
                    while (storageFields.hasNext()) {
                        Map.Entry<String, JsonNode> storageEntry = storageFields.next();
                        storage.put(storageEntry.getKey(), storageEntry.getValue().asText());
                    }
                    
                    accountState.setStorage(storage);
                }
            }
            
            // Add the account to the world state
            addAccount(accountState);
        }
        

    }
    
    /**
     * Loads the accounts.json file into a map
     */
    private Map<String, AccountInfo> loadAccountsFile() throws IOException {
        Map<String, AccountInfo> accountsMap = new HashMap<>();
        File accountsFile = new File(ACCOUNTS_FILE_PATH);
        
        if (accountsFile.exists()) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(accountsFile);
            
            // Get the clients array from the root object
            JsonNode clientsArray = rootNode.get("clients");
            
            // Check if the clients node exists and is an array
            if (clientsArray != null && clientsArray.isArray()) {
                for (JsonNode clientNode : clientsArray) {
                    // Extract information from each client object
                    String name = clientNode.get("name").asText();
                    String address = clientNode.get("address").asText();
                    String publicKeyPath = clientNode.get("publicKeyPath").asText();
                    String privateKeyPath = clientNode.get("privateKeyPath").asText();
                    
                    // Store the account info using the address as the key
                    AccountInfo accountInfo = new AccountInfo(publicKeyPath, privateKeyPath, name, address);
                    accountsMap.put(address, accountInfo);
                }
            } else {
                throw new IOException("Invalid JSON format: Expected a 'clients' array.");
            }
        }
    
    return accountsMap;
}


    
    /**
     * Generates new key pair for an account and saves them to files
     */
    private AccountInfo generateNewAccountInfo(String address, String name) throws IOException {
        try {
            // Generate a unique account ID
            String accountId = "account_" + UUID.randomUUID().toString().substring(0, 8);
            
            // Create key file paths
            String publicKeyPath = KEYS_DIRECTORY + "/" + accountId + ".pub";
            String privateKeyPath = KEYS_DIRECTORY + "/" + accountId + ".key";
            
            // Generate key pair
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048, new SecureRandom());
            KeyPair keyPair = keyGen.generateKeyPair();
            
            // Save public key
            String publicKeyEncoded = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
            Files.write(Paths.get(publicKeyPath), publicKeyEncoded.getBytes());
            
            // Save private key
            String privateKeyEncoded = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
            Files.write(Paths.get(privateKeyPath), privateKeyEncoded.getBytes());
            
            return new AccountInfo(publicKeyPath, privateKeyPath, name, address);
            
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Failed to generate keys: " + e.getMessage(), e);
        }
    }
    
    /**
     * Helper class to store account key information
     */
    private static class AccountInfo {
        String publicKeyPath;
        String privateKeyPath;
        String name;
        String address;
        
        public AccountInfo(String publicKeyPath, String privateKeyPath, String name, String address) {
            this.name = name;
            this.address = address;
            this.publicKeyPath = publicKeyPath;
            this.privateKeyPath = privateKeyPath;
        }
    }

    public static WorldState deepCopy(WorldState originalState) {
        WorldState copy = new WorldState();
    
        for (Map.Entry<String, AccountState> entry : originalState.getAccounts().entrySet()) {
            AccountState original = entry.getValue();
            AccountState cloned = new AccountState(
                original.getAddress(),
                original.getPublicKeyPath(),
                original.getPrivateKeyPath(),
                original.getBalance(),
                original.getName()
            );
    
            cloned.setCode(original.getCode()); // Pode ser null ou uma string
    
            // Copiar profundamente o mapa de storage
            if (original.getStorage() != null) {
                Map<String, String> storageCopy = new HashMap<>(original.getStorage());
                cloned.setStorage(storageCopy);
            }
    
            copy.addAccount(cloned);
        }
    
        return copy;
    }

}