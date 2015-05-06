package code.docflow.utils;

import static com.google.common.base.Preconditions.*;
import com.google.common.base.Strings;
import org.apache.commons.io.FileUtils;
import play.Play;
import play.exceptions.UnexpectedException;
import play.vfs.VirtualFile;

import java.io.*;
import java.text.DecimalFormat;
import java.util.Arrays;

public class FileUtil {

    /**
     * Returns file extension with leading dot.
     */
    public static String extension(String fullPath) {
        int dot = fullPath.lastIndexOf('.');
        int sep = fullPath.lastIndexOf(File.separatorChar);
        if (dot == -1 || dot < sep)
            return "";
        return fullPath.substring(dot);
    }

    /**
     * Returns file extension with leading dot.
     */
    public static String extension(VirtualFile file) {
        return extension(name(file));
    }

    /**
     * Return name of the file, without extension.
     */
    public static String filename(String fullPath) {
        int dot = fullPath.lastIndexOf('.');
        int sep = fullPath.lastIndexOf(File.separatorChar);
        if (dot == -1 || dot < sep)
            dot = fullPath.length();
        return fullPath.substring(sep + 1, dot);
    }

    /**
     * Return name of the file, without extension.
     */
    public static String filename(VirtualFile file) {
        return filename(name(file));
    }

    /**
     * Return name of the file, with extension.
     */
    public static String filenameWithextension(String fullPath) {
        int sep = fullPath.lastIndexOf(File.separatorChar);
        return fullPath.substring(sep + 1);
    }

    /**
     * Return name of the file, without extension.
     */
    public static String filenameWithextension(VirtualFile file) {
        return filenameWithextension(name(file));
    }

    /**
     * Returns clear path without filename and trailing slash.
     */
    public static String path(String fullPath) {
        int sep = fullPath.lastIndexOf(File.separatorChar);
        if (sep == -1)
            return fullPath;
        return fullPath.substring(0, sep);
    }

    /**
     * Returns clear path without filename and trailing slash.
     */
    public static String path(VirtualFile file) {
        return path(name(file));
    }

    public static final String PROP_LINE_SEPARATOR = "line.separator";

    public static class OldLF {
        private String oldLF;

        public OldLF(String oldLF) {
            this.oldLF = oldLF;
        }

        public void restore() {
            System.setProperty(PROP_LINE_SEPARATOR, oldLF);
        }
    }

    public static OldLF setUnixLF() {
        OldLF res = new OldLF(System.getProperty(PROP_LINE_SEPARATOR));
        System.setProperty(PROP_LINE_SEPARATOR, "\n");
        return res;
    }

    /**
     * Copies either one file or whole dir to given destination.
     */
    public static void copy(File srcFileOrDir, File dstDir) throws IOException {
        checkNotNull(srcFileOrDir);
        checkNotNull(dstDir);
        if (srcFileOrDir.isDirectory())
            copyDirectory(srcFileOrDir, dstDir);
        else
            copyFile(srcFileOrDir, dstDir);
    }

    /**
     * Copies fields from source directory to destination directory.
     */
    public static void copyDirectory(File srcDir, File dstDir) throws IOException {
        copyDirectory(srcDir, dstDir, null);
    }

    /**
     * Copies fields from source directory to destination directory.  Optional files filter may be specified.
     */
    public static void copyDirectory(File srcDir, File dstDir, FileFilter fileFilter) throws IOException {
        File nextDirectory = new File(dstDir, srcDir.getName());
        if (!nextDirectory.exists() && !nextDirectory.mkdirs()) {// create the directory if necessary...
            Object[] filler = {nextDirectory.getAbsolutePath()};
            String message = "Dir Copy Failed";
            throw new IOException(message);
        }
        File[] files = srcDir.listFiles();
        for (File file : files)
            if (fileFilter == null || fileFilter.accept(file))
                if (file.isDirectory())
                    copyDirectory(file, nextDirectory, fileFilter);
                else
                    copyFile(file, nextDirectory);
    }

    /**
     * Copy given field to given folder.  File name remains the same.
     */
    public static void copyFile(File srcFile, File dstDir) throws IOException {
        // what we really want to do is create a file with the same name in that dir
        if (dstDir.isDirectory())
            dstDir = new File(dstDir, srcFile.getName());
        FileInputStream input = new FileInputStream(srcFile);
        copyFile(input, dstDir);
    }

    /**
     * Copies input stream to given field.
     */
    public static void copyFile(InputStream input, File dstFile) throws IOException {
        checkNotNull(input, "input");
        checkNotNull(dstFile, "destination");
        OutputStream output = null;
        try {
            output = new FileOutputStream(dstFile);
            byte[] buffer = new byte[16 * 1024];
            int bytesRead = input.read(buffer);
            while (bytesRead >= 0) {
                output.write(buffer, 0, bytesRead);
                bytesRead = input.read(buffer);
            }
        } finally {
            input.close();
            if (output != null)
                output.close();
        }
    }

    public static void removeDirectory(File dstDir) {
        emptyDirectory(dstDir);
        dstDir.delete();
    }

    public static void emptyDirectory(File dstDir) {
        final File[] files = dstDir.listFiles();
        if (files != null)
            for (File file : files)
                if (file.isDirectory())
                    removeDirectory(file);
                else
                    file.delete();
    }

    public static String name(VirtualFile file) {
        try {
            return file.getRealFile().getCanonicalPath();
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    public static void closeQuietly(Reader s) {
        try {
            s.close();
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    public static void closeQuietly(BufferedReader s) {
        try {
            s.close();
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    public static void closeQuietly(Writer s) {
        try {
            s.close();
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    public static void closeQuietly(OutputStream s) {
        try {
            s.close();
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    public static void closeQuietly(InputStream s) {
        try {
            s.close();
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }


    private static DecimalFormat format = new DecimalFormat("##########");

    static {
        format.setMinimumIntegerDigits(10);
        format.setGroupingUsed(false);
    }
    private static long count = 0;

    private static synchronized long getCountLocal() {
        return count++;
    }
    public static ThreadLocal<File> tempFolder = new ThreadLocal<File>();

    // Code is taken from play.data.parsing.TempFilePlugin

    public static File createTempFolder(String type) {

        checkArgument(!Strings.isNullOrEmpty(type), "type");

        if (Play.tmpDir == null || Play.readOnlyTmp)
            return null;

        if (tempFolder.get() == null) {
            File file = new File(Play.tmpDir +
                    File.separator + type + File.separator +
                    System.currentTimeMillis() + "_" + format.format(getCountLocal()));
            file.mkdirs();
            tempFolder.set(file);
        }
        return tempFolder.get();
    }

    public static void removeTempFolder() {
        File file = tempFolder.get();
        if (file != null) {
            tempFolder.remove();
            try {
                FileUtils.deleteDirectory(file);
            } catch (IOException e) {
                // unexpected
            }
        }
    }

    public static final int BLOCK_SIZE = 32 * 1024;

    /**
     * Saves file only if content was changed or it's a new file.  This eliminates unnecessary
     * code recompilation in Play framework dev-mode.
     */
    public static void saveFileIfChanged(VirtualFile dst, ByteArrayOutputStream src) {
        checkNotNull(src, "src");
        checkNotNull(dst, "dst");
        try {
            src.flush();
            src.close();
            final byte[] newContent = src.toByteArray();
            if (dst.exists())
                try {
                    if (newContent.length == dst.length()) {
                        final byte[] oldContent = new byte[newContent.length];
                        final InputStream fin = dst.inputstream();
                        try {
                            fin.read(oldContent);
                        } finally {
                            fin.close();
                        }
                        if (Arrays.equals(newContent, oldContent))
                            return; // content is the same as before - nothing to update
                    }
                } catch (FileNotFoundException e) {
                    dst.getRealFile().mkdirs();
                }
            // create or update file
            final OutputStream fout = dst.outputstream();
            try {
                fout.write(newContent);
                fout.flush();
            } finally {
                fout.close();
            }
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }
}