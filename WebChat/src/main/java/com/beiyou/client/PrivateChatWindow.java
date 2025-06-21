package com.beiyou.client;

import javax.swing.*;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

// 私聊窗口类，每个用户唯一，布局与主界面一致
public class PrivateChatWindow extends JFrame {
    // 聊天显示区
    private JTextPane chatArea;
    // 输入框
    private JTextField inputField;
    // 发送按钮
    private JButton sendButton;
    // 发送图片按钮
    private JButton sendImageButton;
    // 上传文件按钮
    private JButton uploadFileButton;
    // 下载文件按钮
    private JButton downloadFileButton;
    // 文件列表模型
    private DefaultListModel<String> fileListModel;
    // 文件列表
    private JList<String> fileList;
    // 文件选择器
    private JFileChooser fileChooser;
    // 当前下载文件名
    private String currentDownloadFile;
    // 目标用户
    private String targetUser;
    // 当前用户名
    private String currentUsername;
    // 消息处理器
    private ClientMessageHandler messageHandler;
    // 主窗口引用
    private ChatClientGUI mainWindow;

    // 构造方法，初始化私聊窗口
    public PrivateChatWindow(String targetUser, String currentUsername, ClientMessageHandler messageHandler, ChatClientGUI mainWindow) {
        this.targetUser = targetUser;
        this.currentUsername = currentUsername;
        this.messageHandler = messageHandler;
        this.mainWindow = mainWindow;

        setTitle("私聊 - " + targetUser);
        setSize(800, 500);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        fileChooser = new JFileChooser(new java.io.File("src/main/resources/Download"));

        initComponents();
        setupEventHandlers();
        loadPrivateChatHistory();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (mainWindow != null) {
                    mainWindow.privateWindows.remove(targetUser);
                }
            }
        });
    }

    // 初始化UI组件
    private void initComponents() {
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        JScrollPane chatScroll = new JScrollPane(chatArea);

        inputField = new JTextField();
        sendButton = new JButton("发送");
        sendImageButton = new JButton("发送图片");
        uploadFileButton = new JButton("上传文件");
        downloadFileButton = new JButton("下载文件");

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(inputField, BorderLayout.CENTER);
        JPanel sendBtnPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        sendBtnPanel.add(sendButton);
        sendBtnPanel.add(sendImageButton);
        inputPanel.add(sendBtnPanel, BorderLayout.EAST);

        fileListModel = new DefaultListModel<>();
        fileList = new JList<>(fileListModel);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane fileListScroll = new JScrollPane(fileList);
        fileListScroll.setPreferredSize(new Dimension(180, 100));

        JPanel filePanelRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filePanelRow1.add(uploadFileButton);
        filePanelRow1.add(downloadFileButton);

        JPanel filePanel = new JPanel(new BorderLayout());
        JPanel fileButtonPanel = new JPanel(new GridLayout(2, 1));
        fileButtonPanel.add(filePanelRow1);
        fileButtonPanel.add(fileListScroll, BorderLayout.CENTER);
        filePanel.add(fileButtonPanel, BorderLayout.NORTH);
        filePanel.setBorder(BorderFactory.createTitledBorder("私聊文件"));
        filePanel.setPreferredSize(new Dimension(200, 180));

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(filePanel, BorderLayout.CENTER);

        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.add(chatScroll, BorderLayout.CENTER);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);
        chatPanel.add(filePanel, BorderLayout.EAST);

        add(chatPanel, BorderLayout.CENTER);
    }

    // 设置事件监听器
    private void setupEventHandlers() {
        sendButton.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());
        sendImageButton.addActionListener(e -> sendImage());
        uploadFileButton.addActionListener(e -> uploadFile());
        downloadFileButton.addActionListener(e -> downloadSelectedFile());
        fileList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    downloadSelectedFile();
                }
            }
        });
    }

    // 发送文本消息
    private void sendMessage() {
        String msg = inputField.getText().trim();
        if (!msg.isEmpty()) {
            messageHandler.sendPrivateMessage(targetUser, msg);
            inputField.setText("");
        }
    }

    // 追加消息到聊天区
    public void appendMessage(String from, String to, String content) {
        SwingUtilities.invokeLater(() -> {
            try {
                StyledDocument doc = chatArea.getStyledDocument();
                if (content.startsWith("[图片]:")) {
                    String prefix = from + ":";
                    String imgInfo = content.substring(5);
                    String filename, dir;
                    if (imgInfo.contains("@")) {
                        String[] arr = imgInfo.split("@");
                        filename = arr[0];
                        dir = arr[1];
                    } else {
                        filename = imgInfo;
                        dir = from.compareTo(to) < 0 ? from + "_" + to : to + "_" + from;
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
                    doc.insertString(doc.getLength(), from + ": " + content + "\n", null);
                }
                chatArea.setCaretPosition(doc.getLength());
            } catch (Exception e) {}
        });
    }

    // 加载与目标用户的私聊历史
    private void loadPrivateChatHistory() {
        List<String> history = com.beiyou.server.util.MessageLogger.readUserHistory(currentUsername);
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
                                appendMessage(from, userParts[1], content);
                            } else {
                                appendMessage(from, userParts[1], content);
                            }
                        }
                    } catch (Exception e) {
                        // fallback
                        try {
                            StyledDocument doc = chatArea.getStyledDocument();
                            doc.insertString(doc.getLength(), line + "\n", null);
                        } catch (Exception ex) {}
                    }
                }
            }
        }
    }

    // 发送图片
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
                messageHandler.sendMessage("/imgsend " + targetUser + " " + filename + " " + filesize + " private");
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

    // 上传文件
    private void uploadFile() {
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            java.io.File file = fileChooser.getSelectedFile();
            String filename = file.getName();
            long filesize = file.length();
            try {
                messageHandler.sendMessage("/uploadfile " + targetUser + " " + filename + " " + filesize);
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
    }

    // 刷新文件列表
    private void refreshFileList() {
        messageHandler.sendMessage("/filelistfile " + targetUser);
    }

    // 下载选中文件
    private void downloadSelectedFile() {
        String filename = fileList.getSelectedValue();
        if (filename == null) {
            JOptionPane.showMessageDialog(this, "请选择要下载的文件");
            return;
        }
        currentDownloadFile = filename;
        messageHandler.sendMessage("/downloadfile " + targetUser + " " + filename);
    }
} 