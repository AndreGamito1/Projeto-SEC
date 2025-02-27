import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Message {
    private final String payload;
    private final byte[] hmac;
    private final String sender;

    public Message(String payload, String sender, byte[] key) throws Exception {
        this.payload = payload;
        this.sender = sender;
        this.hmac = generateHMAC(payload, key);
    }

    private byte[] generateHMAC(String message, byte[] key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(key, "HmacSHA256");
        mac.init(secretKey);
        return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    }

    public boolean verifyHMAC(byte[] key) throws Exception {
        byte[] expectedHmac = generateHMAC(payload, key);
        return Arrays.equals(expectedHmac, this.hmac);
    }

    public String getPayload() {
        return payload;
    }

    public String getSender() {
        return sender;
    }

    public byte[] getHmac() {
        return hmac;
    }
}
