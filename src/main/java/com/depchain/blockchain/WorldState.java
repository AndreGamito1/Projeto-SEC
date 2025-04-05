package com.depchain.blockchain;

import com.depchain.utils.Logger;
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
    private Map<String, String> nameAddress; // Map to store address and name pairs
    private static final String GENESIS_BLOCK_PATH = "src/main/resources/genesisBlock.json";
    private static final String ACCOUNTS_FILE_PATH = "src/main/resources/accounts.json";
    private static final String KEYS_DIRECTORY = "src/main/resources/generated_keys";

    public WorldState() {
        this.accounts = new HashMap<>();
        this.nameAddress = new HashMap<>();
    }

    public void addAccount(AccountState accountState) {
        System.out.println("Adding account: " + accountState.getName() + " with address: " + accountState.getAddress());
        nameAddress.put(accountState.getName(), accountState.getAddress());
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
        
             // Handle missing accountInfo
            if (accountInfo == null) {
                System.out.println("Account info not found for address: " + address + ". Skipping...");
                continue; // Skip this account if accountInfo is missing
            }
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
            System.out.println("Copy:");
            copy.addAccount(cloned);
        }
    
        return copy;
    }

        private boolean isTransactionValid(Transaction tx, WorldState state) {
        // Aqui deves verificar:
        // - Assinatura válida
        // - Sender existe
        // - Saldo suficiente
        // - Nonce correto (se usares nonce)
        
        AccountState sender = state.getAccount(tx.getSender().toString());
        AccountState receiver = state.getAccount(tx.getReceiver().toString());
        System.out.println("Sender: " + sender + ", Receiver: " + receiver);
    
        if (sender == null || receiver == null) {System.out.println("Sender or receiver null"); return false;}
    
        try {
            double balance = new java.math.BigDecimal(sender.getBalance()).doubleValue();
            return balance >= tx.getAmount(); // saldo suficiente
        } catch (Exception e) {
            System.out.println("Error checking balance: " + e.getMessage());
            return false;
        }
    }
    
    private void applyTransaction(Transaction tx, WorldState state) {
        AccountState sender = state.getAccount(tx.getSender().toString());
        AccountState receiver = state.getAccount(tx.getReceiver().toString());
    
        if (sender == null || receiver == null) return;
    
        java.math.BigDecimal value = java.math.BigDecimal.valueOf(tx.getAmount());
        java.math.BigDecimal senderBalance = new java.math.BigDecimal(sender.getBalance());
        java.math.BigDecimal receiverBalance = new java.math.BigDecimal(receiver.getBalance());
    
        sender.setBalance(senderBalance.subtract(value).toString());
        receiver.setBalance(receiverBalance.add(value).toString());
    } 
    
    public boolean areAllTransactionsValid(Block block) {
        System.out.println("Verifying transactions in block");
        WorldState copyWorldState = WorldState.deepCopy(this); // cópia profunda do estado atual da worldstate
    
        for (Transaction tx : block.getTransactions()) {
            if (!isTransactionValid(tx, copyWorldState)) {
                return false;
            }
            applyTransaction(tx, copyWorldState); // aplica a transação à cópia para atualizar o estado
        }

        System.out.println("All transactions are valid :)");
        return true;
    }

    public void applyBlock(Block block) {
        for (Transaction tx : block.getTransactions()) {
                applyTransaction(tx, this);
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("WorldState:\n");
        
        // Sort accounts by name for a consistent display
        accounts.values().stream()
            .sorted((a1, a2) -> a1.getName().compareTo(a2.getName()))
            .forEach(account -> {
                sb.append("  ").append(account.getName())
                  .append(": ").append(account.getBalance())
                  .append("\n");
            });
            
        return sb.toString();
    }

    //--- Getters and Setters ---

    public Map<String, AccountState> getAccounts() {
        return accounts;
    }

    public AccountState getAccount(String name) {
        String address = nameAddress.get(name);
        return accounts.get(address);
    }

    /**
     * Gets the balance of a client by their name.
     * 
     * @param clientName The name of the client whose balance to retrieve
     * @return The balance as a String, or null if the client doesn't exist
     */
    public String getBalance(String clientName) {
        System.out.println("Getting balance for client: " + clientName);
        AccountState account = getAccount(clientName);
        if (account == null) {
            System.out.println("Account not found for client: " + clientName);
            return null;
        }
        return account.getBalance();
    }

}