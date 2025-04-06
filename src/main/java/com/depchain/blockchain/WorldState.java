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
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.fluent.EVMExecutor;
import org.hyperledger.besu.evm.fluent.SimpleWorld;
import org.hyperledger.besu.evm.tracing.StandardJsonTracer;

public class WorldState {
    private Map<String, AccountState> accounts;
    private Map<String, String> nameAddress; // Map to store address and name pairs
    private static final String GENESIS_BLOCK_PATH = "src/main/resources/genesisBlock.json"; // Path to the genesis
                                                                                             // block file
    private static final String ACCOUNTS_FILE_PATH = "src/main/resources/accounts.json"; // File that contains the
                                                                                         // accounts name and the path
                                                                                         // to their keys
    private static final String KEYS_DIRECTORY = "src/main/resources/generated_keys"; // Directory to store generated
                                                                                      // keys
    private SimpleWorld evmWorld;

    public WorldState() {
        this.accounts = new HashMap<>();
        this.nameAddress = new HashMap<>();
        this.evmWorld = new SimpleWorld();
    }

    public void addAccount(AccountState accountState) {
        System.out.println("Adding account: " + accountState.getName());
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
                    accountInfo.name);

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
                                + address + "], Key=[" + key + "], Value=[" + value + "]"); // Added contract address
                                                                                            // for clarity

                        storage.put(storageEntry.getKey(), storageEntry.getValue().asText());
                    }

                    accountState.setStorage(storage);
                }
            }

            // Add the account to the world state
            System.out.println("Adding account to world state: " + accountState.getName() + " with address: "
                    + accountState.getAddress());
            addAccount(accountState);
        }

    }

    /**
     * Loads the accounts from the accounts.json file.
     * If the file does not exist, it returns an empty map.
     * 
     * @return A map of account addresses to AccountInfo objects
     * @throws IOException If there is an error reading the file
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

    /**
     * Creates a deep copy of the given worldstate object.
     * 
     * @param originalState The original worldstate to be copied.
     * @return A new worldstate instance that is a deep copy of the original.
     */
    public static WorldState deepCopy(WorldState originalState) {
        WorldState copy = new WorldState();

        for (Map.Entry<String, AccountState> entry : originalState.getAccounts().entrySet()) {
            AccountState original = entry.getValue();
            AccountState cloned = new AccountState(
                    original.getAddress(),
                    original.getPublicKeyPath(),
                    original.getPrivateKeyPath(),
                    original.getBalance(),
                    original.getName());

            cloned.setCode(original.getCode());

            if (original.getStorage() != null) {
                Map<String, String> storageCopy = new HashMap<>(original.getStorage());
                cloned.setStorage(storageCopy);
            }

            copy.addAccount(cloned);
        }

        return copy;
    }

    /**
     * Verifies if a transaction is valid by checking the sender's balance and the
     * receiver's existence.
     * 
     * @param tx    The transaction to verify
     * @param state The world state to check against
     * @return true if the transaction is valid, false otherwise
     */
    private boolean isTransactionValid(Transaction tx, WorldState state) {
        System.out.println("Verifying transaction: " + tx);
        System.out.println("Verifying transaction: " + tx);
        System.out.println("Sender: " + tx.getSender() + ", Receiver: " + tx.getReceiver());
        System.out.println(
                "state.getAccount(tx.getSender().toString()) = " + state.getAccount(tx.getSender().toString()));
        AccountState sender = state.getAccount(tx.getSender().toString());
        AccountState receiver = state.getAccount(tx.getReceiver().toString());
        System.out.println("Sender: " + sender + ", Receiver: " + receiver);

        if (sender == null || receiver == null) {
            System.out.println("Sender or receiver null");
            return false;
        }

        try {
            double balance = new java.math.BigDecimal(sender.getBalance()).doubleValue();
            return balance >= tx.getAmount();
        } catch (Exception e) {
            System.out.println("Error checking balance: " + e.getMessage());
            return false;
        }
    }

    /**
     * Applies a transaction to the world state.
     * 
     * @param tx    The transaction to apply
     * @param state The world state to apply the transaction to
     */
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
        /*Not implemented*/
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

    /**
     * Handles a regular transfer between two accounts.
     * 
     * @param tx       The transaction to apply
     * @param sender   The sender's account state
     * @param receiver The receiver's account state
     */
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

    /**
     * Synchronizes the storage of a contract to the EVM.
     * 
     * @param receiver The contract account state
     * @param evmAcc   The mutable account in the EVM
     */
    private void syncStorageToEVM(AccountState receiver, MutableAccount evmAcc) {
        if (receiver.getStorage() == null) {
            return;
        }
        receiver.getStorage().forEach((key, value) -> {

            if (key == null || value == null) {
                return; 
            }
            boolean startsWith0x = value.toLowerCase().startsWith("0x");

            if (startsWith0x) {
                String rawValue = value.substring(2);
                int rawLength = rawValue.length();
                if (!rawValue.matches("^[0-9a-fA-F]*$")) {
                }
            } else {
                int valueLength = value.length();

                if (!value.matches("^[0-9a-fA-F]*$")) {
                }
            }

            try {
                if (key.substring(2).length() % 2 != 0) {
                }
                String hexValue = value.startsWith("0x") ? value.substring(2) : value;
                if (hexValue.length() % 2 != 0) {
                    hexValue = "0" + hexValue;
                }
                Bytes valueBytes = Bytes.fromHexString("0x" + hexValue);
                String hexKey = key.startsWith("0x") ? key.substring(2) : key;
                if (hexKey.length() % 2 != 0) {
                    hexKey = "0" + hexKey;
                }
                Bytes keyBytes = Bytes.fromHexString("0x" + hexKey);


                evmAcc.setStorageValue(
                        UInt256.fromBytes(keyBytes),
                        UInt256.fromBytes(valueBytes));
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                throw e;
            } catch (Exception e) {
                System.err.println(
                        "Unexpected error processing storage! Key: [" + key + "], Value: [" + value + "]");
                e.printStackTrace();
                throw e;
            }
        });
        System.out.println("Finished syncing storage for contract: " + receiver.getAddress());
    }

    /**
     * Synchronizes the storage of a contract from the EVM to the world state.
     * 
     * @param receiver The contract account state
     * @param evmAcc   The mutable account in the EVM
     */
    private void syncStorageFromEVM(AccountState receiver, MutableAccount evmAcc) {
        receiver.getStorage().clear();
        evmAcc.getUpdatedStorage().forEach((key, value) -> {
            receiver.setStorageValue(
                    key.toUnprefixedHexString(),
                    value.toUnprefixedHexString());
        });
    }

    /**
     * Verifies if all transactions in a block are valid.
     * 
     * @param block The block to verify
     * @return true if all transactions are valid, false otherwise
     */
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

    /**
     * Applies all transactions in a block to the world state.
     * 
     * @param block The block containing transactions to apply
     */
    public void applyBlock(Block block) {
        for (Transaction tx : block.getTransactions()) {
            applyTransaction(tx, this);
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("WorldState:\n");

        // Sort accounts by name
        accounts.values().stream()
                .sorted((a1, a2) -> a1.getName().compareTo(a2.getName()))
                .forEach(account -> {
                    sb.append("  ").append(account.getName())
                            .append(": ").append(account.getBalance())
                            .append("\n");
                });

        return sb.toString();
    }

    // --- Getters and Setters ---

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

    public static int extractIntegerFromReturnData(ByteArrayOutputStream byteArrayOutputStream) {
        String[] lines = byteArrayOutputStream.toString().split("\\r?\\n");
        JsonObject jsonObject = JsonParser.parseString(lines[lines.length - 1]).getAsJsonObject();

        String memory = jsonObject.get("memory").getAsString();

        JsonArray stack = jsonObject.get("stack").getAsJsonArray();
        int offset = Integer.decode(stack.get(stack.size() - 1).getAsString());
        int size = Integer.decode(stack.get(stack.size() - 2).getAsString());

        String returnData = memory.substring(2 + offset * 2, 2 + offset * 2 + size * 2);
        return Integer.decode("0x" + returnData);
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