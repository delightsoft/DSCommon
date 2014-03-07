package code;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class DocumentId {

    public final String docType;
    public final Long docId;

    public DocumentId(String docType, Long docId) {

        Preconditions.checkArgument(!Strings.isNullOrEmpty(docType), "docType is null or empty");
        Preconditions.checkArgument(docId == null || docId > 0, "docId: %s", docId);

        this.docType = docType;
        this.docId = docId;
    }

    public static DocumentId parse(String id) {
        try {
            final int at = id.indexOf('@');
            if (at > 0)
                return new DocumentId(id.substring(0, at), Long.parseLong(id.substring(at + 1)));
            else
                return new DocumentId(id, null);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(id);
        }
    }

    @Override
    public String toString() {
        return docId != null ? docType + "@" + docId : docType;
    }
}
