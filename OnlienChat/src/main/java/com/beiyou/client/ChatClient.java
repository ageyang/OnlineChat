package com.beiyou.client;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class ChatClient {
    private static final String SERVER_HOST = "127.0.0.1";
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) {
        try (
                Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
                Scanner scanner = new Scanner(System.in)
        ) {
            // 1. 输入用户名和密码
            System.out.print("请输入用户名：");
            String username = scanner.nextLine();
            System.out.print("请输入密码：");
            String password = scanner.nextLine();
            // 2. 发送登录消息
            out.println("LOGIN|" + username + "|" + password);

            // 3. 等待服务器返回登录结果
            String loginResult = in.readLine();
            if (loginResult == null || !loginResult.equals("LOGIN_SUCCESS")) {
                System.out.println("登录失败: " + (loginResult == null ? "未知错误" : loginResult));
                return;
            }
            System.out.println("登录成功，欢迎您：" + username);
            System.out.println("--------------------");
            System.out.println("聊天指令:");
            System.out.println("  群聊:直接输入消息");
            System.out.println("  私聊: /w <用户名> <消息>");
            System.out.println("  创建小组: /creategroup <小组名> [邀请成员名...]");
            System.out.println("  邀请成员: /invite <用户名>");
            System.out.println("  接受邀请: /accept <小组名>");
            System.out.println("  小组聊天: /g <消息>");
            System.out.println("  离开小组: /leave");
            System.out.println("  退出程序: exit");
            System.out.println("--------------------");

            // 4. 启动接收线程
            new Thread(() -> {
                try {
                    String msgFromServer;
                    while ((msgFromServer = in.readLine()) != null) {
                        if (msgFromServer.startsWith("KICK_OFF")) {
                            String reason = msgFromServer.contains("|") ? msgFromServer.split("\\|", 2)[1] : "您已被踢下线。";
                            System.out.println(reason);
                            System.exit(0); // 结束进程
                        } else if (msgFromServer.startsWith("USER_LIST|")) {
                            String users = msgFromServer.substring(10);
                            System.out.println("当前在线用户：" + users);
                        } else if (msgFromServer.startsWith("INVITE|")) {
                            // INVITE|groupName|inviter
                            String[] parts = msgFromServer.split("\\|", 3);
                            System.out.println("收到邀请: 用户 " + parts[2] + " 邀请您加入小组 " + parts[1]);
                            System.out.println("输入 /accept " + parts[1] + " 加入小组。");
                        } else {
                            System.out.println(msgFromServer);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("与服务器连接断开。");
                }
            }).start();

            // 5. 主线程负责发送消息
            while (true) {
                String msg = scanner.nextLine();
                if ("exit".equalsIgnoreCase(msg)) {
                    System.out.println("退出聊天室。");
                    break;
                }
                out.println(msg);
            }
        } catch (IOException e) {
            System.out.println("无法连接到服务器: " + e.getMessage());
        }
    }
}