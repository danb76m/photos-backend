package me.danb76.photos.database.tables;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.util.UUID;

@Entity
public class LoginAttempts {
    @Id
    @GeneratedValue(strategy= GenerationType.AUTO)
    private UUID id;

    private String username;

    private String ip_addr;

    private String user_agent;

    private long timestamp;

    private boolean success;

    public LoginAttempts() {
        this.timestamp = System.currentTimeMillis();
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getIp_addr() {
        return ip_addr;
    }

    public String getUser_agent() {
        return user_agent;
    }

    public boolean isSuccess() {
        return success;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setIp_addr(String ip_addr) {
        this.ip_addr = ip_addr;
    }

    public void setUser_agent(String user_agent) {
        this.user_agent = user_agent;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
