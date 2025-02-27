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
            
            // Option 1: Send a single message with "Hello world!" payload
            Message message = new Message("Hello world!");
            alp2p.alp2pSend("Receiver", message);
            
            // Option 2: For continuous sending as in your original code
            /*
            while (true) {
                Thread.sleep(3000); // Send every 3 seconds
                Message message = new Message("Hello from port " + port);
                alp2p.alp2pSend("Receiver", message);
            }
            */
            
            // Option 3: Use the continuousSend method
            /*
            alp2p.continuousSend("Receiver", "Hello from port " + port);
            */
        }
    }
}