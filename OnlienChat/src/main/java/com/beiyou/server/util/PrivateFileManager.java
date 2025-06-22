package com.beiyou.server.util;

import java.io.*;

public class PrivateFileManager {
    private static final String BASE_DIR = "src/main/resources/private_files/";

    public static File getPrivateDir(String userA, String userB) {
        String dirName = userA + "_" + userB;
        File dir = new File(BASE_DIR + dirName);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static void saveFile(String userA, String userB, String filename, InputStream in, long filesize) throws IOException {
        File dir = getPrivateDir(userA, userB);
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

    public static File getFile(String userA, String userB, String filename) {
        File dir = getPrivateDir(userA, userB);
        return new File(dir, filename);
    }

    public static String[] listFiles(String userA, String userB) {
        File dir = getPrivateDir(userA, userB);
        String[] files = dir.list();
        return files != null ? files : new String[0];
    }
} 