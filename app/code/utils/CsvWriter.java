package code.utils;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import play.exceptions.JavaExecutionException;

import java.io.IOException;
import java.io.Writer;

public class CsvWriter {

    public static final char TAB = '\t';
    public static final char QUOTA = '\"';

    private boolean firstValue = true;
    private Writer writer;

    public CsvWriter(Writer writer) {
        this.writer = writer;
    }

    public void close() {
        try {
            writer.flush();
            writer.close();
        } catch (IOException e) {
            throw new JavaExecutionException(e);
        }
    }

    public void newLine() {
        try {
            writer.write("\r\n");
            firstValue = true;
        } catch (IOException e) {
            throw new JavaExecutionException(e);
        }
    }

    public void out(String value) {
        try {
            outDelimiter();
            if (value.indexOf(QUOTA) >= 0) {
                writer.write(QUOTA);
                writer.write(value.replace("\"", "\"\""));
                writer.write(QUOTA);
            } else
                writer.write(value);
        } catch (IOException e) {
            throw new JavaExecutionException(e);
        }
    }

    private void outDelimiter() throws IOException {
        if (firstValue)
            firstValue = false;
        else
            writer.write(TAB);
    }

    public void out(double number) {
        try {
            outDelimiter();
            writer.write(Double.toString(number));
        } catch (IOException e) {
            throw new JavaExecutionException(e);
        }
    }
}
