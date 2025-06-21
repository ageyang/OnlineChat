package com.beiyou.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.text.StyledDocument;

public class GroupChatWindow extends JFrame {
    private JTextPane chatArea;
    private JTextField inputField;
    private JButton sendButton;
    private JButton sendImageButton;
    private JButton inviteButton;
    private JButton leaveButton;
    private DefaultListModel<String> fileListModel;
    private JList<String> fileList;
    private JFileChooser fileChooser;
    private String currentDownloadFile;
    
    private String groupName;
    private String currentUsername;
    private ClientMessageHandler messageHandler;
    private ChatClientGUI mainWindow;

    public GroupChatWindow(String groupName, String currentUsername, ClientMessageHandler messageHandler, ChatClientGUI mainWindow) {
        this.groupName = groupName;
        this.currentUsername = currentUsername;
        this.messageHandler = messageHandler;
        this.mainWindow = mainWindow;
        
        setTitle("群聊 - " + groupName);
        setSize(500, 400);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        
        initComponents();
        setupEventHandlers();
        
        // 新增：请求群聊历史
        messageHandler.sendMessage("/grouphistory " + groupName);
    }
    
    private void initComponents() {
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        JScrollPane chatScroll = new JScrollPane(chatArea);
        
        inputField = new JTextField();
        sendButton = new JButton("发送");
        sendImageButton = new JButton("发送图片");
        inviteButton = new JButton("邀请");
        leaveButton = new JButton("离开群聊");
        
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(inputField, BorderLayout.CENTER);
        JPanel sendBtnPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        sendBtnPanel.add(sendButton);
        sendBtnPanel.add(sendImageButton);
        inputPanel.add(sendBtnPanel, BorderLayout.EAST);
        
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(inviteButton);
        buttonPanel.add(leaveButton);
        
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(inputPanel, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        add(chatScroll, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
        
        // 添加窗口关闭事件
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // 窗口关闭时从主窗口的群聊窗口管理中移除
                if (messageHandler.getMainWindow() != null) {
                    messageHandler.getMainWindow().groupWindows.remove(groupName);
                }
                // 新增：减少聊天窗口计数器
                if (mainWindow != null) {
                    mainWindow.decreaseChatWindowCount();
                }
            }
        });
    }
    
    private void setupEventHandlers() {
        sendButton.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());
        inviteButton.addActionListener(e -> showInviteDialog());
        leaveButton.addActionListener(e -> leaveGroup());
        sendImageButton.addActionListener(e -> sendImage());
    }
    
    private void sendMessage() {
        String msg = inputField.getText().trim();
        if (!msg.isEmpty()) {
            messageHandler.sendMessage("/g " + msg);
            inputField.setText("");
        }
    }
    
    private void showInviteDialog() {
        String inviteUsers = JOptionPane.showInputDialog(this, "请输入要邀请的用户名（多个用户用空格分隔）：");
        if (inviteUsers != null && !inviteUsers.trim().isEmpty()) {
            String[] users = inviteUsers.trim().split("\\s+");
            for (String user : users) {
                if (!user.trim().isEmpty()) {
                    messageHandler.sendMessage("/invite " + user.trim());
                }
            }
        }
    }
    
    private void leaveGroup() {
        int choice = JOptionPane.showConfirmDialog(
            this,
            "确定要离开群聊 " + groupName + " 吗？",
            "离开群聊",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        
        if (choice == JOptionPane.YES_OPTION) {
            messageHandler.sendMessage("/leave");
            dispose();
        }
    }
    
    private void sendImage() {
        JFileChooser imgChooser = new JFileChooser();
        imgChooser.setDialogTitle("选择要发送的图片");
        imgChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("图片文件", "jpg", "jpeg", "png", "gif", "bmp"));
        int result = imgChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            java.io.File file = imgChooser.getSelectedFile();
            String filename = file.getName();
            long filesize = file.length();
            try {
                // 发送图片命令
                messageHandler.sendMessage("/imgsend " + groupName + " " + filename + " " + filesize + " smallgroup");
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
    
    public void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            StyledDocument doc = chatArea.getStyledDocument();
            try {
                if (message.contains("[图片]:")) {
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
                        dir = groupName;
                    }
                    doc.insertString(doc.getLength(), prefix + "\n", null);
                    java.io.File imgFile = new java.io.File("src/main/resources/images/" + dir + "/" + filename);
                    if (imgFile.exists()) {
                        try {
                            ImageIcon icon = new ImageIcon(imgFile.getAbsolutePath());
                            java.awt.Image scaled = icon.getImage().getScaledInstance(120, 120, java.awt.Image.SCALE_SMOOTH);
                            icon = new ImageIcon(scaled);
                            chatArea.setCaretPosition(doc.getLength());
                            chatArea.insertIcon(icon);
                            doc.insertString(doc.getLength(), "\n", null);
                        } catch (Exception e) {
                            doc.insertString(doc.getLength(), "[图片]显示失败\n", null);
                        }
                    } else {
                        doc.insertString(doc.getLength(), "[图片]未找到\n", null);
                    }
                } else {
                    doc.insertString(doc.getLength(), message + "\n", null);
                }
                // 自动滚动到底部
                chatArea.setCaretPosition(doc.getLength());
            } catch (Exception e) {
                // 忽略异常
            }
        });
    }
    
    public String getGroupName() {
        return groupName;
    }
} 