package com.beiyou.model;

import java.util.UUID;

public class User {
    private String username;
    private String userId;
    private String password;

    public User(String username, String password) {
        this.username = username;
        this.password = password;
        this.userId = UUID.randomUUID().toString();
    }

    public String getUsername() {
        return username;
    }

    public String getUserId() {
        return userId;
    }

    public String getPassword() {
        return password;
    }
}
