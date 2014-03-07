package code.utils;

import java.io.File;

public class FileUtil {

    public static String extension(String fullPath) {
        int dot = fullPath.lastIndexOf('.');
        int sep = fullPath.lastIndexOf(File.separatorChar);
        if (dot == -1 || dot < sep)
            return "";
        return fullPath.substring(dot + 1);
    }

    public static String filename(String fullPath) {
        int dot = fullPath.lastIndexOf('.');
        int sep = fullPath.lastIndexOf(File.separatorChar);
        if (dot == -1 || dot < sep)
            dot = fullPath.length();
        return fullPath.substring(sep + 1, dot);
    }

    public static String path(String fullPath) {
        int sep = fullPath.lastIndexOf(File.separatorChar);
        if (sep == -1)
            return fullPath;
        return fullPath.substring(0, sep);
    }
}