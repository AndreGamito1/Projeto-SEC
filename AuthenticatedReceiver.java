import javax.crypto.SecretKey;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

public class AuthenticatedReceiver {
    private int port;
    private KeyPair keyPair;
    private PrivateKey privateKey;
    private PublicKey publicKey;
    
    public AuthenticatedReceiver(int port) throws Exception {
        this.port = port;
        
        // Generate RSA Key Pair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        keyPair = keyGen.generateKeyPair();
        privateKey = keyPair.getPrivate();
        publicKey = keyPair.getPublic();
        
        System.out.println("[Receiver] Generated RSA Key Pair");
    }
    
    public void listen() throws Exception {
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("[Receiver] Listening on port " + port);
        
        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("[Receiver] Connection accepted from " + clientSocket.getInetAddress());
            
            new Thread(() -> {
                try {
                    handleClient(clientSocket);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }
    
    private void handleClient(Socket clientSocket) throws Exception {
        ObjectInputStream objectIn = null;
        PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
        BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        
        try {
            // Send Public Key to Sender
            String publicKeyString = Base64.getEncoder().encodeToString(publicKey.getEncoded());
            writer.println(publicKeyString);
            System.out.println("[Receiver] Sent Public Key");
            
            // Receive Encrypted AES Key
            String encryptedAESKey = reader.readLine();
            SecretKey aesKey = CryptoUtils.decryptAESKeyWithRSA(privateKey, encryptedAESKey);
            System.out.println("[Receiver] Received and Decrypted AES Key");
            
            // Set up object input stream
            objectIn = new ObjectInputStream(clientSocket.getInputStream());
            
            // Receive Encrypted Messages
            while (true) {
                try {
                    Message encryptedMessage = (Message) objectIn.readObject();
                    String encryptedPayload = encryptedMessage.getPayload();
                    String decryptedPayload = AESUtils.decrypt(encryptedPayload, aesKey);
                    
                    Message decryptedMessage = new Message(decryptedPayload);
                    System.out.println("[Receiver] Received Message: " + decryptedMessage);
                } catch (EOFException e) {
                    // End of stream - connection closed
                    System.out.println("[Receiver] Connection closed");
                    break;
                }
            }
        } finally {
            if (objectIn != null) {
                objectIn.close();
            }
            clientSocket.close();
        }
    }
}