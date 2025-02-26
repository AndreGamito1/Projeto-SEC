package src.main.java.blockchain.network;

import java.io.*;

/**
 * Utility class for serializing and deserializing Message objects.
 * This is essential for sending messages over the network.
 */
public class MessageSerializer {

    /**
     * Serializes a Message object into a byte array
     *
     * @param message The message to serialize
     * @return Byte array representation of the message
     * @throws IOException If serialization fails
     */
    public static byte[] serialize(Message message) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            
            oos.writeObject(message);
            return baos.toByteArray();
        }
    }

    /**
     * Deserializes a byte array back into a Message object
     *
     * @param data Byte array to deserialize
     * @return The deserialized Message object
     * @throws IOException If deserialization fails
     * @throws ClassNotFoundException If the class of the serialized object cannot be found
     */
    public static Message deserialize(byte[] data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            
            return (Message) ois.readObject();
        }
    }
    
    /**
     * Creates a deep copy of a message by serializing and deserializing it
     * Useful for creating duplicates for testing or when needing to modify a message
     * 
     * @param message The message to clone
     * @return A deep copy of the message
     * @throws IOException If serialization/deserialization fails
     * @throws ClassNotFoundException If the class of the serialized object cannot be found
     */
    public static Message deepCopy(Message message) throws IOException, ClassNotFoundException {
        return deserialize(serialize(message));
    }
}