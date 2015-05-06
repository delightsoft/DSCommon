package code.docflow.api.http;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.utils.FileUtil;
import com.google.common.base.Strings;
import models.DocflowFile;
import play.Play;
import play.exceptions.UnexpectedException;

import java.io.*;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class ResultFile {

    private File file;
    private String downloadFileId;

    private DocflowFile docflowFile;

    public static final String FILES_TEMP_DIR = Play.tmpDir == null || Play.readOnlyTmp ? "" :
            Play.tmpDir + File.separator + "files";

    public static final String KEY_TEMP_DIR = Play.tmpDir == null || Play.readOnlyTmp ? "" :
            Play.tmpDir + File.separator + "keys";

    public static final String KEY_FILE_EXT = ".txt";

    static {
        new File(FILES_TEMP_DIR).mkdirs();
        new File(KEY_TEMP_DIR).mkdirs();
    }

    public ResultFile(DocflowFile docflowFile) {
        final UUID key = UUID.randomUUID();
        final File keyFile = new File(KEY_TEMP_DIR, key + KEY_FILE_EXT);
        try {
            final PrintWriter out = new PrintWriter(keyFile);
            try {
                out.print(docflowFile._fullId());
                out.flush();
            } finally {
                out.close();
            }
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
        this.downloadFileId = docflowFile.title + "?doc=" + docflowFile._fullId() + "&key=" + key;
    }

    public ResultFile(File file, String title) {
        checkNotNull(file, "file");
        checkArgument(file.exists(), "file must exist");
        checkArgument(!Strings.isNullOrEmpty(title), "title");

        final File dstFile = new File(FILES_TEMP_DIR, UUID.randomUUID().toString() + FileUtil.extension(file.getName()));
        dstFile.getParentFile().mkdirs();
        try {
            FileUtil.copy(file, dstFile);
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }

        this.file = dstFile;
        this.downloadFileId = title + FileUtil.extension(dstFile.getName()) + "?file=" + FileUtil.filename(dstFile.getName());
    }

    /**
     * Returns temporary file ID, to be used as regular persistent document downloadFileId.
     */
    public String _fullId() {
        return downloadFileId;
    }

    @Override
    public String toString() {
        return _fullId();
    }
}
