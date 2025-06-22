package com.beiyou.server.util;

import java.io.*;

public class GroupFileManager {
    private static final String BASE_DIR = "src/main/resources/group_files/";

    public static File getGroupFileDir(String groupName) {
        File dir = new File(BASE_DIR + groupName);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static void saveFile(String groupName, String filename, InputStream in, long filesize) throws IOException {
        File dir = getGroupFileDir(groupName);
        File file = new File(dir, filename);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buffer = new byte[4096];
            long remaining = filesize;
            while (remaining > 0) {
                int read = in.read(buffer, 0, (int)Math.min(buffer.length, remaining));
                if (read == -1) break;
                fos.write(buffer, 0, read);
                remaining -= read;
            }
        }
    }

    public static boolean fileExists(String groupName, String filename) {
        File dir = getGroupFileDir(groupName);
        File file = new File(dir, filename);
        return file.exists();
    }

    public static void sendFile(String groupName, String filename, OutputStream out) throws IOException {
        File dir = getGroupFileDir(groupName);
        File file = new File(dir, filename);
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
    }

    public static String[] listFiles(String groupName) {
        File dir = getGroupFileDir(groupName);
        String[] files = dir.list();
        return files != null ? files : new String[0];
    }
} 