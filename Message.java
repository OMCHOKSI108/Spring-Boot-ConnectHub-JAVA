import java.sql.Timestamp;

/**
 * Message model class representing a chat message
 */
public class Message {
    private int messageId;
    private int senderId;
    private int roomId;
    private Integer recipientId; // for private messages
    private String messageText;
    private String messageType;
    private String filePath;
    private Long fileSize;
    private String fileName;
    private Timestamp sentAt;
    private Timestamp editedAt;
    private boolean isEncrypted;
    private Integer replyTo;

    // Constructors
    public Message() {
        this.sentAt = new Timestamp(System.currentTimeMillis());
        this.messageType = "text";
        this.isEncrypted = false;
    }

    public Message(int senderId, String messageText) {
        this();
        this.senderId = senderId;
        this.messageText = messageText;
    }

    // Getters and Setters
    public int getMessageId() {
        return messageId;
    }

    public void setMessageId(int messageId) {
        this.messageId = messageId;
    }

    public int getSenderId() {
        return senderId;
    }

    public void setSenderId(int senderId) {
        this.senderId = senderId;
    }

    public int getRoomId() {
        return roomId;
    }

    public void setRoomId(int roomId) {
        this.roomId = roomId;
    }

    public Integer getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(Integer recipientId) {
        this.recipientId = recipientId;
    }

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Timestamp getSentAt() {
        return sentAt;
    }

    public void setSentAt(Timestamp sentAt) {
        this.sentAt = sentAt;
    }

    public Timestamp getEditedAt() {
        return editedAt;
    }

    public void setEditedAt(Timestamp editedAt) {
        this.editedAt = editedAt;
    }

    public boolean isEncrypted() {
        return isEncrypted;
    }

    public void setEncrypted(boolean encrypted) {
        isEncrypted = encrypted;
    }

    public Integer getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(Integer replyTo) {
        this.replyTo = replyTo;
    }

    @Override
    public String toString() {
        return String.format("Message{id=%d, sender=%d, text='%s', type='%s', sentAt=%s}",
                messageId, senderId, messageText, messageType, sentAt);
    }
}
