import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Data Access Object for Message operations
 */
public class MessageDAO {
    private static final Logger LOGGER = Logger.getLogger(MessageDAO.class.getName());
    private final DatabaseManager dbManager;

    public MessageDAO() {
        this.dbManager = DatabaseManager.getInstance();
    }

    /**
     * Save a new message to the database
     */
    public boolean saveMessage(Message message) {
        String sql = "INSERT INTO messages (sender_id, room_id, recipient_id, message_text, " +
                "message_type, file_path, file_size, file_name, is_encrypted, reply_to) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = dbManager.getConnection().prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, message.getSenderId());

            // Handle optional room_id
            if (message.getRoomId() > 0) {
                stmt.setInt(2, message.getRoomId());
            } else {
                stmt.setNull(2, Types.INTEGER);
            }

            // Handle optional recipient_id
            if (message.getRecipientId() != null) {
                stmt.setInt(3, message.getRecipientId());
            } else {
                stmt.setNull(3, Types.INTEGER);
            }

            stmt.setString(4, message.getMessageText());
            stmt.setString(5, message.getMessageType());
            stmt.setString(6, message.getFilePath());

            if (message.getFileSize() != null) {
                stmt.setLong(7, message.getFileSize());
            } else {
                stmt.setNull(7, Types.BIGINT);
            }

            stmt.setString(8, message.getFileName());
            stmt.setBoolean(9, message.isEncrypted());

            if (message.getReplyTo() != null) {
                stmt.setInt(10, message.getReplyTo());
            } else {
                stmt.setNull(10, Types.INTEGER);
            }

            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                // Get the generated message ID
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        message.setMessageId(generatedKeys.getInt(1));
                    }
                }
                dbManager.commitTransaction();
                return true;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error saving message", e);
            try {
                dbManager.rollbackTransaction();
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error rolling back transaction", ex);
            }
        }
        return false;
    }

    /**
     * Get recent messages for a room
     */
    public List<Message> getRecentMessages(int roomId, int limit) {
        List<Message> messages = new ArrayList<>();
        String sql = "SELECT * FROM messages WHERE room_id = ? ORDER BY sent_at DESC LIMIT ?";

        try (PreparedStatement stmt = dbManager.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, roomId);
            stmt.setInt(2, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(0, mapResultSetToMessage(rs)); // Add to beginning to maintain chronological order
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error getting recent messages for room: " + roomId, e);
        }
        return messages;
    }

    /**
     * Get private messages between two users
     */
    public List<Message> getPrivateMessages(int user1Id, int user2Id, int limit) {
        List<Message> messages = new ArrayList<>();
        String sql = "SELECT * FROM messages WHERE " +
                "((sender_id = ? AND recipient_id = ?) OR (sender_id = ? AND recipient_id = ?)) " +
                "ORDER BY sent_at DESC LIMIT ?";

        try (PreparedStatement stmt = dbManager.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, user1Id);
            stmt.setInt(2, user2Id);
            stmt.setInt(3, user2Id);
            stmt.setInt(4, user1Id);
            stmt.setInt(5, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(0, mapResultSetToMessage(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error getting private messages between users: " + user1Id + " and " + user2Id, e);
        }
        return messages;
    }

    /**
     * Get message history for a user
     */
    public List<Message> getUserMessageHistory(int userId, int limit) {
        List<Message> messages = new ArrayList<>();
        String sql = "SELECT * FROM messages WHERE sender_id = ? ORDER BY sent_at DESC LIMIT ?";

        try (PreparedStatement stmt = dbManager.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapResultSetToMessage(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error getting message history for user: " + userId, e);
        }
        return messages;
    }

    /**
     * Search messages by text content
     */
    public List<Message> searchMessages(String searchText, int limit) {
        List<Message> messages = new ArrayList<>();
        String sql = "SELECT * FROM messages WHERE message_text ILIKE ? ORDER BY sent_at DESC LIMIT ?";

        try (PreparedStatement stmt = dbManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, "%" + searchText + "%");
            stmt.setInt(2, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapResultSetToMessage(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error searching messages: " + searchText, e);
        }
        return messages;
    }

    /**
     * Delete a message (soft delete by updating the text)
     */
    public boolean deleteMessage(int messageId, int userId) {
        String sql = "UPDATE messages SET message_text = '[Message deleted]', edited_at = CURRENT_TIMESTAMP " +
                "WHERE message_id = ? AND sender_id = ?";

        try (PreparedStatement stmt = dbManager.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, messageId);
            stmt.setInt(2, userId);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                dbManager.commitTransaction();
                return true;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error deleting message: " + messageId, e);
            try {
                dbManager.rollbackTransaction();
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error rolling back transaction", ex);
            }
        }
        return false;
    }

    /**
     * Map ResultSet to Message object
     */
    private Message mapResultSetToMessage(ResultSet rs) throws SQLException {
        Message message = new Message();
        message.setMessageId(rs.getInt("message_id"));
        message.setSenderId(rs.getInt("sender_id"));
        message.setRoomId(rs.getInt("room_id"));

        int recipientId = rs.getInt("recipient_id");
        if (!rs.wasNull()) {
            message.setRecipientId(recipientId);
        }

        message.setMessageText(rs.getString("message_text"));
        message.setMessageType(rs.getString("message_type"));
        message.setFilePath(rs.getString("file_path"));

        long fileSize = rs.getLong("file_size");
        if (!rs.wasNull()) {
            message.setFileSize(fileSize);
        }

        message.setFileName(rs.getString("file_name"));
        message.setSentAt(rs.getTimestamp("sent_at"));
        message.setEditedAt(rs.getTimestamp("edited_at"));
        message.setEncrypted(rs.getBoolean("is_encrypted"));

        int replyTo = rs.getInt("reply_to");
        if (!rs.wasNull()) {
            message.setReplyTo(replyTo);
        }

        return message;
    }
}
