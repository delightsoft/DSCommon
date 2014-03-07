package code.docflow.yaml;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class FilePosition {
    public final String filename;
    public final int line;

    public FilePosition(String filename, int line) {
        this.filename = filename;
        this.line = line;
    }

    public String toString() {
        if (filename == null) {
            if (line > 0)
                return "Line " + line + ": ";
            return "";
        }
        if (line > 0)
            return filename + "(" + line + "): ";
        return filename + ": ";
    }
}
