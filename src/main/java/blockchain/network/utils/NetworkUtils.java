package src.main.java.blockchain.network.utils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for network-related operations.
 */
public class NetworkUtils {
    private static final Logger LOGGER = Logger.getLogger(NetworkUtils.class.getName());
    
    /**
     * Determines if the current host is likely on a local network
     * 
     * @return true if the host has a local network address
     */
    public static boolean isLocalNetwork() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isUp() && !networkInterface.isLoopback()) {
                    Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress address = addresses.nextElement();
                        if (address.isSiteLocalAddress()) {
                            return true;
                        }
                    }
                }
            }
        } catch (SocketException e) {
            LOGGER.log(Level.WARNING, "Error checking network interfaces", e);
        }
        return false;
    }
    
    /**
     * Gets the local host address
     * 
     * @return The local host address or localhost if it cannot be determined
     */
    public static String getLocalHostAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isUp() && !networkInterface.isLoopback()) {
                    Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress address = addresses.nextElement();
                        if (address.isSiteLocalAddress()) {
                            return address.getHostAddress();
                        }
                    }
                }
            }
        } catch (SocketException e) {
            LOGGER.log(Level.WARNING, "Error getting local host address", e);
        }
        return "127.0.0.1";
    }
    
    /**
     * Checks if a port is available
     * 
     * @param port The port to check
     * @return true if the port is available
     */
    public static boolean isPortAvailable(int port) {
        if (port < 1024 || port > 65535) {
            return false;
        }
        
        try (java.net.ServerSocket socket = new java.net.ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Finds an available port starting from the given port
     * 
     * @param startPort The port to start checking from
     * @return An available port or -1 if none is found
     */
    public static int findAvailablePort(int startPort) {
        for (int port = startPort; port < startPort + 100; port++) {
            if (isPortAvailable(port)) {
                return port;
            }
        }
        return -1;
    }
}