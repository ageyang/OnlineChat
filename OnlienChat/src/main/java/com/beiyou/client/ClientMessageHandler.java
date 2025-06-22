package com.beiyou.client;

import java.io.*;
import java.net.Socket;

public class ClientMessageHandler {
    private static final String SERVER_HOST = "127.0.0.1";
    private static final int SERVER_PORT = 12345;
    
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private MessageListener messageListener;
    private ChatClientGUI mainWindow; // 添加主窗口引用
    private Socket socket;
    
    public interface MessageListener {
        void onKickOff(String reason);
        void onUserListUpdate(String users);
        void onPrivateMessage(String from, String to, String content);
        void onGroupMessage(String from, String content);
        void onServerMessage(String message);
        void onConnectionError(String error);
        // 新增群聊相关接口
        void onGroupInvitation(String groupName, String inviter);
        void onGroupMessage(String groupName, String from, String content);
        void onGroupJoined(String groupName);
        void onGroupLeft(String groupName);
        void onHelpMessage(String helpText);
    }
    
    public ClientMessageHandler(MessageListener listener) {
        this.messageListener = listener;
        if (listener instanceof ChatClientGUI) {
            this.mainWindow = (ChatClientGUI) listener;
        }
    }
    
    public boolean login(String username, String password) {
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            
            out.println("LOGIN|" + username + "|" + password);
            String loginResult = in.readLine();
            
            if ("LOGIN_SUCCESS".equals(loginResult)) {
                this.username = username;
                startMessageReceiver();
                return true;
            } else {
                return false;
            }
        } catch (IOException e) {
            messageListener.onConnectionError("无法连接服务器: " + e.getMessage());
            return false;
        }
    }
    
    private void startMessageReceiver() {
        new Thread(() -> {
            try {
                String msg;
                while ((msg = in.readLine()) != null) {
                    handleMessage(msg);
                }
            } catch (IOException e) {
                messageListener.onConnectionError("与服务器连接断开");
            }
        }).start();
    }
    
    private void handleMessage(String msg) {
        System.out.println("收到消息: " + msg); // 调试信息
        if (msg.startsWith("KICK_OFF")) {
            String reason = msg.contains("|") ? msg.split("\\|", 2)[1] : "您已被踢下线";
            messageListener.onKickOff(reason);
        } else if (msg.startsWith("USER_LIST|")) {
            messageListener.onUserListUpdate(msg.substring(10));
        } else if (msg.startsWith("INVITE|")) {
            // 处理群聊邀请: INVITE|groupName|inviter
            String[] parts = msg.split("\\|", 3);
            if (parts.length == 3) {
                String groupName = parts[1];
                String inviter = parts[2];
                messageListener.onGroupInvitation(groupName, inviter);
            }
        } else if (msg.startsWith("GROUP_JOINED|")) {
            // 处理加入群聊通知: GROUP_JOINED|groupName
            String groupName = msg.substring(13);
            messageListener.onGroupJoined(groupName);
        } else if (msg.startsWith("GROUP_LEFT|")) {
            // 处理离开群聊通知: GROUP_LEFT|groupName
            String groupName = msg.substring(11);
            messageListener.onGroupLeft(groupName);
        } else if (msg.startsWith("HELP|")) {
            // 处理帮助消息: HELP|helpText
            String helpText = msg.substring(5);
            messageListener.onHelpMessage(helpText);
        } else if (msg.startsWith("[私聊]")) {
            System.out.println("解析私聊消息: " + msg); // 调试信息
            parsePrivateMessage(msg);
        } else if (msg.startsWith("[群聊]")) {
            parseGroupMessage(msg);
        } else if (msg.startsWith("[小组:")) {
            parseSmallGroupMessage(msg);
        } else {
            messageListener.onServerMessage(msg);
        }
    }
    
    private void parsePrivateMessage(String msg) {
        // 解析格式: [私聊][from->to]: 内容
        String[] parts = msg.split("]: ", 2);
        if (parts.length == 2) {
            // 找到第二个[的位置，然后+1去掉[
            int startIndex = msg.indexOf('[', 4) + 1;
            String userPart = msg.substring(startIndex, msg.indexOf(']', startIndex));
            String[] userParts = userPart.split("->");
            if (userParts.length == 2) {
                String from = userParts[0];
                String to = userParts[1];
                String content = parts[1];
                System.out.println("解析私聊消息成功 - from: " + from + ", to: " + to + ", content: " + content); // 调试信息
                messageListener.onPrivateMessage(from, to, content);
            } else {
                System.out.println("解析私聊消息失败 - userParts长度: " + userParts.length); // 调试信息
            }
        } else {
            System.out.println("解析私聊消息失败 - parts长度: " + parts.length); // 调试信息
        }
    }
    
    private void parseGroupMessage(String msg) {
        // 解析格式: [群聊][username]: 内容
        String[] parts = msg.split("]: ", 2);
        if (parts.length == 2) {
            int start = msg.indexOf("[群聊][") + 5;
            int end = msg.indexOf("]:", start);
            if (start >= 5 && end > start) {
                String from = msg.substring(start, end);
                String content = parts[1];
                // 新增：如果是图片消息，直接走onServerMessage统一处理
                if (content.startsWith("[图片]:")) {
                    messageListener.onServerMessage(msg);
                } else {
                    messageListener.onGroupMessage(from, content);
                }
            }
        }
    }
    
    private void parseSmallGroupMessage(String msg) {
        // 解析格式: [小组:groupName][username]: 内容
        String[] parts = msg.split("]: ", 2);
        if (parts.length == 2) {
            // 提取群聊名称和发送者
            int groupStart = msg.indexOf("小组:") + 3;
            int groupEnd = msg.indexOf("][", groupStart);
            int userStart = groupEnd + 2;
            int userEnd = msg.indexOf("]:", userStart);
            
            if (groupStart > 2 && groupEnd > groupStart && userStart > groupEnd && userEnd > userStart) {
                String groupName = msg.substring(groupStart, groupEnd);
                String from = msg.substring(userStart, userEnd);
                String content = parts[1];
                messageListener.onGroupMessage(groupName, from, content);
            }
        }
    }
    
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }
    
    public void sendPrivateMessage(String targetUser, String content) {
        sendMessage("/w " + targetUser + " " + content);
    }
    
    public String getUsername() {
        return username;
    }
    
    public ChatClientGUI getMainWindow() {
        return mainWindow;
    }
    
    public void close() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public Socket getSocket() {
        return socket;
    }
} 