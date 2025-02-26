package src.main.java.blockchain.client;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

import src.main.java.blockchain.utils.AESKeyGenerator;
import src.main.java.blockchain.utils.RSAKeyGenerator;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Scanner;

public class Client {

    private static Key aesKey;
    private static Key rsaPublicKey;

    public static void main(String[] args) {
        try {
            // Load the AES key from the file
            aesKey = AESKeyGenerator.read("keys/aes_key.key");

            // Load the RSA public key (for encrypting with RSA)
            rsaPublicKey = RSAKeyGenerator.read("keys/server_public.key", "pub");

            // Create a UDP socket to send data
            DatagramSocket clientSocket = new DatagramSocket();
            InetAddress serverAddress = InetAddress.getByName("localhost");

            // Create a scanner for user input
            Scanner scanner = new Scanner(System.in);

            while (true) {
                // Get the user input string
                System.out.print("Enter a message to send: ");
                String input = scanner.nextLine();

                // Step 1: AES Encryption of the input message
                String aesEncryptedData = encryptWithAES(input, aesKey);

                // Step 2: RSA Encryption of the AES-encrypted data
                String rsaEncryptedData = encryptWithRSA(aesEncryptedData, rsaPublicKey);

                // Step 3: Send the RSA-encrypted data over UDP to the server
                byte[] sendData = rsaEncryptedData.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, 9876);
                clientSocket.send(sendPacket);

                System.out.println("Message sent to server...");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Method to encrypt data using AES
    private static String encryptWithAES(String input, Key aesKey2) throws Exception {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey2);
        byte[] encryptedBytes = cipher.doFinal(input.getBytes());
        System.out.println("Message encripted with RSA key");
        return Base64.getEncoder().encodeToString(encryptedBytes); // Return as Base64 string
    }

    // Method to encrypt data using RSA
    private static String encryptWithRSA(String input, Key rsaPublicKey2) throws Exception {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey2);
        byte[] encryptedBytes = cipher.doFinal(input.getBytes());
        System.out.println("Message encripted with AES key");
        return Base64.getEncoder().encodeToString(encryptedBytes); // Return as Base64 string
    }
}
