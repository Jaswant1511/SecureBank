package com.securebank.model;

import java.time.LocalDateTime;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: User.java
 *
 * Represents an authenticated user in the system with an assigned security Role.
 */
public class User {

    private int userId;
    private String username;
    private String passwordHash;
    private Role role;
    private int failedLogins;
    private boolean isLocked;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;

    public User() {
        this.active = true;
        this.isLocked = false;
        this.failedLogins = 0;
        this.createdAt = LocalDateTime.now();
    }

    public User(int userId, String username, String passwordHash, Role role, int failedLogins, boolean isLocked, boolean active) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.failedLogins = failedLogins;
        this.isLocked = isLocked;
        this.active = active;
        this.createdAt = LocalDateTime.now();
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public int getFailedLogins() {
        return failedLogins;
    }

    public void setFailedLogins(int failedLogins) {
        this.failedLogins = failedLogins;
    }

    public boolean isLocked() {
        return isLocked;
    }

    public void setLocked(boolean locked) {
        isLocked = locked;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }
}
