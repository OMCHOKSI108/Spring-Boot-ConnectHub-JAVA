import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Enhanced FileTalk Client with improved user interface and features
 */
public class EnhancedClient {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 8888;
    private static final Logger LOGGER = Logger.getLogger(EnhancedClient.class.getName());

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private DataInputStream dataInputStream;
    private DataOutputStream dataOutputStream;
    private boolean isConnected = false;
    private String username;

    public EnhancedClient() {
        connectToServer();
    }

    private void connectToServer() {
        try {
            System.out.println("🌟 ===== Enhanced FileTalk Client ===== 🌟");
            System.out.println("🔗 Connecting to server at " + HOST + ":" + PORT + "...");

            socket = new Socket(HOST, PORT);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);
            dataInputStream = new DataInputStream(socket.getInputStream());
            dataOutputStream = new DataOutputStream(socket.getOutputStream());

            isConnected = true;
            System.out.println("✅ Connected to FileTalk server!");

            // Start reader thread
            startReaderThread();

            // Start writer thread (main interaction)
            startWriterThread();

        } catch (IOException e) {
            System.err.println("❌ Failed to connect to server: " + e.getMessage());
            System.err.println("💡 Make sure the server is running on " + HOST + ":" + PORT);
            LOGGER.log(Level.SEVERE, "Connection failed", e);
        }
    }

    private void startReaderThread() {
        Thread readerThread = new Thread(() -> {
            try {
                String message;
                while (isConnected && (message = reader.readLine()) != null) {
                    handleServerMessage(message);
                }
            } catch (IOException e) {
                if (isConnected) {
                    System.err.println("🔌 Connection to server lost: " + e.getMessage());
                    LOGGER.log(Level.WARNING, "Server connection lost", e);
                }
            }
        });
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void startWriterThread() {
        try (Scanner scanner = new Scanner(System.in)) {
            while (isConnected) {
                String input = scanner.nextLine().trim();

                if (input.isEmpty())
                    continue;

                if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                    disconnect();
                    break;
                }

                // Handle local commands
                if (handleLocalCommand(input)) {
                    continue;
                }

                // Send to server
                writer.println(input);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in writer thread", e);
        }
    }

    private void handleServerMessage(String message) {
        if (message.startsWith("SENDFILE ")) {
            String fileName = message.substring(9).trim();
            receiveFile(fileName);
        } else {
            // Display regular messages with better formatting
            displayMessage(message);
        }
    }

    private void displayMessage(String message) {
        // Add some visual enhancements to messages
        if (message.contains("SERVER:")) {
            System.out.println("🖥️  " + message);
        } else if (message.contains("joined the chat")) {
            System.out.println("🎉 " + message);
        } else if (message.contains("left the chat")) {
            System.out.println("👋 " + message);
        } else if (message.contains("→ You:")) {
            System.out.println("💬 " + message);
        } else if (message.contains("You →")) {
            System.out.println("📤 " + message);
        } else {
            System.out.println(message);
        }
    }

    private boolean handleLocalCommand(String command) {
        if (command.startsWith("@")) {
            // Quick private message syntax: @username message
            String[] parts = command.substring(1).split("\\s+", 2);
            if (parts.length >= 2) {
                writer.println("/msg " + parts[0] + " " + parts[1]);
                return true;
            }
        } else if (command.equalsIgnoreCase("clear")) {
            clearScreen();
            return true;
        } else if (command.equalsIgnoreCase("help")) {
            showLocalHelp();
            return true;
        }
        return false;
    }

    private void showLocalHelp() {
        System.out.println("\n🆘 ===== Client Help ===== 🆘");
        System.out.println("Local Commands:");
        System.out.println("  help           - Show this help");
        System.out.println("  clear          - Clear screen");
        System.out.println("  @user message  - Quick private message");
        System.out.println("  exit/quit      - Disconnect");
        System.out.println("\nServer Commands (send to server):");
        System.out.println("  /help          - Server help");
        System.out.println("  /users         - List online users");
        System.out.println("  /msg user text - Private message");
        System.out.println("  /sendfile path - Send file");
        System.out.println("  /history       - Message history");
        System.out.println("  /whoami        - Your info");
        System.out.println("============================\n");
    }

    private void clearScreen() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\\033[2J\\033[H");
                System.out.flush();
            }
        } catch (Exception e) {
            // Fallback: print some newlines
            for (int i = 0; i < 50; i++) {
                System.out.println();
            }
        }
    }

    private void receiveFile(String fileName) {
        try {
            long fileSize = dataInputStream.readLong();
            System.out.println("📥 Receiving file: " + fileName + " (" + formatFileSize(fileSize) + ")");

            File file = new File("downloads/received_" + fileName);
            file.getParentFile().mkdirs();

            try (FileOutputStream fos = new FileOutputStream(file);
                    BufferedOutputStream bos = new BufferedOutputStream(fos)) {

                byte[] buffer = new byte[8192];
                long totalRead = 0;
                int bytesRead;
                int lastProgress = 0;

                while (totalRead < fileSize &&
                        (bytesRead = dataInputStream.read(buffer, 0,
                                (int) Math.min(buffer.length, fileSize - totalRead))) != -1) {

                    bos.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;

                    // Show progress
                    int progress = (int) ((totalRead * 100) / fileSize);
                    if (progress >= lastProgress + 10) {
                        System.out.println("📊 Download progress: " + progress + "%");
                        lastProgress = progress;
                    }
                }

                bos.flush();
                System.out.println("✅ File downloaded: " + file.getAbsolutePath());
            }

        } catch (IOException e) {
            System.err.println("❌ Error receiving file: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "Error receiving file: " + fileName, e);
        }
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

    private void disconnect() {
        try {
            isConnected = false;
            System.out.println("🔌 Disconnecting from server...");

            if (writer != null) {
                writer.println("exit");
            }

            if (socket != null) {
                socket.close();
            }

            System.out.println("👋 Goodbye! Thanks for using FileTalk!");

        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error during disconnect", e);
        }
    }

    public static void main(String[] args) {
        // Check for command line arguments
        if (args.length > 0) {
            if ("--help".equals(args[0]) || "-h".equals(args[0])) {
                printUsage();
                return;
            }
        }

        System.out.println("🚀 Starting Enhanced FileTalk Client...");
        new EnhancedClient();
    }

    private static void printUsage() {
        System.out.println("Enhanced FileTalk Client");
        System.out.println("Usage: java EnhancedClient [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --help, -h     Show this help message");
        System.out.println();
        System.out.println("Features:");
        System.out.println("  - Multi-user chat rooms");
        System.out.println("  - Private messaging");
        System.out.println("  - File sharing");
        System.out.println("  - User authentication");
        System.out.println("  - Message history");
        System.out.println("  - PostgreSQL database integration");
        System.out.println();
        System.out.println("Quick Commands:");
        System.out.println("  @username message  - Send private message");
        System.out.println("  /help             - Show server commands");
        System.out.println("  clear             - Clear screen");
        System.out.println("  exit              - Quit application");
    }
}
