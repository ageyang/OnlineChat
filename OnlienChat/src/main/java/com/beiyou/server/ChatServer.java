package com.beiyou.server;

import com.beiyou.model.SmallGroup;
import com.beiyou.model.User;
import com.beiyou.server.handler.ClientHandler;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {
    private static final int PORT = 12345;
    private static final String USER_FILE_PATH = "src/main/resources/users.txt";

    // 用户名到处理线程的映射
    public static ConcurrentHashMap<String, ClientHandler> userMap = new ConcurrentHashMap<>();
    // 用户名到User对象的映射
    public static ConcurrentHashMap<String, User> userInfoMap = new ConcurrentHashMap<>();
    // 新增：小组名称到小组对象的映射
    public static ConcurrentHashMap<String, com.beiyou.model.SmallGroup> smallGroups = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        loadUsers(); // 启动时加载用户
        loadGroups(); // 启动时加载小组
        System.out.println("服务器启动，监听端口：" + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("新客户端连接：" + clientSocket.getInetAddress());
                ClientHandler handler = new ClientHandler(clientSocket);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 广播用户列表给所有客户端
    public static void broadcastUserList() {
        StringBuilder sb = new StringBuilder("USER_LIST|");
        for (String user : userMap.keySet()) {
            sb.append(user).append(",");
        }
        if (sb.length() > 10 && sb.charAt(sb.length() - 1) == ',') {
            sb.deleteCharAt(sb.length() - 1);
        }
        String userListMsg = sb.toString();
        for (ClientHandler handler : userMap.values()) {
            handler.sendMessage(userListMsg);
        }
    }

    // 新增：从文件加载用户信息
    private static void loadUsers() {
        try {
            File userFile = new File(USER_FILE_PATH);
            if (!userFile.exists()) {
                userFile.getParentFile().mkdirs();
                userFile.createNewFile();
                System.out.println("未找到用户文件，已创建新文件: " + USER_FILE_PATH);
                return;
            }

            List<String> lines = Files.readAllLines(Paths.get(USER_FILE_PATH));
            for (String line : lines) {
                if (line == null || line.trim().isEmpty() || !line.contains(":")) {
                    continue;
                }
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    User user = new User(parts[0], parts[1]);
                    userInfoMap.put(parts[0], user);
                }
            }
            System.out.println("成功加载 " + userInfoMap.size() + " 个用户。");
        } catch (IOException e) {
            System.err.println("加载用户文件时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 新增：从文件加载小组信息
    private static void loadGroups() {
        try {
            Path groupsDir = Paths.get("src/main/resources/groups");
            if (!Files.exists(groupsDir)) {
                Files.createDirectories(groupsDir);
                System.out.println("未找到小组目录，已创建新目录: " + groupsDir);
                return;
            }

            Files.list(groupsDir)
                .filter(path -> path.toString().endsWith(".txt"))
                .forEach(path -> {
                    String groupName = path.getFileName().toString().replace(".txt", "");
                    SmallGroup group = SmallGroup.loadFromFile(groupName);
                    if (group != null) {
                        smallGroups.put(groupName, group);
                        System.out.println("成功加载小组: " + groupName + " (成员: " + group.getMembers().size() + "人)");
                    }
                });
            
            System.out.println("成功加载 " + smallGroups.size() + " 个小组。");
        } catch (IOException e) {
            System.err.println("加载小组文件时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 新增：向指定小组广播消息
    public static void broadcastToSmallGroup(String groupName, String message) {
        SmallGroup group = smallGroups.get(groupName);
        if (group != null) {
            for (String memberName : group.getMembers()) {
                ClientHandler handler = userMap.get(memberName);
                if (handler != null) {
                    handler.sendMessage(message);
                }
            }
        }
    }
}