package com.depchain.client;

import com.depchain.utils.KeyManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

public class ClientManager {

    private final static Map<String, PublicKey> publicKeys = new HashMap<>();
    private final Map<String, PrivateKey> privateKeys = new HashMap<>();
    private final Map<String, String> addresses = new HashMap<>();

    public ClientManager(String jsonFilePath) throws Exception {
        loadClients(jsonFilePath);
    }

    private void loadClients(String jsonFilePath) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        File file = new File(jsonFilePath);

        if (!file.exists()) {
            throw new Exception("Client JSON file not found: " + jsonFilePath);
        }

        String jsonContent = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        JsonNode rootNode = objectMapper.readTree(jsonContent);

        if (!rootNode.has("clients") || !rootNode.get("clients").isArray()) {
            throw new Exception("Invalid JSON format: expected an array under 'clients'");
        }

        for (JsonNode clientNode : rootNode.get("clients")) {
            String name = clientNode.get("name").asText();
            String address = clientNode.get("address").asText();
            String publicKeyPath = clientNode.get("publicKeyPath").asText();
            String privateKeyPath = clientNode.get("privateKeyPath").asText();
            KeyManager.createAccountKeyPair(name, publicKeyPath, privateKeyPath);

            System.out.println("Loading key: " + publicKeyPath);
            PublicKey publicKey = KeyManager.loadPublicKeyFromFile(publicKeyPath);
            PrivateKey privateKey = KeyManager.loadPrivateKeyFromFile(privateKeyPath);

            publicKeys.put(name, publicKey);
            privateKeys.put(name, privateKey);
            addresses.put(name, address);
        }
    }

    public static PublicKey getPublicKey(String name) {
        return publicKeys.get(name);
    }

    public PrivateKey getPrivateKey(String name) {
        return privateKeys.get(name);
    }

    public String getAddress(String name) {
        return addresses.get(name);
    }
}
