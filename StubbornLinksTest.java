import java.util.Scanner;

public class StubbornLinksTest {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java StubbornLinksTest <server_port> <destPort>");
            return;
        }

        int serverPort = Integer.parseInt(args[0]);  // Server port
        int destPort = Integer.parseInt(args[1]);    // Destination port
        String destIP = "127.0.0.1";                  // Destination IP (localhost for this example)

        try {
            // Create an instance of StubbornLinks
            StubbornLinks stubbornLinks = new StubbornLinks(destIP, destPort, serverPort);

            // Start a thread to listen for incoming messages and send acknowledgments
            new Thread(() -> {
                stubbornLinks.receiveAcknowledgment();
            }).start();

            // Read messages from the console and send them
            Scanner scanner = new Scanner(System.in);
            while (true) {
                // Prompt the user for the message payload
                System.out.print("Enter payload: ");
                String payload = scanner.nextLine();

                // Prompt the user for an optional command
                System.out.print("Enter command (optional): ");
                String command = scanner.nextLine();

                // Create a Message object
                Message message;
                if (command.isEmpty()) {
                    message = new Message(payload); // No command
                } else {
                    message = new Message(payload, command); // With command
                }

                // Send the Message object using stubbornLinks
                stubbornLinks.sp2pSend(message);
                System.out.println("Sent message: " + message);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
