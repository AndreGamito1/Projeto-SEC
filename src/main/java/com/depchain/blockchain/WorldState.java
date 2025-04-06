package com.depchain.blockchain;

import com.depchain.utils.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.apache.tuweni.units.ethereum.Gas;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.fluent.EVMExecutor;
import org.hyperledger.besu.evm.fluent.SimpleWorld;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.tracing.StandardJsonTracer;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

public class WorldState {
    private Map<String, AccountState> accounts;
    private Map<String, String> nameAddress; // Map to store address and name pairs
    private static final String GENESIS_BLOCK_PATH = "src/main/resources/genesisBlock.json";
    private static final String ACCOUNTS_FILE_PATH = "src/main/resources/accounts.json";
    private static final String KEYS_DIRECTORY = "src/main/resources/generated_keys";
    private SimpleWorld evmWorld;

    public WorldState() {
        this.accounts = new HashMap<>();
        this.nameAddress = new HashMap<>();
        this.evmWorld = new SimpleWorld();

        
        
    }

    public void addAccount(AccountState accountState) {
        System.out.println("Adding account: " + accountState.getName() + " with address: " + accountState.getAddress());
        nameAddress.put(accountState.getName(), accountState.getAddress());
        accounts.put(accountState.getAddress(), accountState);
        evmWorld.createAccount(Address.fromHexString(accountState.getAddress()), 0L, Wei.ZERO);

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
                        String key = storageEntry.getKey();
                        String value = storageEntry.getValue().asText();
                        System.out.println("DEBUG loadGenesisState: Reading Contract=["
                        + address + "], Key=[" + key + "], Value=[" + value + "]"); // Added contract address for clarity

                        storage.put(storageEntry.getKey(), storageEntry.getValue().asText());
                    }
                    
                    accountState.setStorage(storage);
                }
            }
            
            // Add the account to the world state
            System.out.println("Adding account to world state: " + accountState.getName() + " with address: " + accountState.getAddress());
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
    
            cloned.setCode(original.getCode());
    
            if (original.getStorage() != null) {
                Map<String, String> storageCopy = new HashMap<>(original.getStorage());
                cloned.setStorage(storageCopy);
            }
    
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
        
        System.out.println("Verifying transaction: " + tx);
        System.out.println("Verifying transaction: " + tx);
        System.out.println("Sender: " + tx.getSender() + ", Receiver: " + tx.getReceiver());
        System.out.println("state.getAccount(tx.getSender().toString()) = " + state.getAccount(tx.getSender().toString()));
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
        AccountState sender = state.getAccount(tx.getSender());
        AccountState receiver = state.getAccount(tx.getReceiver());

        if (sender == null || receiver == null) {
            System.err.println("Sender or receiver does not exist.");
            return;
        }

        if (receiver.isContract()) {
           handleSmartContractTransaction(tx, sender, receiver, state);
        } else {
            handleRegularTransfer(tx, sender, receiver);
        }
    }

   private void handleSmartContractTransaction(Transaction tx, AccountState sender, AccountState receiver, WorldState state) {
        System.out.println("⚙️ Executing smart contract at: " + receiver.getAddress());
    
        try {
            SimpleWorld simpleWorld = state.getEvmWorld(); // shared evmWorld
    
            Address receiverAddr = Address.fromHexString(receiver.getAddress());
            Address senderAddr = Address.fromHexString(sender.getAddress());
    
            MutableAccount receiverAcc = (MutableAccount) simpleWorld.get(receiverAddr);
            receiverAcc.setCode(Bytes.fromHexString(receiver.getCode()));
            syncStorageToEVM(receiver, receiverAcc);
    
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            PrintStream printStream = new PrintStream(byteArrayOutputStream);
            StandardJsonTracer tracer = new StandardJsonTracer(printStream, true, true, true);
            
     
            EVM evmInstance = new EVM(null, null, null, EvmSpecVersion.CANCUN);
            var executor = EVMExecutor.evm(evmInstance)
                .tracer(tracer)
                .code((Code) Bytes.fromHexString(state.getAccount(receiver.getAddress()).getCode()))
                .sender(senderAddr)
                .receiver(receiverAddr)
                .worldUpdater(simpleWorld.updater())
                .commitWorldState();
                
            executor.callData(Bytes.fromHexString(tx.getData()));
            executor.execute();
            int count = extractIntegerFromReturnData(byteArrayOutputStream);
            System.out.println("Output of 'transfer()):' " + Integer.toString(count));

            syncStorageFromEVM(receiver, receiverAcc);
    
        } catch (Exception e) {
            System.err.println("Error during EVM contract execution: " + e.getMessage());
            e.printStackTrace();
        }
    }


    
   
    private void handleRegularTransfer(Transaction tx, AccountState sender, AccountState receiver) {
        BigDecimal value = BigDecimal.valueOf(tx.getAmount());
        BigDecimal senderBalance = new BigDecimal(sender.getBalance());
        BigDecimal receiverBalance = new BigDecimal(receiver.getBalance());

        if (senderBalance.compareTo(value) < 0) {
            System.err.println("Insufficient funds for sender.");
            return;
        }

        sender.setBalance(senderBalance.subtract(value).toString());
        receiver.setBalance(receiverBalance.add(value).toString());
    }

    private void syncStorageToEVM(AccountState receiver, MutableAccount evmAcc) {
        System.out.println("DEBUG: Syncing storage for contract: " + receiver.getAddress());
        if (receiver.getStorage() == null) {
            System.out.println("DEBUG: Storage map is null for " + receiver.getAddress());
            return;
        }
        receiver.getStorage().forEach((key, value) -> {
            System.out.println("DEBUG: Processing storage entry - Key: [" + key + "], Value: [" + value + "]");
    
            // --->>> ADD/MODIFY THESE DEBUG LINES <<<---
            if (key == null || value == null) {
                 System.err.println("FATAL DEBUG: Key or Value is NULL. Key=" + key + ", Value=" + value);
                 // Handle appropriately, maybe throw?
                 return; // Skip this entry
            }
            System.out.println("DEBUG: Value String Length: " + value.length());
            boolean startsWith0x = value.toLowerCase().startsWith("0x");
            System.out.println("DEBUG: Value startsWith('0x'): " + startsWith0x);
    
            if (startsWith0x) {
                String rawValue = value.substring(2);
                int rawLength = rawValue.length();
                System.out.println("DEBUG: Raw Value Substring: [" + rawValue + "]"); // Print the raw substring
                System.out.println("DEBUG: Raw Value Substring Length: " + rawLength);
                System.out.println("DEBUG: Raw Value Substring is Even Length: " + (rawLength % 2 == 0));
                // Check for non-hex characters in the raw substring
                if (!rawValue.matches("^[0-9a-fA-F]*$")) {
                     System.err.println("FATAL DEBUG: Raw Value Substring contains NON-HEX characters!");
                }
            } else {
                int valueLength = value.length();
                System.out.println("DEBUG: Value does not start with 0x");
                System.out.println("DEBUG: Value String is Even Length: " + (valueLength % 2 == 0));
                 // Check for non-hex characters in the value
                if (!value.matches("^[0-9a-fA-F]*$")) {
                     System.err.println("FATAL DEBUG: Value (no prefix) contains NON-HEX characters!");
                }
            }
            // --->>> END DEBUG LINES <<<---
    
            try {
                // Check key validity (simplified)
                if (key.substring(2).length() % 2 != 0) {
                     System.err.println("ERROR: Invalid storage KEY format detected: " + key);
                }
                // Sanitize value string
                String hexValue = value.startsWith("0x") ? value.substring(2) : value;
                if (hexValue.length() % 2 != 0) {
                    System.err.println("WARN: Odd-length hex detected. Padding with leading 0.");
                    hexValue = "0" + hexValue;
                }
                Bytes valueBytes = Bytes.fromHexString("0x" + hexValue);
                String hexKey = key.startsWith("0x") ? key.substring(2) : key;
                if (hexKey.length() % 2 != 0) {
                    System.err.println("WARN: Odd-length hex key detected. Padding with leading 0.");
                    hexKey = "0" + hexKey;
                }
                Bytes keyBytes = Bytes.fromHexString("0x" + hexKey);

    
                // The actual conversion happens here
    
                evmAcc.setStorageValue(
                    UInt256.fromBytes(keyBytes),
                    UInt256.fromBytes(valueBytes)
                );
                System.out.println("DEBUG: Successfully processed Key: " + key);
            } catch (IllegalArgumentException e) {
                System.err.println("FATAL DEBUG: CONVERSION FAILED! Key: [" + key + "], Value: [" + value + "]");
                e.printStackTrace();
                throw e;
            } catch (Exception e) {
                 System.err.println("FATAL DEBUG: Unexpected error processing storage! Key: [" + key + "], Value: [" + value + "]");
                 e.printStackTrace();
                 throw e;
            }
        });
        System.out.println("DEBUG: Finished syncing storage for contract: " + receiver.getAddress());
    }
    
    private void syncStorageFromEVM(AccountState receiver, MutableAccount evmAcc) {
        receiver.getStorage().clear();
        evmAcc.getUpdatedStorage().forEach((key, value) -> {
            receiver.setStorageValue(
                key.toUnprefixedHexString(),
                value.toUnprefixedHexString()
            );
        });
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

     public static int extractIntegerFromReturnData(ByteArrayOutputStream byteArrayOutputStream) {
        String[] lines = byteArrayOutputStream.toString().split("\\r?\\n");
        JsonObject jsonObject = JsonParser.parseString(lines[lines.length - 1]).getAsJsonObject();

        String memory = jsonObject.get("memory").getAsString();

        JsonArray stack = jsonObject.get("stack").getAsJsonArray();
        int offset = Integer.decode(stack.get(stack.size() - 1).getAsString());
        int size = Integer.decode(stack.get(stack.size() - 2).getAsString());

        String returnData = memory.substring(2 + offset * 2, 2 + offset * 2 + size * 2);
        return Integer.decode("0x"+returnData);
    }

    public static String convertIntegerToHex256Bit(int number) {
        BigInteger bigInt = BigInteger.valueOf(number);

        return String.format("%064x", bigInt);
    }

    public static String padHexStringTo256Bit(String hexString) {
        if (hexString.startsWith("0x")) {
            hexString = hexString.substring(2);
        }

        int length = hexString.length();
        int targetLength = 64;

        if (length >= targetLength) {
            return hexString.substring(0, targetLength);
        }

        return "0".repeat(targetLength - length) +
                hexString;
    }

    //--- Getters and Setters ---

    private SimpleWorld getEvmWorld() {
        return evmWorld;
    }

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