import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Data Access Object for User operations
 * Handles all database operations related to users
 */
public class UserDAO {
    private static final Logger LOGGER = Logger.getLogger(UserDAO.class.getName());
    private final DatabaseManager dbManager;

    public UserDAO() {
        this.dbManager = DatabaseManager.getInstance();
    }

    /**
     * Create a new user in the database
     */
    public boolean createUser(User user) {
        String sql = "INSERT INTO users (username, password_hash, email, display_name, avatar_url, is_admin, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try {
            PreparedStatement stmt = dbManager.getConnection().prepareStatement(sql,
                    Statement.RETURN_GENERATED_KEYS);
            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getPasswordHash());
            stmt.setString(3, user.getEmail());
            stmt.setString(4, user.getDisplayName());
            stmt.setString(5, user.getAvatarUrl());
            stmt.setBoolean(6, user.isAdmin());
            stmt.setString(7, user.getStatus());

            dbManager.beginTransaction();
            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                // Get the generated user ID
                ResultSet generatedKeys = stmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    user.setUserId(generatedKeys.getInt(1));
                }
                dbManager.commitTransaction();
                LOGGER.info("User created successfully: " + user.getUsername());
                return true;
            }
            dbManager.rollbackTransaction();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating user: " + user.getUsername(), e);
            dbManager.rollbackTransaction();
        }
        return false;
    }

    /**
     * Find user by username
     */
    public User findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ? AND status = 'active'";

        try (PreparedStatement stmt = dbManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToUser(rs);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error finding user by username: " + username, e);
        }
        return null;
    }

    /**
     * Find user by ID
     */
    public User findById(int userId) {
        String sql = "SELECT * FROM users WHERE user_id = ? AND status = 'active'";

        try (PreparedStatement stmt = dbManager.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToUser(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding user by ID: " + userId, e);
        }
        return null;
    }

    /**
     * Update user's online status
     */
    public boolean updateOnlineStatus(int userId, boolean isOnline) {
        String sql = "UPDATE users SET is_online = ?, last_seen = CURRENT_TIMESTAMP WHERE user_id = ?";

        try (PreparedStatement stmt = dbManager.getConnection().prepareStatement(sql)) {
            stmt.setBoolean(1, isOnline);
            stmt.setInt(2, userId);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                dbManager.commitTransaction();
                return true;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error updating online status for user ID: " + userId, e);
            try {
                dbManager.rollbackTransaction();
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Error rolling back transaction", ex);
            }
        }
        return false;
    }

    /**
     * Get all online users
     */
    public List<User> getOnlineUsers() {
        List<User> onlineUsers = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE is_online = true AND status = 'active' ORDER BY username";

        try (PreparedStatement stmt = dbManager.getConnection().prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                onlineUsers.add(mapResultSetToUser(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error getting online users", e);
        }
        return onlineUsers;
    }

    /**
     * Authenticate user login
     */
    public User authenticateUser(String username, String passwordHash) {
        User user = findByUsername(username);
        if (user != null && user.getPasswordHash().equals(passwordHash)) {
            // Update last seen and online status
            updateOnlineStatus(user.getUserId(), true);
            user.setOnline(true);
            return user;
        }
        return null;
    }

    /**
     * Update user profile
     */
    public boolean updateUser(User user) {
        String sql = "UPDATE users SET email = ?, display_name = ?, avatar_url = ? WHERE user_id = ?";

        try (PreparedStatement stmt = dbManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, user.getEmail());
            stmt.setString(2, user.getDisplayName());
            stmt.setString(3, user.getAvatarUrl());
            stmt.setInt(4, user.getUserId());

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                dbManager.commitTransaction();
                LOGGER.info("User updated successfully: " + user.getUsername());
                return true;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error updating user: " + user.getUsername(), e);
            dbManager.rollbackTransaction();
        }
        return false;
    }

    /**
     * Check if username exists
     */
    public boolean usernameExists(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";

        try (PreparedStatement stmt = dbManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error checking username existence: " + username, e);
        }
        return false;
    }

    /**
     * Map ResultSet to User object
     */
    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setUserId(rs.getInt("user_id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setEmail(rs.getString("email"));
        user.setDisplayName(rs.getString("display_name"));
        user.setAvatarUrl(rs.getString("avatar_url"));
        user.setOnline(rs.getBoolean("is_online"));
        user.setLastSeen(rs.getTimestamp("last_seen"));
        user.setCreatedAt(rs.getTimestamp("created_at"));
        user.setAdmin(rs.getBoolean("is_admin"));
        user.setStatus(rs.getString("status"));
        return user;
    }
}
