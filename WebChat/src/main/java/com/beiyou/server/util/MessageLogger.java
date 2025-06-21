package com.beiyou.server.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;

public class MessageLogger {

    private static final Path LOG_DIR = Paths.get("src/main/resources/logs");
    private static final Path GROUP_CHAT_DIR = Paths.get("src/main/resources/groups");

    static {
        try {
            if (!Files.exists(LOG_DIR)) {
                Files.createDirectories(LOG_DIR);
            }
            if (!Files.exists(GROUP_CHAT_DIR)) {
                Files.createDirectories(GROUP_CHAT_DIR);
            }
        } catch (IOException e) {
            System.err.println("创建日志目录失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void logMessage(String username, String message) {
        if (username == null || username.trim().isEmpty()) {
            return;
        }
        try {
            Path userLogFile = LOG_DIR.resolve(username + ".txt");
            String logEntry = message + System.lineSeparator();
            Files.write(userLogFile, logEntry.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("为用户 " + username + " 记录消息失败: " + e.getMessage());
        }
    }

    public static List<String> readUserHistory(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            Path userLogFile = LOG_DIR.resolve(username + ".txt");
            if (Files.exists(userLogFile)) {
                return Files.readAllLines(userLogFile, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            System.err.println("读取用户 " + username + " 历史记录失败: " + e.getMessage());
        }
        return Collections.emptyList();
    }
    
    // 新增：记录小组聊天消息
    public static void logGroupChat(String groupName, String message) {
        if (groupName == null || groupName.trim().isEmpty()) {
            return;
        }
        try {
            Path groupChatFile = GROUP_CHAT_DIR.resolve(groupName + "_chat.txt");
            String logEntry = message + System.lineSeparator();
            Files.write(groupChatFile, logEntry.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("为小组 " + groupName + " 记录消息失败: " + e.getMessage());
        }
    }
    
    // 新增：读取小组聊天历史
    public static List<String> readGroupChatHistory(String groupName) {
        if (groupName == null || groupName.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            Path groupChatFile = GROUP_CHAT_DIR.resolve(groupName + "_chat.txt");
            if (Files.exists(groupChatFile)) {
                return Files.readAllLines(groupChatFile, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            System.err.println("读取小组 " + groupName + " 聊天记录失败: " + e.getMessage());
        }
        return Collections.emptyList();
    }
}