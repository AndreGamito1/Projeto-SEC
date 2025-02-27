import javax.crypto.SecretKey;
import java.io.*;
import java.net.Socket;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import java.security.KeyFactory;

public class AuthenticatedPerfectLinks {
    private String hostname;
    private int port;
    private SecretKey aesKey;

    public AuthenticatedPerfectLinks(String hostname, int port) {
        this.hostname = hostname;
        this.port = port;
    }

    public void alp2pSend(String dest, String message) throws Exception {
        Socket socket = new Socket(hostname, port);
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);

        // Receive Receiver's Public Key
        String publicKeyString = reader.readLine();
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyString);
        PublicKey receiverPublicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKeyBytes));

        // Generate AES Key
        aesKey = CryptoUtils.generateAESKey();
        System.out.println("[Sender] Generated AES Key");

        // Encrypt AES Key using Receiver's Public Key
        String encryptedAESKey = CryptoUtils.encryptAESKeyWithRSA(receiverPublicKey, aesKey);
        writer.println(encryptedAESKey);
        System.out.println("[Sender] Sent Encrypted AES Key");

        // Send Encrypted Messages
        while (true) {
            Thread.sleep(3000);
            String encryptedMessage = AESUtils.encrypt("Hello from Sender!", aesKey);
            writer.println(encryptedMessage);
            System.out.println("[Sender] Sent Encrypted Message");
        }
    }
}
