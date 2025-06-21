package com.beiyou.model;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SmallGroup {
    private String groupName;
    private String owner; // 小组创建者
    private Set<String> members; // 小组成员的用户名
    private static final Path GROUPS_DIR = Paths.get("src/main/resources/groups");

    static {
        try {
            if (!Files.exists(GROUPS_DIR)) {
                Files.createDirectories(GROUPS_DIR);
            }
        } catch (IOException e) {
            System.err.println("创建小组目录失败: " + e.getMessage());
        }
    }

    public SmallGroup(String groupName, String owner) {
        this.groupName = groupName;
        this.owner = owner;
        this.members = ConcurrentHashMap.newKeySet();
        this.members.add(owner); // 创建者自动加入
    }

    public String getGroupName() {
        return groupName;
    }

    public String getOwner() {
        return owner;
    }

    public Set<String> getMembers() {
        return members;
    }

    public void addMember(String username) {
        members.add(username);
        saveToFile(); // 保存到文件
    }

    public void removeMember(String username) {
        members.remove(username);
        saveToFile(); // 保存到文件
    }

    public boolean isMember(String username) {
        return members.contains(username);
    }
    
    // 新增：保存小组信息到文件
    private void saveToFile() {
        try {
            Path groupFile = GROUPS_DIR.resolve(groupName + ".txt");
            StringBuilder content = new StringBuilder();
            content.append("owner:").append(owner).append("\n");
            for (String member : members) {
                content.append("member:").append(member).append("\n");
            }
            Files.write(groupFile, content.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("保存小组 " + groupName + " 信息失败: " + e.getMessage());
        }
    }
    
    // 新增：从文件加载小组信息
    public static SmallGroup loadFromFile(String groupName) {
        try {
            Path groupFile = GROUPS_DIR.resolve(groupName + ".txt");
            if (!Files.exists(groupFile)) {
                return null;
            }
            
            String owner = null;
            Set<String> members = ConcurrentHashMap.newKeySet();
            
            for (String line : Files.readAllLines(groupFile, StandardCharsets.UTF_8)) {
                if (line.startsWith("owner:")) {
                    owner = line.substring(6);
                } else if (line.startsWith("member:")) {
                    members.add(line.substring(7));
                }
            }
            
            if (owner != null && !members.isEmpty()) {
                SmallGroup group = new SmallGroup(groupName, owner);
                group.members.clear();
                group.members.addAll(members);
                return group;
            }
        } catch (IOException e) {
            System.err.println("加载小组 " + groupName + " 信息失败: " + e.getMessage());
        }
        return null;
    }
    
    // 新增：删除小组文件
    public void deleteFile() {
        try {
            Path groupFile = GROUPS_DIR.resolve(groupName + ".txt");
            Files.deleteIfExists(groupFile);
        } catch (IOException e) {
            System.err.println("删除小组 " + groupName + " 文件失败: " + e.getMessage());
        }
    }

    // 新增：删除小组聊天记录文件
    public void deleteChatHistory() {
        try {
            Path groupChatFile = Paths.get("src/main/resources/groups").resolve(groupName + "_chat.txt");
            Files.deleteIfExists(groupChatFile);
        } catch (IOException e) {
            System.err.println("删除小组 " + groupName + " 聊天记录失败: " + e.getMessage());
        }
    }
}
