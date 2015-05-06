package code.docflow.api;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.controlflow.Result;
import code.docflow.model.Action;
import code.docflow.model.DocType;
import code.docflow.model.Field;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.docflow.users.CurrentUser;
import code.docflow.utils.FileUtil;
import com.google.common.base.Strings;
import models.DocflowFile;
import org.joda.time.DateTime;
import org.joda.time.DateTimeFieldType;
import play.Play;
import play.exceptions.UnexpectedException;
import play.vfs.VirtualFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class DocflowApiFile {

    public final static VirtualFile UPLOAD_PATH = VirtualFile.fromRelativePath(Play.configuration.getProperty("files.path", "/files"));

    public static DocflowFile _persistFile(final File file, final String fileTitle, final Result result) {
        checkNotNull(file, "file");
        checkArgument(file.exists(), "file must exist");
        checkNotNull(result, "result");

        // Copy file to files repository and create new DocflowFile document
        DocflowFile res = new DocflowFile();
        DateTime now = DateTime.now();
        final String extension = FileUtil.extension(file.getName());
        res.title = !Strings.isNullOrEmpty(fileTitle) ? (fileTitle + extension) : file.getName();
        res.filename = String.format("%04d/%02d/%s%s",
                now.get(DateTimeFieldType.year()),
                now.get(DateTimeFieldType.monthOfYear()),
                UUID.randomUUID().toString(),
                extension);
        final VirtualFile dstFile = UPLOAD_PATH.child(res.filename);
        dstFile.getRealFile().getParentFile().mkdirs();
        try {
            FileUtil.copyFile(file, dstFile.getRealFile());
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
        res.blocked = false;
        res.save();

        return res;
    }

    public static File _getFile(final DocflowFile docflowFile, final Result result) {
        checkNotNull(docflowFile, "docflowFile");
        checkNotNull(result, "result");
        return UPLOAD_PATH.child(docflowFile.filename).getRealFile();
    }
}
