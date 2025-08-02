import java.io.*;
import java.net.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Server {
    private static final int PORT = 8888;
    private static final Logger LOGGER = Logger.getLogger(Server.class.getName());

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private BufferedReader reader;
    private PrintWriter writer;
    private DataInputStream dataInputStream;
    private DataOutputStream dataOutputStream;

    public Server() {
        try {
            serverSocket = new ServerSocket(PORT);
            LOGGER.info("Server started on port " + PORT);

            clientSocket = serverSocket.accept();
            LOGGER.info("Client connected: " + clientSocket.getInetAddress());

            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            writer = new PrintWriter(clientSocket.getOutputStream(), true);
            dataInputStream = new DataInputStream(clientSocket.getInputStream());
            dataOutputStream = new DataOutputStream(clientSocket.getOutputStream());

            startReading();
            startWriting();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error establishing connection", e);
        }
    }

    private void startReading() {
        Thread readerThread = new Thread(() -> {
            try {
                LOGGER.info("Reader thread started");
                String message;
                while ((message = reader.readLine()) != null) {
                    if (message.equalsIgnoreCase("exit")) {
                        LOGGER.info("Client terminated the connection.");
                        closeConnection();
                        break;
                    } else if (message.startsWith("SENDFILE ")) {
                        String fileName = message.substring(9).trim();
                        receiveFile(fileName);
                    } else {
                        LOGGER.info("Client: " + message);
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error reading from client", e);
            }
        });
        readerThread.start();
    }

    private void startWriting() {
        Thread writerThread = new Thread(() -> {
            try (BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in))) {
                LOGGER.info("Writer thread started");
                String message;
                while ((message = consoleReader.readLine()) != null) {
                    if (message.equalsIgnoreCase("exit")) {
                        writer.println("exit");
                        closeConnection();
                        break;
                    } else if (message.startsWith("sendfile ")) {
                        String filePath = message.substring(9).trim();
                        sendFile(filePath);
                    } else {
                        writer.println(message);
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error writing to client", e);
            }
        });
        writerThread.start();
    }

    private void sendFile(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                LOGGER.warning("File not found: " + filePath);
                return;
            }

            // Notify client
            writer.println("SENDFILE " + file.getName());

            // Send file size
            dataOutputStream.writeLong(file.length());

            // Send file content
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    dataOutputStream.write(buffer, 0, bytesRead);
                }
                dataOutputStream.flush();
                LOGGER.info("File sent successfully: " + file.getName());
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error sending file", e);
        }
    }

    private void receiveFile(String fileName) {
        try {
            File file = new File("received_" + fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                long fileSize = dataInputStream.readLong();
                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalRead = 0;

                while (totalRead < fileSize && (bytesRead = dataInputStream.read(buffer, 0,
                        (int) Math.min(buffer.length, fileSize - totalRead))) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                }

                LOGGER.info("File received successfully: " + file.getName());
                writer.println("File received: " + file.getName());
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error receiving file", e);
        }
    }

    private void closeConnection() {
        try {
            if (clientSocket != null) clientSocket.close();
            if (serverSocket != null) serverSocket.close();
            LOGGER.info("Connection closed.");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error closing connection", e);
        }
    }

    public static void main(String[] args) {
        LOGGER.info("Launching Server...");
        new Server();
    }
}
