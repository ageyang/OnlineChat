package com.beiyou.client;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.util.Map;

// 主界面类，负责群聊、私聊窗口管理和主UI
public class ChatClientGUI extends JFrame implements ClientMessageHandler.MessageListener {
    // 聊天显示区
    private JTextPane chatArea;
    // 输入框
    private JTextField inputField;
    // 发送按钮
    private JButton sendButton;
    // 返回群聊按钮
    private JButton backToGroupButton;
    // 在线用户列表
    private JList<String> userList;
    private DefaultListModel<String> userListModel;
    // 群聊列表
    private JList<String> groupList;
    private DefaultListModel<String> groupListModel;
    // 创建群聊按钮
    private JButton createGroupButton;
    // 管理所有群聊窗口
    public Map<String, GroupChatWindow> groupWindows;
    // 管理所有私聊窗口
    public Map<String, PrivateChatWindow> privateWindows = new HashMap<>();
    // 聊天窗口数量限制
    private static final int MAX_CHAT_WINDOWS = 10;
    private int currentChatWindowCount = 0;
    // 消息处理器
    private ClientMessageHandler messageHandler;
    // 当前私聊对象
    private String currentChatTarget;
    // 当前用户名
    private String currentUsername;
    // 文件传输相关组件
    private JButton uploadFileButton;
    private JButton downloadFileButton;
    private DefaultListModel<String> fileListModel;
    private JList<String> fileList;
    private JFileChooser fileChooser;
    private String currentDownloadFile;
    // 文件区切换状态
    private boolean showGroupFiles = true;
    private JButton switchFileTypeButton;
    // 发送图片按钮
    private JButton sendImageButton;

    public ChatClientGUI() {
        setTitle("群聊 - 聊天室");
        setSize(800, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // 初始化群聊窗口管理
        groupWindows = new HashMap<>();

        // 设置文件选择器默认目录
        fileChooser = new JFileChooser(new java.io.File("src/main/resources/Download"));

        initComponents();
        setupEventHandlers();
        
        if (loginAndConnect()) {
            setVisible(true);
        } else {
            System.exit(0);
        }
    }

    // 初始化UI组件
    private void initComponents() {
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setContentType("text/html");
        JScrollPane chatScroll = new JScrollPane(chatArea);

        inputField = new JTextField();
        sendButton = new JButton("发送");
        backToGroupButton = new JButton("返回群聊");
        backToGroupButton.setEnabled(false);
        sendImageButton = new JButton("发送图片");

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(inputField, BorderLayout.CENTER);
        // 右侧按钮区
        JPanel sendBtnPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        sendBtnPanel.add(sendButton);
        sendBtnPanel.add(sendImageButton);
        inputPanel.add(sendBtnPanel, BorderLayout.EAST);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(backToGroupButton, BorderLayout.WEST);

        // 左侧聊天区域
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.add(topPanel, BorderLayout.NORTH);
        chatPanel.add(chatScroll, BorderLayout.CENTER);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);

        // 右侧面板：分为在线用户和群聊列表
        JPanel rightPanel = new JPanel(new BorderLayout());
        
        // 在线用户列表
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setPreferredSize(new Dimension(150, 200));
        JPanel userPanel = new JPanel(new BorderLayout());
        userPanel.setBorder(BorderFactory.createTitledBorder("在线用户"));
        userPanel.add(userScroll, BorderLayout.CENTER);
        
        // 群聊列表
        groupListModel = new DefaultListModel<>();
        groupList = new JList<>(groupListModel);
        JScrollPane groupScroll = new JScrollPane(groupList);
        groupScroll.setPreferredSize(new Dimension(150, 200));
        JPanel groupPanel = new JPanel(new BorderLayout());
        groupPanel.setBorder(BorderFactory.createTitledBorder("我的群聊"));
        
        // 创建群聊按钮
        createGroupButton = new JButton("创建群聊");
        groupPanel.add(createGroupButton, BorderLayout.NORTH);
        groupPanel.add(groupScroll, BorderLayout.CENTER);
        
        // 将用户列表和群聊列表添加到右侧面板
        rightPanel.add(userPanel, BorderLayout.NORTH);
        rightPanel.add(groupPanel, BorderLayout.CENTER);
        
        // 设置分割面板
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chatPanel, rightPanel);
        splitPane.setDividerLocation(550);

        // 新增：文件传输相关UI
        fileListModel = new DefaultListModel<>();
        fileList = new JList<>(fileListModel);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane fileListScroll = new JScrollPane(fileList);
        fileListScroll.setPreferredSize(new Dimension(180, 100)); // 缩小文件区高度

        uploadFileButton = new JButton("上传文件");
        downloadFileButton = new JButton("下载文件");
        switchFileTypeButton = new JButton("切换到小组文件");
        fileChooser = new JFileChooser(new java.io.File("src/main/resources/Download"));

        JPanel filePanelRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filePanelRow1.add(uploadFileButton);
        filePanelRow1.add(downloadFileButton);
        filePanelRow1.add(switchFileTypeButton);

        JPanel filePanel = new JPanel(new BorderLayout());
        JPanel fileButtonPanel = new JPanel(new GridLayout(2, 1));
        fileButtonPanel.add(filePanelRow1);
        fileButtonPanel.add(fileListScroll, BorderLayout.CENTER);
        filePanel.add(fileButtonPanel, BorderLayout.NORTH);
        filePanel.setBorder(BorderFactory.createTitledBorder("群文件/小组文件"));
        filePanel.setPreferredSize(new Dimension(200, 180)); // 缩小文件区宽度

        chatPanel.add(filePanel, BorderLayout.EAST);

        add(splitPane, BorderLayout.CENTER);
    }

    // 设置事件监听器
    private void setupEventHandlers() {
        sendButton.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());
        backToGroupButton.addActionListener(e -> switchToGroupChat());
        createGroupButton.addActionListener(e -> showCreateGroupDialog());
        uploadFileButton.addActionListener(e -> uploadFile());
        downloadFileButton.addActionListener(e -> downloadSelectedFile());
        switchFileTypeButton.addActionListener(e -> switchFileType(switchFileTypeButton));
        sendImageButton.addActionListener(e -> sendImage());

        userList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String targetUser = userList.getSelectedValue();
                    if (targetUser != null && !targetUser.equals(currentUsername)) {
                        // 打开/激活唯一私聊窗口
                        if (privateWindows.containsKey(targetUser)) {
                            PrivateChatWindow window = privateWindows.get(targetUser);
                            if (window.isVisible()) {
                                window.toFront();
                            } else {
                                window.setVisible(true);
                            }
                        } else {
                            PrivateChatWindow window = new PrivateChatWindow(targetUser, currentUsername, messageHandler, ChatClientGUI.this);
                            privateWindows.put(targetUser, window);
                            window.setVisible(true);
                        }
                    }
                }
            }
        });
        
        groupList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String groupName = groupList.getSelectedValue();
                    if (groupName != null) {
                        openGroupChatWindow(groupName);
                    }
                }
            }
        });

        fileList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    downloadSelectedFile();
                }
            }
        });
    }

    // 登录并连接服务器
    private boolean loginAndConnect() {
        String username = JOptionPane.showInputDialog(this, "请输入用户名：");
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        
        String password = JOptionPane.showInputDialog(this, "请输入密码：");
        if (password == null) {
            return false;
        }

        messageHandler = new ClientMessageHandler(this);
        if (messageHandler.login(username, password)) {
            currentUsername = username;
            setTitle("群聊 - " + username);
            // 登录成功后加载群聊历史
            loadGroupChatHistory();
            return true;
        } else {
            JOptionPane.showMessageDialog(this, "登录失败！");
            return false;
        }
    }

    // 发送消息（群聊或私聊）
    private void sendMessage() {
        String msg = inputField.getText().trim();
        if (!msg.isEmpty()) {
            if (currentChatTarget != null) {
                // 私聊模式
                messageHandler.sendPrivateMessage(currentChatTarget, msg);
            } else {
                // 群聊模式
                if (msg.equals("/help")) {
                    // 发送帮助命令到服务器
                    messageHandler.sendMessage("/help");
                } else {
                    messageHandler.sendMessage(msg);
                }
            }
            inputField.setText("");
        }
    }

    // 切换到私聊窗口（已废弃，现用独立窗口）
    private void switchToPrivateChat(String targetUser) {
        currentChatTarget = targetUser;
        setTitle("私聊 - " + targetUser);
        backToGroupButton.setEnabled(true);
        chatArea.setText(""); // 清空聊天区域
        appendText("=== 与 " + targetUser + " 的私聊 ===\n");
        
        // 读取与目标用户的私聊历史
        loadPrivateChatHistory(targetUser);
    }

    // 切换到群聊窗口
    private void switchToGroupChat() {
        currentChatTarget = null;
        setTitle("群聊 - " + currentUsername);
        backToGroupButton.setEnabled(false);
        chatArea.setText(""); // 清空聊天区域
        appendText("=== 群聊 ===\n");
        
        // 读取群聊历史
        loadGroupChatHistory();
    }
    
    // 加载与目标用户的私聊历史
    private void loadPrivateChatHistory(String targetUser) {
        java.util.List<String> history = com.beiyou.server.util.MessageLogger.readUserHistory(currentUsername);
        for (String line : history) {
            if (line.contains("[私聊]")) {
                if (line.contains("[" + currentUsername + "->" + targetUser + "]:") || 
                    line.contains("[" + targetUser + "->" + currentUsername + "]:")) {
                    try {
                        int startIndex = line.indexOf('[', 4) + 1;
                        String userPart = line.substring(startIndex, line.indexOf(']', startIndex));
                        String[] userParts = userPart.split("->");
                        if (userParts.length == 2) {
                            String from = userParts[0];
                            String content = line.split("]: ", 2)[1];
                            if (content.contains("[图片]:")) {
                                appendHistoryLine(line);
                            } else {
                                appendText(from + ": " + content + "\n");
                            }
                        }
                    } catch (Exception e) {
                        appendHistoryLine(line);
                    }
                }
            }
        }
    }
    
    // 加载群聊历史
    private void loadGroupChatHistory() {
        java.util.List<String> history = com.beiyou.server.util.MessageLogger.readUserHistory(currentUsername);
        for (String line : history) {
            if (line.contains("[群聊]")) {
                appendHistoryLine(line);
            }
        }
    }

    // 消息监听器实现：被踢下线
    @Override
    public void onKickOff(String reason) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this, reason);
            System.exit(0);
        });
    }

    // 消息监听器实现：用户列表更新
    @Override
    public void onUserListUpdate(String users) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            for (String user : users.split(",")) {
                if (!user.trim().isEmpty()) {
                    userListModel.addElement(user.trim());
                }
            }
        });
    }

    // 消息监听器实现：私聊消息分发到唯一窗口
    @Override
    public void onPrivateMessage(String from, String to, String content) {
        SwingUtilities.invokeLater(() -> {
            String otherUser = from.equals(currentUsername) ? to : from;
            PrivateChatWindow window = privateWindows.get(otherUser);
            if (window == null) {
                window = new PrivateChatWindow(otherUser, currentUsername, messageHandler, this);
                privateWindows.put(otherUser, window);
                window.setVisible(true);
            }
            window.appendMessage(from, to, content);
        });
    }

    // 消息监听器实现：群聊消息
    @Override
    public void onGroupMessage(String from, String content) {
        SwingUtilities.invokeLater(() -> {
            // 只有在群聊模式下才显示群聊消息
            if (currentChatTarget == null) {
                appendText("[群聊][" + from + "]: " + content + "\n");
            }
        });
    }

    // 消息监听器实现：服务器消息
    @Override
    public void onServerMessage(String message) {
        //System.out.println("收到原始消息: [" + message + "]");
        SwingUtilities.invokeLater(() -> {
            if (message.contains("[图片]:")) {
                System.out.println("收到图片消息");
                // 只处理图片，不显示原文
                int idx = message.indexOf("[图片]:");
                String prefix = message.substring(0, idx);
                String imgInfo = message.substring(idx + 5);
                String filename, dir;
                if (imgInfo.contains("@")) {
                    String[] arr = imgInfo.split("@");
                    filename = arr[0];
                    dir = arr[1];
                } else {
                    filename = imgInfo;
                    dir = "global";
                }
                appendText(prefix + "\n");
                java.io.File imgFile = new java.io.File("src/main/resources/images/" + dir + "/" + filename);
                if (imgFile.exists()) {
                    try {
                        ImageIcon icon = new ImageIcon(imgFile.getAbsolutePath());
                        java.awt.Image scaled = icon.getImage().getScaledInstance(120, 120, java.awt.Image.SCALE_SMOOTH);
                        icon = new ImageIcon(scaled);
                        appendImage(icon, filename);
                    } catch (Exception e) {
                        appendText("[图片]显示失败\n");
                    }
                } else {
                    appendText("[图片]未找到\n");
                }
                return; // 关键：图片消息处理后直接返回，防止走到appendText
            } else if (message.startsWith("FILELIST|")) {
                fileListModel.clear();
                String[] files = message.substring(9).split(",");
                for (String f : files) {
                    if (!f.trim().isEmpty()) fileListModel.addElement(f.trim());
                }
            } else if (message.startsWith("FILEPORT ")) {
                int port = Integer.parseInt(message.split(" ", 2)[1]);
                new Thread(() -> downloadFileFromPort(port, currentDownloadFile)).start();
            } else if (message.startsWith("SERVER: 私聊文件") || message.startsWith("[私聊][SERVER]: ")) {
                if (currentChatTarget != null) {
                    appendText(message + "\n");
                }
            } else if (message.trim().matches("^/imgsend\\s+.*")) {
                // 过滤/imgsend命令原文，不显示
                return;
            } else {
                appendText(message + "\n");
            }
        });
    }

    // 消息监听器实现：连接错误
    @Override
    public void onConnectionError(String error) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this, error);
            System.exit(0);
        });
    }

    // 群聊相关方法：显示创建群聊对话框
    private void showCreateGroupDialog() {
        String groupName = JOptionPane.showInputDialog(this, "请输入群聊名称：");
        if (groupName != null && !groupName.trim().isEmpty()) {
            String inviteUsers = JOptionPane.showInputDialog(this, "请输入要邀请的用户名（多个用户用空格分隔）：");
            String command = "/creategroup " + groupName.trim();
            if (inviteUsers != null && !inviteUsers.trim().isEmpty()) {
                command += " " + inviteUsers.trim();
            }
            messageHandler.sendMessage(command);
        }
    }
    
    // 打开群聊窗口
    private void openGroupChatWindow(String groupName) {
        // 检查是否已经打开了该群聊窗口
        if (groupWindows.containsKey(groupName)) {
            GroupChatWindow window = groupWindows.get(groupName);
            if (window.isVisible()) {
                window.toFront(); // 将窗口提到前面
                return;
            }
        }
        
        // 检查聊天窗口数量限制
        if (currentChatWindowCount >= MAX_CHAT_WINDOWS) {
            JOptionPane.showMessageDialog(this, 
                "已达到最大聊天窗口数量限制（" + MAX_CHAT_WINDOWS + "个），请关闭一些聊天窗口后再试。",
                "窗口数量限制",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // 创建新的群聊窗口
        GroupChatWindow groupWindow = new GroupChatWindow(groupName, currentUsername, messageHandler, this);
        groupWindows.put(groupName, groupWindow);
        currentChatWindowCount++;
        groupWindow.setVisible(true);
    }
    
    // 显示群聊邀请
    public void showGroupInvitation(String groupName, String inviter) {
        SwingUtilities.invokeLater(() -> {
            int choice = JOptionPane.showConfirmDialog(
                this,
                "用户 " + inviter + " 邀请您加入群聊 " + groupName + "\n是否接受邀请？",
                "群聊邀请",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            );
            
            if (choice == JOptionPane.YES_OPTION) {
                messageHandler.sendMessage("/accept " + groupName);
            }
        });
    }
    
    // 添加群聊到列表
    public void addGroupToList(String groupName) {
        SwingUtilities.invokeLater(() -> {
            if (!groupListModel.contains(groupName)) {
                groupListModel.addElement(groupName);
            }
        });
    }
    
    // 从列表移除群聊
    public void removeGroupFromList(String groupName) {
        SwingUtilities.invokeLater(() -> {
            groupListModel.removeElement(groupName);
            // 关闭对应的群聊窗口
            GroupChatWindow window = groupWindows.remove(groupName);
            if (window != null) {
                window.dispose();
                // 减少聊天窗口计数器
                decreaseChatWindowCount();
            }
        });
    }
    
    // 接收群聊消息
    public void receiveGroupMessage(String groupName, String from, String content) {
        SwingUtilities.invokeLater(() -> {
            GroupChatWindow window = groupWindows.get(groupName);
            if (window != null) {
                window.appendMessage(from + ": " + content);
            }
        });
    }
    
    // 显示帮助信息
    public void showHelpMessage(String helpText) {
        SwingUtilities.invokeLater(() -> {
            appendText("=== 帮助信息 ===\n");
            appendText(helpText + "\n");
            appendText("================\n");
        });
    }

    // 实现群聊相关的MessageListener接口方法
    @Override
    public void onGroupInvitation(String groupName, String inviter) {
        showGroupInvitation(groupName, inviter);
    }
    
    @Override
    public void onGroupMessage(String groupName, String from, String content) {
        receiveGroupMessage(groupName, from, content);
    }
    
    @Override
    public void onGroupJoined(String groupName) {
        addGroupToList(groupName);
    }
    
    @Override
    public void onGroupLeft(String groupName) {
        removeGroupFromList(groupName);
    }
    
    @Override
    public void onHelpMessage(String helpText) {
        showHelpMessage(helpText);
    }

    // 聊天窗口计数器减少
    public void decreaseChatWindowCount() {
        if (currentChatWindowCount > 0) {
            currentChatWindowCount--;
        }
    }

    // 文件传输相关方法
    private void uploadFile() {
        if (currentChatTarget != null) { // 私聊模式
            int confirm = JOptionPane.showConfirmDialog(this, "即将发送文件给 " + currentChatTarget + "，是否继续？", "上传确认", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;
            int result = fileChooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                java.io.File file = fileChooser.getSelectedFile();
                String filename = file.getName();
                long filesize = file.length();
                try {
                    messageHandler.sendMessage("/uploadfile " + currentChatTarget + " " + filename + " " + filesize);
                    java.io.FileInputStream fis = new java.io.FileInputStream(file);
                    java.io.OutputStream out = messageHandler.getSocket().getOutputStream();
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = fis.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    out.flush();
                    fis.close();
                    JOptionPane.showMessageDialog(this, "文件发送成功！");
                    refreshFileList();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "文件发送失败: " + ex.getMessage());
                }
            }
            return;
        }
        // 群聊/小组模式
        if (!showGroupFiles && getCurrentSmallGroupName() == null) {
            JOptionPane.showMessageDialog(this, "当前未加入任何小组，无法上传小组文件！");
            return;
        }
        String uploadTarget = showGroupFiles ? "群聊" : "小组";
        String groupName = getCurrentSmallGroupName();
        String targetMsg = showGroupFiles ? "群聊" : (groupName != null ? ("小组：" + groupName) : "小组");
        int confirm = JOptionPane.showConfirmDialog(this, "即将上传到 " + targetMsg + "，是否继续？", "上传确认", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            java.io.File file = fileChooser.getSelectedFile();
            String filename = file.getName();
            long filesize = file.length();
            try {
                if (showGroupFiles) {
                    messageHandler.sendMessage("/upload " + filename + " " + filesize);
                } else {
                    messageHandler.sendMessage("/upload " + groupName + " " + filename + " " + filesize);
                }
                java.io.FileInputStream fis = new java.io.FileInputStream(file);
                java.io.OutputStream out = messageHandler.getSocket().getOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
                fis.close();
                JOptionPane.showMessageDialog(this, "文件上传成功！");
                refreshFileList();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "文件上传失败: " + ex.getMessage());
            }
        }
    }

    private void refreshFileList() {
        if (currentChatTarget != null) {
            messageHandler.sendMessage("/filelistfile " + currentChatTarget);
        } else if (showGroupFiles) {
            messageHandler.sendMessage("/filelist");
        } else {
            if (getCurrentSmallGroupName() != null) {
                messageHandler.sendMessage("/filelist " + getCurrentSmallGroupName());
            } else {
                fileListModel.clear();
            }
        }
    }

    private void downloadSelectedFile() {
        String filename = fileList.getSelectedValue();
        if (filename == null) {
            JOptionPane.showMessageDialog(this, "请选择要下载的文件");
            return;
        }
        currentDownloadFile = filename;
        if (currentChatTarget != null) {
            messageHandler.sendMessage("/downloadfile " + currentChatTarget + " " + filename);
        } else if (showGroupFiles) {
            messageHandler.sendMessage("/download " + filename);
        } else {
            if (getCurrentSmallGroupName() != null) {
                messageHandler.sendMessage("/download " + getCurrentSmallGroupName() + " " + filename);
            }
        }
    }

    private void downloadFileFromPort(int port, String filename) {
        try (java.net.Socket fileSocket = new java.net.Socket("127.0.0.1", port)) {
            java.io.InputStream in = fileSocket.getInputStream();
            // 先读8字节文件大小
            byte[] sizeBytes = new byte[8];
            int readSize = 0;
            while (readSize < 8) {
                int r = in.read(sizeBytes, readSize, 8 - readSize);
                if (r == -1) throw new java.io.IOException("连接中断");
                readSize += r;
            }
            long filesize = bytesToLong(sizeBytes);
            fileChooser.setSelectedFile(new java.io.File(filename));
            int result = fileChooser.showSaveDialog(this);
            if (result != JFileChooser.APPROVE_OPTION) return;
            java.io.File saveFile = fileChooser.getSelectedFile();
            java.io.FileOutputStream fos = new java.io.FileOutputStream(saveFile);
            byte[] buffer = new byte[4096];
            long remaining = filesize;
            while (remaining > 0) {
                int read = in.read(buffer, 0, (int)Math.min(buffer.length, remaining));
                if (read == -1) break;
                fos.write(buffer, 0, read);
                remaining -= read;
            }
            fos.close();
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this, "文件下载完成: " + saveFile.getAbsolutePath())
            );
        } catch (Exception e) {
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this, "文件下载失败: " + e.getMessage())
            );
        }
    }

    private static long bytesToLong(byte[] bytes) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
        return buffer.getLong();
    }

    // 文件区切换
    private void switchFileType(JButton switchBtn) {
        showGroupFiles = !showGroupFiles;
        if (showGroupFiles) {
            switchBtn.setText("切换到小组文件");
            refreshFileList();
        } else {
            switchBtn.setText("切换到群聊文件");
            refreshGroupFileList();
        }
    }

    private void refreshGroupFileList() {
        // 只显示小组文件
        if (getCurrentSmallGroupName() != null) {
            messageHandler.sendMessage("/filelist " + getCurrentSmallGroupName());
        } else {
            fileListModel.clear();
        }
    }

    private String getCurrentSmallGroupName() {
        // 查找当前用户所在小组名
        for (String groupName : groupWindows.keySet()) {
            if (groupWindows.get(groupName) != null) {
                return groupName;
            }
        }
        return null;
    }

    // 发送图片方法
    private void sendImage() {
        JFileChooser imgChooser = new JFileChooser();
        imgChooser.setDialogTitle("选择要发送的图片");
        imgChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("图片文件", "jpg", "jpeg", "png", "gif", "bmp"));
        int result = imgChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            java.io.File file = imgChooser.getSelectedFile();
            String filename = file.getName();
            long filesize = file.length();
            String type;
            String target;
            if (currentChatTarget != null) {
                type = "private";
                target = currentChatTarget;
            } else if (!showGroupFiles && getCurrentSmallGroupName() != null) {
                type = "smallgroup";
                target = getCurrentSmallGroupName();
            } else {
                type = "group";
                target = "global";
            }
            try {
                messageHandler.sendMessage("/imgsend " + target + " " + filename + " " + filesize + " " + type);
                java.io.FileInputStream fis = new java.io.FileInputStream(file);
                java.io.OutputStream out = messageHandler.getSocket().getOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
                fis.close();
                JOptionPane.showMessageDialog(this, "图片发送成功！");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "图片发送失败: " + ex.getMessage());
            }
        }
    }

    // 辅助方法：插入文本到JTextPane
    private void appendText(String text) {
        try {
            StyledDocument doc = chatArea.getStyledDocument();
            doc.insertString(doc.getLength(), text, null);
            chatArea.setCaretPosition(doc.getLength());
        } catch (Exception e) {}
    }

    // 辅助方法：插入图片到JTextPane
    private void appendImage(ImageIcon icon, String filename) {
        try {
            StyledDocument doc = chatArea.getStyledDocument();
            chatArea.setCaretPosition(doc.getLength());
            chatArea.insertIcon(icon);
            doc.insertString(doc.getLength(), "\n", null);
        } catch (Exception e) {
            appendText("[图片]插入失败: " + filename + "\n");
        }
    }

    // 历史记录加载图片支持
    private void appendHistoryLine(String line) {
        if (line.contains("[图片]:")) {
            int idx = line.indexOf("[图片]:");
            String prefix = line.substring(0, idx);
            String imgInfo = line.substring(idx + 5);
            String filename, dir;
            if (imgInfo.contains("@")) {
                String[] arr = imgInfo.split("@");
                filename = arr[0];
                dir = arr[1];
            } else {
                filename = imgInfo;
                dir = "global";
            }
            appendText(prefix + "\n"); // 只显示前缀
            java.io.File imgFile = new java.io.File("src/main/resources/images/" + dir + "/" + filename);
            if (imgFile.exists()) {
                try {
                    ImageIcon icon = new ImageIcon(imgFile.getAbsolutePath());
                    java.awt.Image scaled = icon.getImage().getScaledInstance(120, 120, java.awt.Image.SCALE_SMOOTH);
                    icon = new ImageIcon(scaled);
                    appendImage(icon, filename);
                } catch (Exception e) {
                    appendText("[图片]显示失败\n");
                }
            } else {
                appendText("[图片]未找到\n");
            }
        } else {
            appendText(line + "\n");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatClientGUI::new);
    }
} 