package com.securebank.model;

import java.time.LocalDateTime;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: Customer.java
 *
 * Represents customer profile information associated with a User login.
 */
public class Customer {

    private int customerId;
    private int userId;
    private String fullName;
    private String email;
    private String phone;
    private String address;
    private LocalDateTime createdAt;

    public Customer() {
        this.createdAt = LocalDateTime.now();
    }

    public Customer(int customerId, int userId, String fullName, String email, String phone, String address) {
        this.customerId = customerId;
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.createdAt = LocalDateTime.now();
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
