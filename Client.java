import java.net.*;
import java.security.Key;
import java.util.Base64;
import java.util.Scanner;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class Client {
    private static Key aesKey;  // AES Key
    private static Key rsaPublicKey;
    private static Key rsaPrivateKey;
    public static void main(String[] args) {
        DatagramSocket socket = null;
        Scanner scanner = new Scanner(System.in);
        try {
            // Load AES Key
            aesKey = AESKeyGenerator.read("keys/aes_key.key");

            // Load RSA Public Key for encryption
            rsaPublicKey = RSAKeyGenerator.read("keys/client_public.key", "pub");
            rsaPrivateKey = RSAKeyGenerator.read("keys/client_private.key", "priv");

            socket = new DatagramSocket(); // Create a socket
            InetAddress serverAddress = InetAddress.getByName("localhost"); // Server address

            System.out.print("Enter a message to send to the server: ");
            String message = scanner.nextLine(); // Read user input

            // Encrypt the AES key using RSA Public Key
            String encryptedAESKey = RSAencrypt(Base64.getEncoder().encodeToString(aesKey.getEncoded()), rsaPublicKey);
            
            // Encrypt the message using AES
            String encryptedMessage = AESencrypt(message, aesKey);

            // Combine RSA-encrypted AES key and AES-encrypted message
            String finalEncryptedData = encryptedAESKey + ":" + encryptedMessage;

            byte[] sendData = finalEncryptedData.getBytes();

            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, 9876);
            socket.send(sendPacket); // Send the encrypted message

            byte[] receiveData = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            socket.receive(receivePacket); // Receive encrypted response

            String encryptedResponse = new String(receivePacket.getData(), 0, receivePacket.getLength());

            // Split the received response into encrypted AES key and AES-encrypted response message
            String[] parts = encryptedResponse.split(":", 2);
            if (parts.length != 2) {
                System.err.println("Invalid response format.");
                return;
            }

            String encryptedAESKeyResponse = parts[0];  // RSA-encrypted AES key
            String AESencryptedResponseMessage = parts[1];  // AES-encrypted response message

            // Decrypt the AES key from the server's RSA-encrypted AES key
            String decryptedAESKey = RSAdecrypt(encryptedAESKeyResponse, null);  // Decrypt with the private RSA key of the client
            aesKey = new SecretKeySpec(Base64.getDecoder().decode(decryptedAESKey), "AES");

            // Decrypt the response message using AES key
            String decryptedResponse = AESdecrypt(AESencryptedResponseMessage, aesKey);
            System.out.println("Received from server (Decrypted): " + decryptedResponse);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            scanner.close();
        }
    }

    // Encrypts data using the RSA public key
    public static String RSAencrypt(String data, Key rsaPublicKey) throws Exception {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);
        byte[] encryptedBytes = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    // Decrypts data using the RSA private key (for client-side decryption)
    public static String RSAdecrypt(String data, Key key) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(data));
        return new String(decryptedBytes);
    }

    private static String AESencrypt(String data, Key key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encryptedBytes = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    private static String AESdecrypt(String data, Key key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(data));
        return new String(decryptedBytes);
    }
}
