-- ConnectHub Database Schema
-- PostgreSQL Database Setup

-- Create database (run this command separately in PostgreSQL)
-- CREATE DATABASE connecthub_db;

-- Users table
CREATE TABLE users (
    user_id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE,
    display_name VARCHAR(100),
    avatar_url VARCHAR(255),
    is_online BOOLEAN DEFAULT FALSE,
    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_admin BOOLEAN DEFAULT FALSE,
    status VARCHAR(20) DEFAULT 'active' -- active, banned, suspended
);

-- Chat rooms/channels
CREATE TABLE chat_rooms (
    room_id SERIAL PRIMARY KEY,
    room_name VARCHAR(100) NOT NULL,
    description TEXT,
    is_private BOOLEAN DEFAULT FALSE,
    created_by INTEGER REFERENCES users(user_id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    max_users INTEGER DEFAULT 50
);

-- Room membership
CREATE TABLE room_members (
    member_id SERIAL PRIMARY KEY,
    room_id INTEGER REFERENCES chat_rooms(room_id) ON DELETE CASCADE,
    user_id INTEGER REFERENCES users(user_id) ON DELETE CASCADE,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    role VARCHAR(20) DEFAULT 'member', -- admin, moderator, member
    UNIQUE(room_id, user_id)
);

-- Messages table
CREATE TABLE messages (
    message_id SERIAL PRIMARY KEY,
    sender_id INTEGER REFERENCES users(user_id),
    room_id INTEGER REFERENCES chat_rooms(room_id),
    recipient_id INTEGER REFERENCES users(user_id), -- for private messages
    message_text TEXT NOT NULL,
    message_type VARCHAR(20) DEFAULT 'text', -- text, file, system, image
    file_path VARCHAR(500), -- for file messages
    file_size BIGINT, -- file size in bytes
    file_name VARCHAR(255),
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    edited_at TIMESTAMP,
    is_encrypted BOOLEAN DEFAULT FALSE,
    reply_to INTEGER REFERENCES messages(message_id)
);

-- File transfers table
CREATE TABLE file_transfers (
    transfer_id SERIAL PRIMARY KEY,
    sender_id INTEGER REFERENCES users(user_id),
    recipient_id INTEGER REFERENCES users(user_id),
    file_name VARCHAR(255) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    file_type VARCHAR(100),
    transfer_status VARCHAR(20) DEFAULT 'pending', -- pending, in_progress, completed, failed, cancelled
    progress_percentage INTEGER DEFAULT 0,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    download_count INTEGER DEFAULT 0,
    expiry_date TIMESTAMP
);

-- User sessions for tracking active connections
CREATE TABLE user_sessions (
    session_id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(user_id),
    session_token VARCHAR(255) UNIQUE NOT NULL,
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_activity TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE
);

-- Message read status
CREATE TABLE message_read_status (
    read_id SERIAL PRIMARY KEY,
    message_id INTEGER REFERENCES messages(message_id) ON DELETE CASCADE,
    user_id INTEGER REFERENCES users(user_id) ON DELETE CASCADE,
    read_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(message_id, user_id)
);

-- Server logs and analytics
CREATE TABLE server_logs (
    log_id SERIAL PRIMARY KEY,
    log_level VARCHAR(20) NOT NULL, -- INFO, WARN, ERROR, DEBUG
    log_message TEXT NOT NULL,
    user_id INTEGER REFERENCES users(user_id),
    ip_address INET,
    action VARCHAR(100), -- login, logout, file_upload, message_send, etc.
    details JSONB, -- additional data in JSON format
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- User preferences and settings
CREATE TABLE user_preferences (
    pref_id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(user_id) ON DELETE CASCADE,
    theme VARCHAR(20) DEFAULT 'light', -- light, dark
    notifications_enabled BOOLEAN DEFAULT TRUE,
    sound_enabled BOOLEAN DEFAULT TRUE,
    auto_download_files BOOLEAN DEFAULT FALSE,
    max_file_size_mb INTEGER DEFAULT 100,
    language VARCHAR(10) DEFAULT 'en',
    timezone VARCHAR(50) DEFAULT 'UTC',
    UNIQUE(user_id)
);

-- Blocked users
CREATE TABLE blocked_users (
    block_id SERIAL PRIMARY KEY,
    blocker_id INTEGER REFERENCES users(user_id) ON DELETE CASCADE,
    blocked_id INTEGER REFERENCES users(user_id) ON DELETE CASCADE,
    blocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reason TEXT,
    UNIQUE(blocker_id, blocked_id)
);

-- Create indexes for better performance
CREATE INDEX idx_messages_sender_room ON messages(sender_id, room_id);
CREATE INDEX idx_messages_sent_at ON messages(sent_at);
CREATE INDEX idx_file_transfers_status ON file_transfers(transfer_status);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_online ON users(is_online);
CREATE INDEX idx_user_sessions_active ON user_sessions(is_active);
CREATE INDEX idx_server_logs_created_at ON server_logs(created_at);

-- Insert default data
INSERT INTO users (username, password_hash, display_name, is_admin) 
VALUES ('admin', 'admin123hash', 'System Administrator', TRUE);

INSERT INTO chat_rooms (room_name, description, created_by) 
VALUES ('General', 'Main chat room for everyone', 1);

INSERT INTO room_members (room_id, user_id, role) 
VALUES (1, 1, 'admin');

-- Create views for common queries
CREATE VIEW active_users AS
SELECT user_id, username, display_name, last_seen
FROM users 
WHERE is_online = TRUE AND status = 'active';

CREATE VIEW recent_messages AS
SELECT m.message_id, u.username, m.message_text, m.sent_at, cr.room_name
FROM messages m
JOIN users u ON m.sender_id = u.user_id
LEFT JOIN chat_rooms cr ON m.room_id = cr.room_id
ORDER BY m.sent_at DESC
LIMIT 100;
