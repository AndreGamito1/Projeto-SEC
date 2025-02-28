import java.util.Scanner;

public class StubbornLinksTest {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java StubbornLinksTest <server_port> <dest_port>");
            return;
        }

        int server_port = Integer.parseInt(args[0]);
        int destPort = Integer.parseInt(args[1]);
        String destIP = "127.0.0.1";

        try {
            // Create an instance of StubbornLinks
            StubbornLinks stubbornLinks = new StubbornLinks(destIP, destPort, server_port);

            // Start a thread to listen for incoming messages and send acknowledgments
            new Thread(() -> {
                stubbornLinks.receiveAcknowledgment();
            }).start();

            // Read messages from the console and send them
            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.print("Enter message: ");
                String message = scanner.nextLine();
                stubbornLinks.sp2pSend(message);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}