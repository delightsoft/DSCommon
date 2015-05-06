package code.docflow.api.http;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import static com.google.common.base.Preconditions.*;
import org.apache.commons.codec.net.URLCodec;
import play.exceptions.UnexpectedException;
import play.libs.MimeTypes;
import play.mvc.Http.Request;
import play.mvc.Http.Response;
import play.mvc.results.Result;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

/**
 * It's copy of {@link play.mvc.results.RenderBinary} with fix to send proper cyrrilic nameds fiels.
 */
public class DocflowRenderFile extends Result {

    private final File file;
    private final boolean isTempFile;
    private final boolean isInline;

    public DocflowRenderFile(File file, boolean isTempFile, boolean isInline) {
        this.file = file;
        this.isTempFile = isTempFile;
        this.isInline = isInline;
    }

    @Override
    public void apply(Request request, Response response) {
        response.contentType = MimeTypes.getContentType(file.getName());
        response.setHeader("Content-Disposition", isInline ? "inline" : "attachment");
        try {
//            if (isTempFile)
                response.setHeader("Content-Length", file.length() + "");
            response.direct = isTempFile ? new TempFileStream(file) : file;
        } catch (FileNotFoundException e) {
            throw new UnexpectedException(e);
        }
    }
}
