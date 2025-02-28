import java.util.Scanner;

public class ClientLibrary {
    private static final String LEADER_HOST = "localhost";
    private static final int LEADER_PORT = 5000;
    
    public static void main(String[] args) throws Exception {
        ClientLibrary library = new ClientLibrary();
        library.startTerminalInterface();
    }
    
    public void startTerminalInterface() throws Exception {
        Scanner scanner = new Scanner(System.in);
        AuthenticatedPerfectLinks alp2p = new AuthenticatedPerfectLinks(LEADER_HOST, LEADER_PORT);
        
        System.out.println("Client Library started. Connected to Leader on port " + LEADER_PORT);
        
        boolean running = true;
        while (running) {
            System.out.println("\nSelect an option:");
            System.out.println("1. Buy (sends <\"buy\", \"2\">)");
            System.out.println("2. Sell (sends <\"sell\", \"1\">)");
            System.out.println("3. Exit");
            System.out.print("> ");
            
            String input = scanner.nextLine().trim();
            
            switch (input) {
                case "1":
                    Message buyMessage = new Message("buy", "2");
                    alp2p.alp2pSend("Client", buyMessage);
                    System.out.println("Sent message: <\"buy\", \"2\">");
                    break;
                case "2":
                    Message sellMessage = new Message("sell", "1");
                    alp2p.alp2pSend("Client", sellMessage);
                    System.out.println("Sent message: <\"sell\", \"1\">");
                    break;
                case "3":
                    running = false;
                    System.out.println("Exiting...");
                    break;
                default:
                    System.out.println("Invalid option. Please try again.");
                    break;
            }
        }
        
        scanner.close();
    }
}