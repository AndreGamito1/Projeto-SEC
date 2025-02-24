import java.net.*;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class Server {
    private static final int PORT = 9876;
    private static Key rsaPrivateKey; // RSA Private Key
    private static Key aesKey;               // AES Key

    public static void main(String[] args) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(PORT);
            System.err.println("Server started on port " + PORT);

            // Load RSA Private Key (used for decrypting the AES-encrypted message from the client)
            rsaPrivateKey = RSAKeyGenerator.read("keys/server_private.key", "priv");
            aesKey = AESKeyGenerator.read("keys/aes_key.key");

            byte[] receiveData = new byte[1024];

            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);

                String rsaEncryptedMessage = new String(receivePacket.getData(), 0, receivePacket.getLength());

                // Decrypt the message using RSA Private Key
                String aesEncryptedMessage = RSAdecrypt(rsaEncryptedMessage, rsaPrivateKey);

                // Decrypt the AES-encrypted message using the AES key
                String message = AESdecrypt(aesEncryptedMessage, aesKey);
                System.out.println("Received (Decrypted): " + message);

                // Prepare response (simply sending back the received message)
                String aesResponse = AESencrypt("Response: " + message, aesKey);

                // Encrypt the response message with RSA using the client's public key
                Key clientPublicKey = RSAKeyGenerator.read("keys/client_public.key", "pub");  // Assuming the client's public key is available
                String rsaEncryptedResponse = RSAencrypt(aesResponse, clientPublicKey);

                // Send the RSA-encrypted response back to the client
                byte[] sendData = rsaEncryptedResponse.getBytes();
                InetAddress clientAddress = receivePacket.getAddress();
                int clientPort = receivePacket.getPort();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                socket.send(sendPacket);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (socket != null && !socket.isClosed()) {
                System.err.println("Closing the socket");
                socket.close();
            }
        }
    }

    // Decrypts data using the RSA private key
    private static String RSAdecrypt(String data, Key rsaPrivateKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, rsaPrivateKey);
        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(data));
        return new String(decryptedBytes);
    }

    // Encrypts data using the RSA public key
    private static String RSAencrypt(String data, Key clientPublicKey) throws Exception {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, clientPublicKey);
        byte[] encryptedBytes = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    // Decrypts data using AES
    private static String AESdecrypt(String data, Key key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(data));
        return new String(decryptedBytes);
    }

    // Encrypts data using AES
    private static String AESencrypt(String data, Key key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encryptedBytes = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }
}
