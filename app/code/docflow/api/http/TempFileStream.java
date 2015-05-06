package code.docflow.api.http;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Removes temp file, once stream is closed.
 */
public class TempFileStream extends FileInputStream {

    private File file;

    public TempFileStream(File file) throws FileNotFoundException {
        super(file);
        this.file = file;
    }

    @Override
    public void close() throws IOException {
        super.close();
        file.delete();
    }
}
