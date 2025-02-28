import java.util.Scanner;

public class AuthenticatedPerfectLinksTest {

    public static void main(String[] args) {
        // Ensure proper usage
        if (args.length != 3) {
            System.out.println("Usage: java AuthenticatedPerfectLinksTest <mode> <port1> <port2>");
            System.out.println("Mode should be 'server' or 'client'.");
            return;
        }

        String mode = args[0];
        int port1 = Integer.parseInt(args[1]); // Port for the server (client will use this as destination)
        int port2 = Integer.parseInt(args[2]); // Port for communication (server listens on port1, client sends to port2)

        try {
            if (mode.equals("server")) {
                // Server mode
                runServer(port1, port2);
            } else if (mode.equals("client")) {
                // Client mode
                runClient(port1, port2);
            } else {
                System.out.println("Invalid mode! Use 'server' or 'client'.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Server mode: listens for incoming messages
    private static void runServer(int serverPort, int clientPort) throws Exception {
        AuthenticatedPerfectLinks authenticatedLinks = new AuthenticatedPerfectLinks("127.0.0.1", clientPort, serverPort);
        authenticatedLinks.receiveMessages();
        System.out.println("Server is listening on port " + serverPort + "...");

        // The thread handling incoming messages will automatically process them
        // No need for additional user input for the server
    }

    // Client mode: sends messages to the server
    private static void runClient(int serverPort, int clientPort) throws Exception {
        AuthenticatedPerfectLinks authenticatedLinks = new AuthenticatedPerfectLinks("127.0.0.1", serverPort, clientPort);
        authenticatedLinks.receiveMessages();

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

            // Send the message object using authenticated links
            authenticatedLinks.alp2pSend(message);
            System.out.println("Sent message: " + message);
        }
    }
}
