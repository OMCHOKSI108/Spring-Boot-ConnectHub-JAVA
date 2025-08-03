import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.sql.Timestamp;

/**
 * Enhanced FileTalk Server with PostgreSQL integration
 * Supports multiple clients, user authentication, and database persistence
 */
public class EnhancedServer {
    private static final int DEFAULT_PORT = 8888;
    private static final Logger LOGGER = Logger.getLogger(EnhancedServer.class.getName());

    private ServerSocket serverSocket;
    private final Map<Integer, ClientHandler> connectedClients = new ConcurrentHashMap<>();
    private final Map<String, Integer> usernameToUserId = new ConcurrentHashMap<>();
    private final ExecutorService clientThreadPool = Executors.newCachedThreadPool();
    private volatile boolean isRunning = true;
    private int port;

    // Database components
    private DatabaseManager dbManager;
    private UserDAO userDAO;
    private MessageDAO messageDAO;

    public EnhancedServer() {
        // Get port from environment variable or use default
        String portEnv = System.getenv("PORT");
        port = portEnv != null ? Integer.parseInt(portEnv) : DEFAULT_PORT;

        initializeDatabase();
        startServer();
    }

    private void initializeDatabase() {
        try {
            dbManager = DatabaseManager.getInstance();
            userDAO = new UserDAO();
            messageDAO = new MessageDAO();

            if (dbManager.isDatabaseHealthy()) {
                LOGGER.info("Database connection established successfully");
                LOGGER.info(dbManager.getDatabaseInfo());
            } else {
                LOGGER.severe("Database connection failed - running in offline mode");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize database", e);
        }
    }

    private void startServer() {
        try {
            serverSocket = new ServerSocket(port);
            LOGGER.info("🚀 Enhanced FileTalk Server started on port " + port);
            LOGGER.info("📊 Max clients supported: " + Runtime.getRuntime().availableProcessors() * 10);

            // Start server management thread
            startServerConsole();

            // Accept client connections
            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    LOGGER.info("📱 New client connection from: " + clientSocket.getInetAddress());

                    // Create and start client handler
                    ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                    clientThreadPool.submit(clientHandler);

                } catch (IOException e) {
                    if (isRunning) {
                        LOGGER.log(Level.SEVERE, "Error accepting client connection", e);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to start server", e);
        } finally {
            shutdown();
        }
    }

    private void startServerConsole() {
        Thread consoleThread = new Thread(() -> {
            try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
                LOGGER.info("💬 Server console started. Type 'help' for commands.");
                String command;

                while (isRunning && (command = console.readLine()) != null) {
                    handleServerCommand(command.trim());
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error in server console", e);
            }
        });
        consoleThread.setDaemon(true);
        consoleThread.start();
    }

    private void handleServerCommand(String command) {
        String[] parts = command.split("\\s+");
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "help":
                printHelp();
                break;
            case "status":
                printServerStatus();
                break;
            case "clients":
                printConnectedClients();
                break;
            case "broadcast":
                if (parts.length > 1) {
                    String message = command.substring(command.indexOf(' ') + 1);
                    broadcastServerMessage(message);
                } else {
                    System.out.println("Usage: broadcast <message>");
                }
                break;
            case "kick":
                if (parts.length > 1) {
                    kickUser(parts[1]);
                } else {
                    System.out.println("Usage: kick <username>");
                }
                break;
            case "shutdown":
                LOGGER.info("🛑 Server shutdown initiated by admin");
                isRunning = false;
                break;
            default:
                System.out.println("Unknown command: " + cmd + ". Type 'help' for available commands.");
        }
    }

    private void printHelp() {
        System.out.println("\n=== Server Commands ===");
        System.out.println("help      - Show this help message");
        System.out.println("status    - Show server status");
        System.out.println("clients   - List connected clients");
        System.out.println("broadcast <message> - Send message to all clients");
        System.out.println("kick <username> - Disconnect a user");
        System.out.println("shutdown  - Stop the server");
        System.out.println("========================\n");
    }

    private void printServerStatus() {
        System.out.println("\n=== Server Status ===");
        System.out.println("Port: " + port);
        System.out.println("Connected clients: " + connectedClients.size());
        System.out.println("Database status: " + (dbManager.isDatabaseHealthy() ? "✅ Healthy" : "❌ Offline"));
        System.out.println("Uptime: " + getUptime());
        System.out.println("====================\n");
    }

    private void printConnectedClients() {
        System.out.println("\n=== Connected Clients ===");
        if (connectedClients.isEmpty()) {
            System.out.println("No clients connected");
        } else {
            for (ClientHandler client : connectedClients.values()) {
                System.out.println("👤 " + client.getDisplayInfo());
            }
        }
        System.out.println("========================\n");
    }

    private String getUptime() {
        // Simple uptime calculation - you can make this more sophisticated
        return "Running";
    }

    // Client management methods
    public synchronized void addClient(int userId, ClientHandler client) {
        connectedClients.put(userId, client);
        if (client.getUser() != null) {
            usernameToUserId.put(client.getUser().getUsername(), userId);
            userDAO.updateOnlineStatus(userId, true);
            notifyUserJoined(client.getUser());
        }
        LOGGER.info("👥 Client added. Total connected: " + connectedClients.size());
    }

    public synchronized void removeClient(int userId) {
        ClientHandler client = connectedClients.remove(userId);
        if (client != null && client.getUser() != null) {
            usernameToUserId.remove(client.getUser().getUsername());
            userDAO.updateOnlineStatus(userId, false);
            notifyUserLeft(client.getUser());
        }
        LOGGER.info("👥 Client removed. Total connected: " + connectedClients.size());
    }

    public void broadcastMessage(String message, int senderUserId) {
        broadcastMessage(message, senderUserId, -1);
    }

    public void broadcastMessage(String message, int senderUserId, int excludeUserId) {
        ClientHandler sender = connectedClients.get(senderUserId);
        String senderName = (sender != null && sender.getUser() != null) ? sender.getUser().getDisplayName()
                : "Unknown";

        String formattedMessage = String.format("[%s] %s: %s",
                getCurrentTimestamp(), senderName, message);

        // Save message to database
        if (dbManager.isDatabaseHealthy()) {
            try {
                Message msg = new Message();
                msg.setSenderId(senderUserId);
                msg.setMessageText(message);
                msg.setMessageType("text");
                messageDAO.saveMessage(msg);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to save message to database", e);
            }
        }

        // Broadcast to all connected clients
        for (Map.Entry<Integer, ClientHandler> entry : connectedClients.entrySet()) {
            if (entry.getKey() != excludeUserId) {
                entry.getValue().sendMessage(formattedMessage);
            }
        }
    }

    public void broadcastServerMessage(String message) {
        String formattedMessage = String.format("[%s] 🖥️ SERVER: %s",
                getCurrentTimestamp(), message);

        for (ClientHandler client : connectedClients.values()) {
            client.sendMessage(formattedMessage);
        }
        LOGGER.info("📢 Server message broadcasted: " + message);
    }

    private void notifyUserJoined(User user) {
        String message = String.format("👋 %s joined the chat", user.getDisplayName());
        broadcastServerMessage(message);
    }

    private void notifyUserLeft(User user) {
        String message = String.format("👋 %s left the chat", user.getDisplayName());
        broadcastServerMessage(message);
    }

    private void kickUser(String username) {
        Integer userId = usernameToUserId.get(username);
        if (userId != null) {
            ClientHandler client = connectedClients.get(userId);
            if (client != null) {
                client.disconnect("You have been kicked by the server administrator");
                System.out.println("User " + username + " has been kicked");
            }
        } else {
            System.out.println("User " + username + " not found");
        }
    }

    public void sendPrivateMessage(String fromUsername, String toUsername, String message) {
        Integer toUserId = usernameToUserId.get(toUsername);
        if (toUserId != null) {
            ClientHandler recipient = connectedClients.get(toUserId);
            if (recipient != null) {
                String formattedMessage = String.format("[%s] 💬 %s → You: %s",
                        getCurrentTimestamp(), fromUsername, message);
                recipient.sendMessage(formattedMessage);

                // Send confirmation to sender
                Integer fromUserId = usernameToUserId.get(fromUsername);
                if (fromUserId != null) {
                    ClientHandler sender = connectedClients.get(fromUserId);
                    if (sender != null) {
                        String confirmMessage = String.format("[%s] 💬 You → %s: %s",
                                getCurrentTimestamp(), toUsername, message);
                        sender.sendMessage(confirmMessage);
                    }
                }
            }
        }
    }

    public List<String> getOnlineUsersList() {
        List<String> onlineUsers = new ArrayList<>();
        for (ClientHandler client : connectedClients.values()) {
            if (client.getUser() != null) {
                onlineUsers.add(client.getUser().getDisplayName());
            }
        }
        return onlineUsers;
    }

    private String getCurrentTimestamp() {
        return new Timestamp(System.currentTimeMillis()).toString().substring(11, 19);
    }

    // Getters for ClientHandler access
    public UserDAO getUserDAO() {
        return userDAO;
    }

    public MessageDAO getMessageDAO() {
        return messageDAO;
    }

    public DatabaseManager getDbManager() {
        return dbManager;
    }

    private void shutdown() {
        try {
            isRunning = false;

            // Disconnect all clients
            for (ClientHandler client : connectedClients.values()) {
                client.disconnect("Server is shutting down");
            }

            // Shutdown thread pool
            clientThreadPool.shutdown();
            try {
                if (!clientThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    clientThreadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                clientThreadPool.shutdownNow();
            }

            // Close server socket
            if (serverSocket != null) {
                serverSocket.close();
            }

            // Close database connection
            if (dbManager != null) {
                dbManager.closeConnection();
            }

            LOGGER.info("🛑 Server shutdown completed");

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error during server shutdown", e);
        }
    }

    public static void main(String[] args) {
        LOGGER.info("🌟 Starting Enhanced FileTalk Server with PostgreSQL...");
        new EnhancedServer();
    }
}
