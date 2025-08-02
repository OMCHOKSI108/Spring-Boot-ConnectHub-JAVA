import java.io.*;
import java.net.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Handles individual client connections
 * Each client gets its own ClientHandler instance running in a separate thread
 */
public class ClientHandler implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());

    private final Socket clientSocket;
    private final EnhancedServer server;
    private BufferedReader reader;
    private PrintWriter writer;
    private DataInputStream dataInputStream;
    private DataOutputStream dataOutputStream;
    private User user;
    private boolean isConnected = true;

    public ClientHandler(Socket clientSocket, EnhancedServer server) {
        this.clientSocket = clientSocket;
        this.server = server;
        initializeStreams();
    }

    private void initializeStreams() {
        try {
            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            writer = new PrintWriter(clientSocket.getOutputStream(), true);
            dataInputStream = new DataInputStream(clientSocket.getInputStream());
            dataOutputStream = new DataOutputStream(clientSocket.getOutputStream());

            LOGGER.info("Client streams initialized for: " + clientSocket.getInetAddress());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize client streams", e);
            disconnect("Stream initialization failed");
        }
    }

    @Override
    public void run() {
        try {
            // First, handle user authentication
            if (authenticateUser()) {
                server.addClient(user.getUserId(), this);
                sendWelcomeMessage();
                sendOnlineUsersList();

                // Main message processing loop
                processMessages();
            } else {
                sendMessage("❌ Authentication failed. Disconnecting...");
                disconnect("Authentication failed");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in client handler", e);
        } finally {
            cleanup();
        }
    }

    private boolean authenticateUser() {
        try {
            sendMessage("🔐 Welcome to FileTalk! Please login or register.");
            sendMessage("💡 Commands: LOGIN <username> <password> or REGISTER <username> <password> <display_name>");

            int attempts = 0;
            final int MAX_ATTEMPTS = 3;

            while (attempts < MAX_ATTEMPTS && isConnected) {
                String authMessage = reader.readLine();
                if (authMessage == null) {
                    return false;
                }

                String[] parts = authMessage.trim().split("\\s+");
                if (parts.length < 3) {
                    sendMessage(
                            "❌ Invalid format. Use: LOGIN <username> <password> or REGISTER <username> <password> <display_name>");
                    attempts++;
                    continue;
                }

                String command = parts[0].toUpperCase();
                String username = parts[1];
                String password = parts[2];

                if ("LOGIN".equals(command)) {
                    user = server.getUserDAO().authenticateUser(username, hashPassword(password));
                    if (user != null) {
                        sendMessage("✅ Login successful! Welcome back, " + user.getDisplayName());
                        return true;
                    } else {
                        sendMessage("❌ Invalid username or password");
                    }
                } else if ("REGISTER".equals(command)) {
                    if (parts.length < 4) {
                        sendMessage("❌ Registration format: REGISTER <username> <password> <display_name>");
                        attempts++;
                        continue;
                    }

                    String displayName = parts[3];
                    if (server.getUserDAO().usernameExists(username)) {
                        sendMessage("❌ Username already exists");
                    } else {
                        User newUser = new User(username, hashPassword(password), displayName);
                        if (server.getUserDAO().createUser(newUser)) {
                            user = newUser;
                            sendMessage("✅ Registration successful! Welcome, " + displayName);
                            return true;
                        } else {
                            sendMessage("❌ Registration failed. Please try again.");
                        }
                    }
                } else {
                    sendMessage("❌ Unknown command. Use LOGIN or REGISTER");
                }

                attempts++;
            }

            if (attempts >= MAX_ATTEMPTS) {
                sendMessage("❌ Too many failed attempts. Disconnecting...");
            }
            return false;

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error during authentication", e);
            return false;
        }
    }

    private void processMessages() {
        try {
            String message;
            while (isConnected && (message = reader.readLine()) != null) {
                handleMessage(message);
            }
        } catch (IOException e) {
            if (isConnected) {
                LOGGER.log(Level.WARNING, "Client disconnected unexpectedly: " + getDisplayInfo(), e);
            }
        }
    }

    private void handleMessage(String message) {
        try {
            message = message.trim();
            if (message.isEmpty())
                return;

            // Handle commands
            if (message.startsWith("/")) {
                handleCommand(message);
            } else if (message.startsWith("SENDFILE ")) {
                String fileName = message.substring(9).trim();
                receiveFile(fileName);
            } else if (message.equalsIgnoreCase("exit")) {
                disconnect("User requested disconnect");
            } else {
                // Regular chat message
                server.broadcastMessage(message, user.getUserId());
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error handling message from " + getDisplayInfo(), e);
            sendMessage("❌ Error processing your message. Please try again.");
        }
    }

    private void handleCommand(String command) {
        String[] parts = command.substring(1).split("\\s+", 3);
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "help":
                sendHelpMessage();
                break;
            case "users":
            case "online":
                sendOnlineUsersList();
                break;
            case "msg":
            case "pm":
                if (parts.length >= 3) {
                    String toUsername = parts[1];
                    String privateMessage = parts[2];
                    server.sendPrivateMessage(user.getUsername(), toUsername, privateMessage);
                } else {
                    sendMessage("❌ Usage: /msg <username> <message>");
                }
                break;
            case "sendfile":
                if (parts.length >= 2) {
                    String filePath = parts[1];
                    sendFile(filePath);
                } else {
                    sendMessage("❌ Usage: /sendfile <file_path>");
                }
                break;
            case "history":
                sendRecentMessages();
                break;
            case "whoami":
                sendMessage("👤 You are: " + user.getDisplayName() + " (@" + user.getUsername() + ")");
                break;
            default:
                sendMessage("❌ Unknown command: " + cmd + ". Type /help for available commands.");
        }
    }

    private void sendHelpMessage() {
        sendMessage("\n=== 📋 FileTalk Commands ===");
        sendMessage("/help          - Show this help message");
        sendMessage("/users         - List online users");
        sendMessage("/msg <user> <text> - Send private message");
        sendMessage("/sendfile <path>   - Send a file");
        sendMessage("/history       - Show recent messages");
        sendMessage("/whoami        - Show your info");
        sendMessage("exit           - Disconnect from server");
        sendMessage("============================\n");
    }

    private void sendWelcomeMessage() {
        sendMessage("\n🌟 ===== Welcome to Enhanced FileTalk ===== 🌟");
        sendMessage("👋 Hello " + user.getDisplayName() + "!");
        sendMessage("💬 You can now chat with other users");
        sendMessage("📁 File sharing is enabled");
        sendMessage("❓ Type /help for available commands");
        sendMessage("=====================================\n");
    }

    private void sendOnlineUsersList() {
        java.util.List<String> onlineUsers = server.getOnlineUsersList();
        sendMessage("\n👥 === Online Users (" + onlineUsers.size() + ") ===");
        for (String username : onlineUsers) {
            sendMessage("🟢 " + username);
        }
        sendMessage("=============================\n");
    }

    private void sendRecentMessages() {
        // This would get recent messages from database
        sendMessage("📜 Recent message history feature coming soon!");
    }

    private void sendFile(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                sendMessage("❌ File not found: " + filePath);
                return;
            }

            if (file.length() > 100 * 1024 * 1024) { // 100MB limit
                sendMessage("❌ File too large. Maximum size is 100MB.");
                return;
            }

            // Notify about file transfer
            sendMessage("📤 Sending file: " + file.getName() + " (" + formatFileSize(file.length()) + ")");
            server.broadcastMessage("📁 " + user.getDisplayName() + " is sharing file: " + file.getName(),
                    user.getUserId(), user.getUserId());

            // Send file metadata
            writer.println("SENDFILE " + file.getName());
            dataOutputStream.writeLong(file.length());

            // Send file content
            try (FileInputStream fis = new FileInputStream(file);
                    BufferedInputStream bis = new BufferedInputStream(fis)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalSent = 0;

                while ((bytesRead = bis.read(buffer)) != -1) {
                    dataOutputStream.write(buffer, 0, bytesRead);
                    totalSent += bytesRead;

                    // Show progress for large files
                    if (file.length() > 1024 * 1024) { // > 1MB
                        int progress = (int) ((totalSent * 100) / file.length());
                        if (progress % 25 == 0) {
                            sendMessage("📊 Upload progress: " + progress + "%");
                        }
                    }
                }

                dataOutputStream.flush();
                sendMessage("✅ File sent successfully: " + file.getName());
                LOGGER.info("File sent by " + user.getUsername() + ": " + file.getName());
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error sending file: " + filePath, e);
            sendMessage("❌ Error sending file: " + e.getMessage());
        }
    }

    private void receiveFile(String fileName) {
        try {
            long fileSize = dataInputStream.readLong();
            File file = new File("uploads/received_" + user.getUsername() + "_" + fileName);

            // Create uploads directory if it doesn't exist
            file.getParentFile().mkdirs();

            sendMessage("📥 Receiving file: " + fileName + " (" + formatFileSize(fileSize) + ")");

            try (FileOutputStream fos = new FileOutputStream(file);
                    BufferedOutputStream bos = new BufferedOutputStream(fos)) {

                byte[] buffer = new byte[8192];
                long totalRead = 0;
                int bytesRead;

                while (totalRead < fileSize &&
                        (bytesRead = dataInputStream.read(buffer, 0,
                                (int) Math.min(buffer.length, fileSize - totalRead))) != -1) {

                    bos.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;

                    // Show progress for large files
                    if (fileSize > 1024 * 1024) { // > 1MB
                        int progress = (int) ((totalRead * 100) / fileSize);
                        if (progress % 25 == 0) {
                            sendMessage("📊 Download progress: " + progress + "%");
                        }
                    }
                }

                bos.flush();
                sendMessage("✅ File received successfully: " + file.getName());
                LOGGER.info("File received by " + user.getUsername() + ": " + fileName);
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error receiving file: " + fileName, e);
            sendMessage("❌ Error receiving file: " + e.getMessage());
        }
    }

    public void sendMessage(String message) {
        if (writer != null && isConnected) {
            writer.println(message);
        }
    }

    public void disconnect(String reason) {
        if (isConnected) {
            isConnected = false;
            sendMessage("🔌 Disconnecting: " + reason);
            LOGGER.info("Client disconnected: " + getDisplayInfo() + " - " + reason);
        }
    }

    private void cleanup() {
        try {
            isConnected = false;

            // Remove from server
            if (user != null) {
                server.removeClient(user.getUserId());
            }

            // Close streams
            if (reader != null)
                reader.close();
            if (writer != null)
                writer.close();
            if (dataInputStream != null)
                dataInputStream.close();
            if (dataOutputStream != null)
                dataOutputStream.close();
            if (clientSocket != null)
                clientSocket.close();

        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error during cleanup for " + getDisplayInfo(), e);
        }
    }

    // Utility methods
    private String hashPassword(String password) {
        // Simple hash for demo - in production, use bcrypt or similar
        return password + "_hashed";
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    public String getDisplayInfo() {
        if (user != null) {
            return user.getDisplayName() + " (@" + user.getUsername() + ") from " +
                    clientSocket.getInetAddress().getHostAddress();
        }
        return "Anonymous from " + clientSocket.getInetAddress().getHostAddress();
    }

    // Getters
    public User getUser() {
        return user;
    }

    public boolean isConnected() {
        return isConnected;
    }
}
