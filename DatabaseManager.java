import java.sql.*;
import java.util.Properties;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Database Connection Manager for FileTalk
 * Handles PostgreSQL database connections and connection pooling
 */
public class DatabaseManager {
    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());
    private static DatabaseManager instance;
    private Connection connection;
    private Properties dbProperties;

    // Database configuration
    private String host;
    private int port;
    private String database;
    private String username;
    private String password;

    private DatabaseManager() {
        loadConfiguration();
        initializeConnection();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    private void loadConfiguration() {
        dbProperties = new Properties();
        try {
            // First check for Railway environment variables
            String railwayDbUrl = System.getenv("DATABASE_URL");
            if (railwayDbUrl != null && !railwayDbUrl.isEmpty()) {
                parseRailwayDatabaseUrl(railwayDbUrl);
                LOGGER.info("Using Railway database configuration");
                return;
            }

            // Fallback to application.properties
            FileInputStream fis = new FileInputStream("config/application.properties");
            dbProperties.load(fis);
            fis.close();

            // Check for individual environment variables first, then properties file
            host = System.getenv("DB_HOST");
            if (host == null)
                host = dbProperties.getProperty("db.host", "localhost");

            String portEnv = System.getenv("DB_PORT");
            port = portEnv != null ? Integer.parseInt(portEnv)
                    : Integer.parseInt(dbProperties.getProperty("db.port", "5432"));

            database = System.getenv("DB_NAME");
            if (database == null)
                database = dbProperties.getProperty("db.name", "connecthub_db");

            username = System.getenv("DB_USERNAME");
            if (username == null)
                username = dbProperties.getProperty("db.username", "connecthub_user");

            password = System.getenv("DB_PASSWORD");
            if (password == null)
                password = dbProperties.getProperty("db.password", "connecthub_pass");

            LOGGER.info("Database configuration loaded successfully");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error loading database configuration", e);
            setDefaultConfiguration();
        }
    }

    /**
     * Parse Railway's DATABASE_URL format:
     * postgresql://user:password@host:port/database
     */
    private void parseRailwayDatabaseUrl(String databaseUrl) {
        try {
            // Remove the postgresql:// prefix
            String url = databaseUrl.substring("postgresql://".length());

            // Split user:password@host:port/database
            String[] parts = url.split("@");
            String[] userPass = parts[0].split(":");
            username = userPass[0];
            password = userPass[1];

            String[] hostPortDb = parts[1].split("/");
            String[] hostPort = hostPortDb[0].split(":");
            host = hostPort[0];
            port = Integer.parseInt(hostPort[1]);
            database = hostPortDb[1];

            LOGGER.info("Parsed Railway database URL successfully");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error parsing Railway database URL", e);
            setDefaultConfiguration();
        }
    }

    private void setDefaultConfiguration() {
        host = "localhost";
        port = 5432;
        database = "connecthub_db";
        username = "connecthub_user";
        password = "connecthub_pass";
    }

    private void initializeConnection() {
        try {
            // Load PostgreSQL JDBC driver
            Class.forName("org.postgresql.Driver");

            String url = String.format("jdbc:postgresql://%s:%d/%s", host, port, database);

            Properties connectionProps = new Properties();
            connectionProps.setProperty("user", username);
            connectionProps.setProperty("password", password);
            connectionProps.setProperty("ssl", "false");
            connectionProps.setProperty("autoReconnect", "true");
            connectionProps.setProperty("characterEncoding", "UTF-8");

            connection = DriverManager.getConnection(url, connectionProps);
            connection.setAutoCommit(false); // Enable transaction management

            LOGGER.info("Database connection established successfully");

            // Test the connection
            if (testConnection()) {
                LOGGER.info("Database connection test passed");
            } else {
                LOGGER.warning("Database connection test failed");
            }

        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "PostgreSQL JDBC driver not found. Please add postgresql.jar to classpath", e);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "Failed to establish database connection. Check if PostgreSQL is running and credentials are correct",
                    e);
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                LOGGER.info("Reconnecting to database...");
                initializeConnection();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error checking connection status", e);
        }
        return connection;
    }

    public boolean testConnection() {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT 1")) {
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) == 1;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Database connection test failed", e);
            return false;
        }
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                LOGGER.info("Database connection closed");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error closing database connection", e);
        }
    }

    // Transaction management methods
    public void beginTransaction() throws SQLException {
        getConnection().setAutoCommit(false);
    }

    public void commitTransaction() throws SQLException {
        getConnection().commit();
        getConnection().setAutoCommit(true);
    }

    public void rollbackTransaction() throws SQLException {
        getConnection().rollback();
        getConnection().setAutoCommit(true);
    }

    // Health check method
    public boolean isDatabaseHealthy() {
        try {
            return connection != null && !connection.isClosed() && testConnection();
        } catch (SQLException e) {
            return false;
        }
    }

    // Get database metadata
    public String getDatabaseInfo() {
        try {
            DatabaseMetaData metaData = getConnection().getMetaData();
            return String.format("Database: %s %s, Driver: %s %s",
                    metaData.getDatabaseProductName(),
                    metaData.getDatabaseProductVersion(),
                    metaData.getDriverName(),
                    metaData.getDriverVersion());
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error getting database metadata", e);
            return "Database info unavailable";
        }
    }

    /**
     * Test method for database connection
     */
    public static void main(String[] args) {
        System.out.println("🔍 Testing Database Connection...");
        try {
            DatabaseManager dbManager = DatabaseManager.getInstance();
            if (dbManager.isDatabaseHealthy()) {
                System.out.println("✅ Database connection successful!");
                System.out.println("📊 " + dbManager.getDatabaseInfo());
            } else {
                System.out.println("❌ Database connection failed!");
            }
        } catch (Exception e) {
            System.out.println("❌ Database connection error: " + e.getMessage());
        }
    }
}
