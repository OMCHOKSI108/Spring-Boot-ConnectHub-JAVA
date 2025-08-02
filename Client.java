import java.io.*;
import java.net.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Client {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 8888;
    private static final Logger LOGGER = Logger.getLogger(Client.class.getName());

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private DataInputStream dataInputStream;
    private DataOutputStream dataOutputStream;

    public Client() {
        try {
            LOGGER.info("Connecting to server...");
            socket = new Socket(HOST, PORT);
            LOGGER.info("Connected to server");

            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);
            dataInputStream = new DataInputStream(socket.getInputStream());
            dataOutputStream = new DataOutputStream(socket.getOutputStream());

            startReading();
            startWriting();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error connecting to server", e);
        }
    }

    private void startReading() {
        Thread readerThread = new Thread(() -> {
            try {
                LOGGER.info("Reader started");
                String message;
                while ((message = reader.readLine()) != null) {
                    if (message.equalsIgnoreCase("exit")) {
                        LOGGER.info("Server terminated the connection");
                        closeSocket();
                        break;
                    } else if (message.startsWith("SENDFILE ")) {
                        String fileName = message.substring(9).trim();
                        receiveFile(fileName);
                    } else {
                        LOGGER.info("Server: " + message);
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error reading from server", e);
            }
        });
        readerThread.start();
    }

    private void startWriting() {
        Thread writerThread = new Thread(() -> {
            try (BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in))) {
                LOGGER.info("Writer started");
                String message;
                while ((message = consoleReader.readLine()) != null) {
                    if (message.equalsIgnoreCase("exit")) {
                        writer.println("exit");
                        closeSocket();
                        break;
                    } else if (message.startsWith("sendfile ")) {
                        sendFile(message.substring(9).trim());
                    } else {
                        writer.println(message);
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error writing to server", e);
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

            // Notify server
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

    private void closeSocket() {
        try {
            if (socket != null) socket.close();
            LOGGER.info("Socket closed");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error closing socket", e);
        }
    }

    public static void main(String[] args) {
        LOGGER.info("Starting client...");
        new Client();
    }
}
