import javax.crypto.SecretKey;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Base64;

public class AuthenticatedReceiver {
    private int port;
    private KeyPair rsaKeyPair; // RSA Key Pair
    private SecretKey aesKey;   // AES Key after decryption

    public AuthenticatedReceiver(int port) throws Exception {
        this.port = port;
        this.rsaKeyPair = CryptoUtils.generateRSAKeyPair();
    }

    public void listen() throws Exception {
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("[Receiver] Listening on port " + port);

        Socket socket = serverSocket.accept();
        System.out.println("[Receiver] Connected to Sender");

        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);

        // Send Public Key to Sender
        String publicKeyString = Base64.getEncoder().encodeToString(rsaKeyPair.getPublic().getEncoded());
        writer.println(publicKeyString);
        System.out.println("[Receiver] Sent Public RSA Key");

        // Receive Encrypted AES Key
        String encryptedAESKey = reader.readLine();
        aesKey = CryptoUtils.decryptAESKeyWithRSA(rsaKeyPair.getPrivate(), encryptedAESKey);
        System.out.println("[Receiver] Received and Decrypted AES Key");

        // Now receive encrypted messages
        while (true) {
            String encryptedMessage = reader.readLine();
            String decryptedMessage = AESUtils.decrypt(encryptedMessage, aesKey);
            System.out.println("[Receiver] Received: " + decryptedMessage);
        }
    }
}
