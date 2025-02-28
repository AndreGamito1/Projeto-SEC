public class Leader {
    private int port;
    
    public Leader(int port) {
        this.port = port;
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: java Leader <port>");
            return;
        }
        
        int port = Integer.parseInt(args[0]);
        Leader leader = new Leader(port);
        leader.start();
    }
    
    public void start() {
        System.out.println("[Leader] Started on port " + port);
        
        try {
            // Create and start the AuthenticatedReceiver
            System.out.println("[Leader] Listening on port " + port);
            AuthenticatedReceiver receiver = new AuthenticatedReceiver(port);
            // Start listening for client messages
            // The message processing will happen inside the AuthenticatedReceiver
            receiver.listen();
            
        } catch (Exception e) {
            System.err.println("Error in leader: " + e.getMessage());
            e.printStackTrace();
        }
    }
}