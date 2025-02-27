public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: java Main <port>");
            return;
        }

        int port = Integer.parseInt(args[0]);

        // If port is even, act as a receiver; if odd, act as a sender.
        if (port % 2 == 0) {
            System.out.println("[Receiver] Listening on port " + port);
            AuthenticatedReceiver receiver = new AuthenticatedReceiver(port);
            receiver.listen();
        } else {
            System.out.println("[Sender] Sending messages to port " + (port - 1));
            AuthenticatedPerfectLinks alp2p = new AuthenticatedPerfectLinks("localhost", port - 1);
            while (true) {
                Thread.sleep(3000); // Send every 3 seconds
                alp2p.alp2pSend("Receiver", "Hello from port " + port);
            }
        }
    }
}
