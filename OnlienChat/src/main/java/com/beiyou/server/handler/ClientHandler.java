package com.beiyou.server.handler;


import com.beiyou.model.SmallGroup;
import com.beiyou.model.User;
import com.beiyou.server.ChatServer;
import com.beiyou.server.util.MessageLogger;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;
    private User user;
    private volatile boolean running = true;
    private String currentSmallGroupName; // 新增：当前所在的小组名

    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        try {
            String msg;
            while (running && (msg = in.readLine()) != null) {
                // 如果当前连接已被踢下线（running=false），不再处理消息
                if (!running) break;

                // 登录消息处理
                if (username == null && msg.startsWith("LOGIN|")) {
                    String[] parts = msg.split("\\|", 3);
                    if (parts.length < 3) {
                        sendMessage("LOGIN_FAIL|格式错误，应为LOGIN|用户名|密码");
                        continue;
                    }
                    String uname = parts[1];
                    String pwd = parts[2];
                    User existUser = ChatServer.userInfoMap.get(uname);
                    if (existUser == null) {
                        // 注册新用户
                        user = new User(uname, pwd);
                        ChatServer.userInfoMap.put(uname, user);
                        // 将新用户信息追加到文件
                        try {
                            String userInfoLine = uname + ":" + pwd + System.lineSeparator();
                            Files.write(Paths.get("src/main/resources/users.txt"), userInfoLine.getBytes("UTF-8"), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                        } catch (IOException e) {
                            System.err.println("保存新用户信息失败: " + e.getMessage());
                            e.printStackTrace();
                        }
                    } else {
                        if (!existUser.getPassword().equals(pwd)) {
                            sendMessage("LOGIN_FAIL|密码错误");
                            continue;
                        }
                        user = existUser;
                        // 校验密码通过后再踢掉原连接
                        if (ChatServer.userMap.containsKey(uname)) {
                            ClientHandler oldHandler = ChatServer.userMap.get(uname);
                            oldHandler.sendMessage("KICK_OFF|您的账号已在其他地方登录，您已被踢下线。");
                            oldHandler.forceClose(); // 强制关闭原连接
                            ChatServer.userMap.remove(uname); // 立即移除
                        }
                    }
                    this.username = uname;
                    ChatServer.userMap.put(uname, this);
                    sendMessage("LOGIN_SUCCESS");
                    sendHistory(); // 登录成功后发送历史记录
                    
                    // 新增：恢复用户的小组状态
                    restoreGroupMembership();
                    
                    ChatServer.broadcastUserList();
                    continue;
                }

                // 新增：创建小组功能 /creategroup <groupName> <user1> <user2>...
                if (msg.startsWith("/creategroup ")) {
                    handleCreateGroup(msg);
                    continue;
                }

                // 新增：接受小组邀请 /accept <groupName>
                if (msg.startsWith("/accept ")) {
                    handleAcceptInvitation(msg);
                    continue;
                }

                // 新增：发送小组消息 /g <message>
                if (msg.startsWith("/g ")) {
                    handleGroupMessage(msg);
                    continue;
                }

                // 新增：离开小组 /leave
                if (msg.equals("/leave")) {
                    handleLeaveGroup();
                    continue;
                }

                // 新增: 小组成员邀请他人
                if (msg.startsWith("/invite ")) {
                    handleInviteToGroup(msg);
                    continue;
                }

                // 新增: 帮助命令
                if (msg.equals("/help")) {
                    handleHelpCommand();
                    continue;
                }

                // 新增: 群聊历史命令
                if (msg.startsWith("/grouphistory ")) {
                    String[] parts = msg.split(" ", 2);
                    if (parts.length == 2) {
                        String groupName = parts[1];
                        // 新增：使用专门的小组聊天记录读取方法
                        java.util.List<String> history = com.beiyou.server.util.MessageLogger.readGroupChatHistory(groupName);
                        for (String line : history) {
                            sendMessage(line);
                        }
                        sendMessage("----------- 记录加载完毕 -----------");
                    }
                    continue;
                }

                // 群聊文件上传命令（不带groupName）
                if (msg.startsWith("/upload ") && msg.split(" ").length == 3) {
                    String[] parts = msg.split(" ", 3);
                    String filename = parts[1];
                    long filesize = Long.parseLong(parts[2]);
                    try {
                        com.beiyou.server.util.GroupFileManager.saveFile("global", filename, socket.getInputStream(), filesize);
                        sendMessage("SERVER: 群聊文件 " + filename + " 上传成功！");
                        // 广播给所有在线用户
                        for (ClientHandler handler : com.beiyou.server.ChatServer.userMap.values()) {
                            handler.sendMessage("[群聊][SERVER]: 用户 " + username + " 上传了文件 " + filename);
                        }
                    } catch (Exception e) {
                        sendMessage("SERVER: 文件上传失败: " + e.getMessage());
                    }
                    continue;
                }

                // 群聊文件下载命令（单独Socket）
                if (msg.startsWith("/download ") && msg.split(" ").length == 2) {
                    String[] parts = msg.split(" ", 2);
                    String filename = parts[1];
                    new Thread(() -> {
                        try (java.net.ServerSocket fileServer = new java.net.ServerSocket(0)) { // 0表示自动分配端口
                            int port = fileServer.getLocalPort();
                            sendMessage("FILEPORT " + port); // 通知客户端端口
                            java.net.Socket fileSocket = fileServer.accept();
                            java.io.File file = new java.io.File("src/main/resources/group_files/global/" + filename);
                            long filesize = file.length();
                            java.io.OutputStream out = fileSocket.getOutputStream();
                            out.write(longToBytes(filesize)); // 先发8字节文件大小
                            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                                byte[] buffer = new byte[4096];
                                int read;
                                while ((read = fis.read(buffer)) != -1) {
                                    out.write(buffer, 0, read);
                                }
                            }
                            out.flush();
                            fileSocket.close();
                        } catch (Exception e) {
                            sendMessage("SERVER: 文件下载失败: " + e.getMessage());
                        }
                    }).start();
                    continue;
                }

                // 群聊文件列表命令
                if (msg.equals("/filelist")) {
                    String[] files = com.beiyou.server.util.GroupFileManager.listFiles("global");
                    if (files.length > 0) {
                        sendMessage("FILELIST|" + String.join(",", files));
                    } else {
                        sendMessage("FILELIST|");
                    }
                    continue;
                }

                // 小组文件上传命令（带groupName）
                if (msg.startsWith("/upload ") && msg.split(" ").length == 4) {
                    String[] parts = msg.split(" ", 4);
                    String groupName = parts[1];
                    String filename = parts[2];
                    long filesize = Long.parseLong(parts[3]);
                    try {
                        com.beiyou.server.util.GroupFileManager.saveFile(groupName, filename, socket.getInputStream(), filesize);
                        sendMessage("SERVER: 小组文件 " + filename + " 上传成功！");
                        // 广播给小组成员
                        com.beiyou.model.SmallGroup group = com.beiyou.server.ChatServer.smallGroups.get(groupName);
                        if (group != null) {
                            for (String member : group.getMembers()) {
                                ClientHandler handler = com.beiyou.server.ChatServer.userMap.get(member);
                                if (handler != null) {
                                    handler.sendMessage("[小组:" + groupName + "][SERVER]: 用户 " + username + " 上传了文件 " + filename);
                                    // 新增：在小组内广播"用户名上传了文件名"
                                    handler.sendMessage("[小组:" + groupName + "][" + username + "]: " + username + " 上传了 " + filename);
                                }
                            }
                        }
                    } catch (Exception e) {
                        sendMessage("SERVER: 文件上传失败: " + e.getMessage());
                    }
                    continue;
                }

                // 小组文件下载命令（单独Socket）
                if (msg.startsWith("/download ") && msg.split(" ").length == 3) {
                    String[] parts = msg.split(" ", 3);
                    String groupName = parts[1];
                    String filename = parts[2];
                    new Thread(() -> {
                        try (java.net.ServerSocket fileServer = new java.net.ServerSocket(0)) {
                            int port = fileServer.getLocalPort();
                            sendMessage("FILEPORT " + port);
                            java.net.Socket fileSocket = fileServer.accept();
                            java.io.File file = new java.io.File("src/main/resources/group_files/" + groupName + "/" + filename);
                            long filesize = file.length();
                            java.io.OutputStream out = fileSocket.getOutputStream();
                            out.write(longToBytes(filesize));
                            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                                byte[] buffer = new byte[4096];
                                int read;
                                while ((read = fis.read(buffer)) != -1) {
                                    out.write(buffer, 0, read);
                                }
                            }
                            out.flush();
                            fileSocket.close();
                        } catch (Exception e) {
                            sendMessage("SERVER: 文件下载失败: " + e.getMessage());
                        }
                    }).start();
                    continue;
                }

                // 小组文件列表命令
                if (msg.startsWith("/filelist ")) {
                    String[] parts = msg.split(" ", 2);
                    String groupName = parts[1];
                    String[] files = com.beiyou.server.util.GroupFileManager.listFiles(groupName);
                    if (files.length > 0) {
                        sendMessage("FILELIST|" + String.join(",", files));
                    } else {
                        sendMessage("FILELIST|");
                    }
                    continue;
                }

                // 私聊消息处理：/w 目标用户名 消息内容
                if (username != null && msg != null && msg.startsWith("/w ")) {
                    String[] parts = msg.split(" ", 3);
                    if (parts.length >= 3) {
                        String targetUser = parts[1];
                        String privateContent = parts[2];
                        // 修复：发送给目标用户的消息应该是 [私聊][发送方->接收方]: 内容
                        String privateMsgForTarget = "[私聊][" + username + "->" + targetUser + "]: " + privateContent;
                        System.out.println(privateMsgForTarget);
                        String privateMsgForSender = "[私聊][" + username + "->" + targetUser + "]: " + privateContent;
                        System.out.println(privateMsgForSender);
                        ClientHandler targetHandler = ChatServer.userMap.get(targetUser);
                        if (targetHandler != null) {
                            targetHandler.sendMessage(privateMsgForTarget);
                            MessageLogger.logMessage(targetUser, privateMsgForTarget);
                        } else {
                            this.sendMessage("用户 " + targetUser + " 不在线。");
                        }
                        this.sendMessage(privateMsgForSender);
                        MessageLogger.logMessage(this.username, privateMsgForSender);
                    } else {
                        this.sendMessage("私聊格式错误，应为：/w 用户名 消息内容");
                    }
                    continue;
                }

                // 私聊文件相关命令提前continue，防止被群发
                if (msg.startsWith("/uploadfile ") && msg.split(" ").length == 4) {
                    String[] parts = msg.split(" ", 4);
                    String targetUser = parts[1];
                    String filename = parts[2];
                    long filesize = Long.parseLong(parts[3]);
                    String userA = username.compareTo(targetUser) < 0 ? username : targetUser;
                    String userB = username.compareTo(targetUser) < 0 ? targetUser : username;
                    try {
                        com.beiyou.server.util.PrivateFileManager.saveFile(userA, userB, filename, socket.getInputStream(), filesize);
                        sendMessage("SERVER: 私聊文件 " + filename + " 上传成功！");
                        // 只通知目标用户（不群发）
                        ClientHandler targetHandler = com.beiyou.server.ChatServer.userMap.get(targetUser);
                        if (targetHandler != null) {
                            targetHandler.sendMessage("[私聊][SERVER]: 用户 " + username + " 发送了文件 " + filename);
                            // 新增：发一条私聊文本消息
                            String notifyMsg = username + " 上传了 " + filename;
                            targetHandler.sendMessage("[私聊][" + username + "->" + targetUser + "]: " + notifyMsg);
                            MessageLogger.logMessage(targetUser, "[私聊][" + username + "->" + targetUser + "]: " + notifyMsg);
                        }
                        // 自己也收到一条私聊文本消息
                        String notifyMsgSelf = username + " 上传了 " + filename;
                        this.sendMessage("[私聊][" + username + "->" + targetUser + "]: " + notifyMsgSelf);
                        MessageLogger.logMessage(this.username, "[私聊][" + username + "->" + targetUser + "]: " + notifyMsgSelf);
                    } catch (Exception e) {
                        sendMessage("SERVER: 文件上传失败: " + e.getMessage());
                    }
                    continue;
                }
                if (msg.startsWith("/downloadfile ") && msg.split(" ").length == 3) {
                    String[] parts = msg.split(" ", 3);
                    String targetUser = parts[1];
                    String filename = parts[2];
                    String userA = username.compareTo(targetUser) < 0 ? username : targetUser;
                    String userB = username.compareTo(targetUser) < 0 ? targetUser : username;
                    new Thread(() -> {
                        try (java.net.ServerSocket fileServer = new java.net.ServerSocket(0)) {
                            int port = fileServer.getLocalPort();
                            sendMessage("FILEPORT " + port);
                            java.net.Socket fileSocket = fileServer.accept();
                            java.io.File file = com.beiyou.server.util.PrivateFileManager.getFile(userA, userB, filename);
                            long filesize = file.length();
                            java.io.OutputStream out = fileSocket.getOutputStream();
                            out.write(longToBytes(filesize));
                            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                                byte[] buffer = new byte[4096];
                                int read;
                                while ((read = fis.read(buffer)) != -1) {
                                    out.write(buffer, 0, read);
                                }
                            }
                            out.flush();
                            fileSocket.close();
                        } catch (Exception e) {
                            sendMessage("SERVER: 文件下载失败: " + e.getMessage());
                        }
                    }).start();
                    continue;
                }
                if (msg.startsWith("/filelistfile ") && msg.split(" ").length == 2) {
                    String[] parts = msg.split(" ", 2);
                    String targetUser = parts[1];
                    String userA = username.compareTo(targetUser) < 0 ? username : targetUser;
                    String userB = username.compareTo(targetUser) < 0 ? targetUser : username;
                    String[] files = com.beiyou.server.util.PrivateFileManager.listFiles(userA, userB);
                    if (files.length > 0) {
                        sendMessage("FILELIST|" + String.join(",", files));
                    } else {
                        sendMessage("FILELIST|");
                    }
                    continue;
                }

                // 图片发送命令：/imgsend <target> <filename> <filesize> [type]
                if (msg.startsWith("/imgsend ")) {
                    String[] parts = msg.split(" ", 5);
                    if (parts.length < 4) {
                        sendMessage("SERVER: 图片发送命令格式错误");
                        continue;
                    }
                    String target = parts[1];
                    String filename = parts[2];
                    long filesize = Long.parseLong(parts[3]);
                    String type = parts.length == 5 ? parts[4] : "group"; // group/smallgroup/private
                    try {
                        if ("group".equals(type)) {
                            // 群聊图片
                            java.io.File dir = new java.io.File("src/main/resources/images/global");
                            if (!dir.exists()) dir.mkdirs();
                            java.io.File file = new java.io.File(dir, filename);
                            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                                byte[] buffer = new byte[4096];
                                long remaining = filesize;
                                while (remaining > 0) {
                                    int read = socket.getInputStream().read(buffer, 0, (int)Math.min(buffer.length, remaining));
                                    if (read == -1) break;
                                    fos.write(buffer, 0, read);
                                    remaining -= read;
                                }
                            }
                            // 修正前缀格式，去掉多余的[
                            String imgMsg = "[群聊][" + username + "]: [图片]:" + filename;
                            for (ClientHandler handler : com.beiyou.server.ChatServer.userMap.values()) {
                                handler.sendMessage(imgMsg);
                            }
                            for (String onlineUser : com.beiyou.server.ChatServer.userMap.keySet()) {
                                com.beiyou.server.util.MessageLogger.logMessage(onlineUser, imgMsg);
                            }
                        } else if ("smallgroup".equals(type)) {
                            // 小组图片
                            java.io.File dir = new java.io.File("src/main/resources/images/" + target);
                            if (!dir.exists()) dir.mkdirs();
                            java.io.File file = new java.io.File(dir, filename);
                            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                                byte[] buffer = new byte[4096];
                                long remaining = filesize;
                                while (remaining > 0) {
                                    int read = socket.getInputStream().read(buffer, 0, (int)Math.min(buffer.length, remaining));
                                    if (read == -1) break;
                                    fos.write(buffer, 0, read);
                                    remaining -= read;
                                }
                            }
                            // 修正前缀格式，去掉多余的[
                            String imgMsg = "[小组:" + target + "][" + username + "]: [图片]:" + filename + "@" + target;
                            com.beiyou.model.SmallGroup group = com.beiyou.server.ChatServer.smallGroups.get(target);
                            if (group != null) {
                                for (String member : group.getMembers()) {
                                    ClientHandler handler = com.beiyou.server.ChatServer.userMap.get(member);
                                    if (handler != null) {
                                        handler.sendMessage(imgMsg);
                                    }
                                }
                            }
                            // 只写入小组聊天记录
                            com.beiyou.server.util.MessageLogger.logGroupChat(target, imgMsg);
                        } else if ("private".equals(type)) {
                            // 私聊图片
                            String userA = username.compareTo(target) < 0 ? username : target;
                            String userB = username.compareTo(target) < 0 ? target : username;
                            java.io.File dir = new java.io.File("src/main/resources/images/" + userA + "_" + userB);
                            if (!dir.exists()) dir.mkdirs();
                            java.io.File file = new java.io.File(dir, filename);
                            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                                byte[] buffer = new byte[4096];
                                long remaining = filesize;
                                while (remaining > 0) {
                                    int read = socket.getInputStream().read(buffer, 0, (int)Math.min(buffer.length, remaining));
                                    if (read == -1) break;
                                    fos.write(buffer, 0, read);
                                    remaining -= read;
                                }
                            }
                            // 修正前缀格式，去掉多余的[
                            String imgMsg = "[私聊][" + username + "->" + target + "]: [图片]:" + filename + "@" + userA + "_" + userB;
                            ClientHandler targetHandler = com.beiyou.server.ChatServer.userMap.get(target);
                            if (targetHandler != null) {
                                targetHandler.sendMessage(imgMsg);
                            }
                            this.sendMessage(imgMsg);
                            com.beiyou.server.util.MessageLogger.logMessage(username, imgMsg);
                            com.beiyou.server.util.MessageLogger.logMessage(target, imgMsg);
                        }
                        sendMessage("SERVER: 图片 " + filename + " 发送成功！");
                    } catch (Exception e) {
                        sendMessage("SERVER: 图片发送失败: " + e.getMessage());
                    }
                    continue;
                }

                // 群聊消息处理
                if (username != null && !msg.isEmpty()) {
                    String groupMsg = "[群聊][" + username + "]: " + msg;
                    // 只给所有在线用户记录日志
                    for (String onlineUser : ChatServer.userMap.keySet()) {
                        MessageLogger.logMessage(onlineUser, groupMsg);
                    }
                    // 给所有在线用户发送实时消息
                    for (ClientHandler handler : ChatServer.userMap.values()) {
                        handler.sendMessage(groupMsg);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("客户端断开连接：" + socket.getInetAddress());
        } finally {
            close();
        }
    }

    private void handleCreateGroup(String msg) {
        String[] parts = msg.split(" ");
        if (parts.length < 2) {
            sendMessage("SERVER: 创建小组格式错误，应为 /creategroup <小组名> [邀请成员名...]");
            return;
        }
        if (currentSmallGroupName != null) {
            sendMessage("SERVER: 您已经在一个小组中，请先使用 /leave 离开当前小组。");
            return;
        }
        String groupName = parts[1];
        if (ChatServer.smallGroups.containsKey(groupName)) {
            sendMessage("SERVER: 小组 " + groupName + " 已存在。");
            return;
        }

        SmallGroup newGroup = new SmallGroup(groupName, this.username);
        ChatServer.smallGroups.put(groupName, newGroup);
        this.currentSmallGroupName = groupName;
        
        // 发送加入群聊通知
        this.sendMessage("GROUP_JOINED|" + groupName);
        
        sendMessage("SERVER: 您已成功创建并加入小组 " + groupName);
        
        // 新增：记录小组创建消息到聊天记录
        String createMsg = "[小组:" + groupName + "][SERVER]: 小组 " + groupName + " 已创建，创建者: " + this.username;
        MessageLogger.logGroupChat(groupName, createMsg);

        // 发送邀请
        if (parts.length > 2) {
            for (int i = 2; i < parts.length; i++) {
                String targetUsername = parts[i];
                if (targetUsername.equals(this.username)) {
                    sendMessage("SERVER: 您已经是小组成员，无需邀请自己。");
                    continue;
                }
                ClientHandler targetHandler = ChatServer.userMap.get(targetUsername);
                if (targetHandler != null) {
                    targetHandler.sendMessage("INVITE|" + groupName + "|" + this.username);
                } else {
                    sendMessage("SERVER: 用户 " + targetUsername + " 不在线，无法邀请。");
                }
            }
        }
    }

    private void handleAcceptInvitation(String msg) {
        String[] parts = msg.split(" ", 2);
        if (parts.length < 2) {
            sendMessage("SERVER: 接受邀请格式错误，应为 /accept <小组名>");
            return;
        }
        if (currentSmallGroupName != null) {
            sendMessage("SERVER: 您已经在一个小组中，请先使用 /leave 离开。");
            return;
        }
        String groupName = parts[1];
        SmallGroup group = ChatServer.smallGroups.get(groupName);
        if (group == null) {
            sendMessage("SERVER: 小组 " + groupName + " 不存在或已解散。");
            return;
        }

        group.addMember(this.username);
        this.currentSmallGroupName = groupName;
        
        // 发送加入群聊通知
        this.sendMessage("GROUP_JOINED|" + groupName);
        
        String notification = "SERVER: " + this.username + " 已加入小组。";
        ChatServer.broadcastToSmallGroup(groupName, notification);
        
        // 新增：记录加入小组消息到聊天记录
        String joinMsg = "[小组:" + groupName + "][SERVER]: " + this.username + " 已加入小组";
        MessageLogger.logGroupChat(groupName, joinMsg);
    }

    private void handleGroupMessage(String msg) {
        if (currentSmallGroupName == null) {
            sendMessage("SERVER: 您不属于任何小组，无法发送消息。请先创建或加入一个小组。");
            return;
        }
        String content = msg.substring(3);
        String groupMsg = "[小组:" + currentSmallGroupName + "][" + username + "]: " + content;
        SmallGroup group = ChatServer.smallGroups.get(currentSmallGroupName);
        // 只记录在线成员日志
        for (String member : group.getMembers()) {
            if (ChatServer.userMap.containsKey(member)) {
                MessageLogger.logMessage(member, groupMsg);
            }
        }
        // 新增：写入小组聊天记录
        MessageLogger.logGroupChat(currentSmallGroupName, groupMsg);
        
        // 实时发送
        ChatServer.broadcastToSmallGroup(currentSmallGroupName, groupMsg);
    }

    private void handleLeaveGroup() {
        if (currentSmallGroupName == null) {
            sendMessage("SERVER: 您当前不在任何小组中。");
            return;
        }

        String groupName = currentSmallGroupName;
        SmallGroup group = ChatServer.smallGroups.get(currentSmallGroupName);
        if (group != null) {
            group.removeMember(this.username);
            String notification = "SERVER: " + username + " 已离开小组。";
            ChatServer.broadcastToSmallGroup(currentSmallGroupName, notification);
            
            // 新增：记录离开小组消息到聊天记录
            String leaveMsg = "[小组:" + currentSmallGroupName + "][SERVER]: " + username + " 已离开小组";
            MessageLogger.logGroupChat(currentSmallGroupName, leaveMsg);

            // 如果小组空了，或者创建者离开，则解散小组
            if (group.getMembers().isEmpty() || group.getOwner().equals(this.username)) {
                ChatServer.broadcastToSmallGroup(currentSmallGroupName, "SERVER: 小组创建者已离开，小组 '" + currentSmallGroupName + "' 已解散。");
                ChatServer.smallGroups.remove(currentSmallGroupName);
                // 删除小组文件
                group.deleteFile();
                // 删除小组聊天记录
                group.deleteChatHistory();
                // 新增：记录小组解散消息到聊天记录
               // String disbandMsg = "[小组:" + currentSmallGroupName + "][SERVER]: 小组 '" + currentSmallGroupName + "' 已解散";
              //  MessageLogger.logGroupChat(currentSmallGroupName, disbandMsg);
                // 通知所有成员他们已不在小组内
                for (String memberName : group.getMembers()) {
                    ClientHandler handler = ChatServer.userMap.get(memberName);
                    if (handler != null) {
                        handler.currentSmallGroupName = null;
                        handler.sendMessage("GROUP_LEFT|" + currentSmallGroupName);
                    }
                }
            }
        }

        sendMessage("SERVER: 您已离开小组 " + currentSmallGroupName);
        sendMessage("GROUP_LEFT|" + currentSmallGroupName);
        this.currentSmallGroupName = null;
    }

    private void handleInviteToGroup(String msg) {
        String[] parts = msg.split(" ", 2);
        if (parts.length < 2) {
            sendMessage("SERVER: 邀请格式错误，应为 /invite <用户名>");
            return;
        }
        if (this.currentSmallGroupName == null) {
            sendMessage("SERVER: 您必须先加入一个小组才能邀请他人。");
            return;
        }

        String targetUsername = parts[1];
        if (targetUsername.equals(this.username)) {
            sendMessage("SERVER: 您不能邀请自己。");
            return;
        }

        SmallGroup group = ChatServer.smallGroups.get(this.currentSmallGroupName);
        if (group.isMember(targetUsername)) {
            sendMessage("SERVER: 用户 " + targetUsername + " 已经是小组成员。");
            return;
        }

        ClientHandler targetHandler = ChatServer.userMap.get(targetUsername);
        if (targetHandler == null) {
            sendMessage("SERVER: 用户 " + targetUsername + " 不在线或不存在。");
            return;
        }

        if (targetHandler.getCurrentSmallGroupName() != null) {
            sendMessage("SERVER: 用户 " + targetUsername + " 已在另一个小组中。");
            return;
        }

        targetHandler.sendMessage("INVITE|" + this.currentSmallGroupName + "|" + this.username);
        sendMessage("SERVER: 已向 " + targetUsername + " 发送邀请。");
        ChatServer.broadcastToSmallGroup(this.currentSmallGroupName, "SERVER: " + this.username + " 邀请了 " + targetUsername + " 加入小组。");
    }

    private void handleHelpCommand() {
        sendMessage("SERVER: 帮助命令：");
        sendMessage("/help - 显示帮助信息");
        sendMessage("/creategroup <groupName> <user1> <user2>... - 创建一个新的小组并邀请成员加入");
        sendMessage("/accept <groupName> - 接受邀请加入小组");
        sendMessage("/g <message> - 向小组发送消息");
        sendMessage("/leave - 离开当前小组");
        sendMessage("/invite <username> - 邀请其他用户加入小组");
        sendMessage("/w <username> <message> - 向指定用户发送私聊消息");
    }

    private void sendHistory() {
        // List<String> history = MessageLogger.readUserHistory(this.username);
        // if (!history.isEmpty()) {
        //     sendMessage("----------- 历史消息 -----------");
        //     for (String line : history) {
        //         // 只发送群聊历史，小组历史由/grouphistory命令单独处理
        //         if (line.contains("[群聊]")) {
        //             sendMessage(line);
        //         }
        //     }
        // }
        sendMessage("----------- 记录加载完毕 -----------");
    }

    public void sendMessage(String msg) {
        out.println(msg);
    }

    public void close() {
        // 移除下线时离开小组的逻辑，保持小组状态
        running = false;
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 只移除当前对象本身
        if (username != null && ChatServer.userMap.get(username) == this) {
            ChatServer.userMap.remove(username);
            ChatServer.broadcastUserList();
        }
    }

    // 新增：强制关闭Socket并中断阻塞
    public void forceClose() {
        // 移除被踢下线时离开小组的逻辑，保持小组状态
        running = false;
        try {
            if (socket != null && !socket.isClosed()) socket.close();
            if (in != null) in.close();
            if (out != null) out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getUsername() {
        return this.username;
    }

    public String getCurrentSmallGroupName() {
        return this.currentSmallGroupName;
    }

    // 新增：恢复用户的小组状态
    private void restoreGroupMembership() {
        // 查找用户所在的小组
        for (String groupName : ChatServer.smallGroups.keySet()) {
            SmallGroup group = ChatServer.smallGroups.get(groupName);
            if (group.isMember(this.username)) {
                this.currentSmallGroupName = groupName;
                sendMessage("GROUP_JOINED|" + groupName);
                sendMessage("SERVER: 您已重新加入小组 " + groupName);
                return;
            }
        }
    }

    // 工具方法：
    private static byte[] longToBytes(long x) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(8);
        buffer.putLong(x);
        return buffer.array();
    }
}