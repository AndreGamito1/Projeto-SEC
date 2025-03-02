/*package com.example;
import java.security.*;
import java.util.*;

public class AuthenticatedPerfectLinksTest {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java AuthenticatedPerfectLinksTest <serverPort> <destPort>");
            return;
        }
        Logger.initFromArgs("--log=all");

        int serverPort = Integer.parseInt(args[0]); // Server port
        int destPort = Integer.parseInt(args[1]);   // Destination port
        String destIP = "127.0.0.1";                 // Destination IP (localhost for this example)

        try {
            AuthenticatedPerfectLinks alp2p = new AuthenticatedPerfectLinks(
                destIP, destPort, serverPort);

            Scanner scanner = new Scanner(System.in);

            while (true) {
                System.out.print("Enter payload: ");
                String payload = scanner.nextLine();

                Message message = new Message(generateMessageID(), payload);
                alp2p.alp2pSend("dest", message);
                System.out.println("Sent authenticated message with payload: " + payload);

                System.out.println("Received message count: " + alp2p.getReceivedSize());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String generateMessageID() {
        return UUID.randomUUID().toString();
    }
}
*/