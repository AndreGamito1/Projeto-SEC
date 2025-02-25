import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Server {

    private static Key aesKey;
    private static Key rsaPrivateKey;
    
    public static void main(String[] args) {
        try {
            // Load the AES key from the file
            aesKey = AESKeyGenerator.read("keys/aes_key.key");

            // Load the RSA private key (for decrypting with RSA)
            rsaPrivateKey = RSAKeyGenerator.read("keys/server_private.key", "priv");

            // Create a UDP socket to listen for incoming data
            DatagramSocket serverSocket = new DatagramSocket(9876);
            System.out.println("Server listening on port 9876...");

            while (true) {
                // Receive incoming UDP packet
                byte[] receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                serverSocket.receive(receivePacket);

                // Get the encrypted data as a string
                String encryptedData = new String(receivePacket.getData(), 0, receivePacket.getLength());

                // Step 1: RSA Decryption of AES-encrypted data
                String aesEncryptedData = decryptWithRSA(encryptedData, rsaPrivateKey);

                // Step 2: AES Decryption to get the original message
                String originalMessage = decryptWithAES(aesEncryptedData, aesKey);

                // Step 3: Print the original message
                System.out.println("Original Message: " + originalMessage);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Method to decrypt data using RSA
    private static String decryptWithRSA(String input, Key rsaPrivateKey2) throws Exception {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, rsaPrivateKey2);
        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(input));
        System.out.println("Message decripted with RSA key");
        return new String(decryptedBytes); // Return as decrypted string
    }

    // Method to decrypt data using AES
    private static String decryptWithAES(String input, Key aesKey2) throws Exception {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, aesKey2);
        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(input));
        System.out.println("Message decripted with AES key");
        return new String(decryptedBytes); // Return as decrypted string
    }
}

