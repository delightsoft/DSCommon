package code.docflow.types;

import code.docflow.DocflowConfig;
import code.docflow.compiler.enums.CrudActions;
import code.docflow.docs.DocumentPersistent;
import code.docflow.jsonBinding.RecordAccessor;
import code.docflow.model.DocType;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.docflow.users.CurrentUser;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Transient;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
@Embeddable
public final class DocumentRef implements Comparable<DocumentRef> {

    public static final DocumentRef NULL = new DocumentRef();

    public static class DocumentAccessException extends Exception {

        public enum Type {
            UNKNOWN_DOCUMENT_TYPE("Unknown document type"),
            NO_ACCESS_TO_DOCUMENT_TYPE("No access to document type"),
            NO_ACCESS_TO_DOCUMENT("No access to document instance"),
            NO_SUCH_DOCUMENT("Document does not exist");

            private String text;

            Type(String text) {
                this.text = text;
            }

            @Override
            public String toString() {
                return text;
            }
        }

        public Type type;
        public DocumentRef ref;

        public DocumentAccessException(Type type, DocumentRef ref) {
            this.type = type;
            this.ref = ref;
        }

        @Override
        public String toString() {
            return "Document " + ref.toString() + ": " + type.toString();
        }
    }

    @Column(length = 100, nullable = true)
    public String type;

    public long id;

    @Transient
    private DocumentPersistent doc;

    public DocumentRef(DocType type) {
        this.type = type.name;
        this.id = 0;
    }

    public DocumentRef(DocType type, long id) {
        Preconditions.checkArgument(id >= 0, "docId: %s", id);
        this.type = type.name;
        this.id = id;
    }

    public DocumentRef(DocumentPersistent doc, String type, long id) {
        this.doc = doc;
        this.type = type;
        this.id = id;
    }

    public DocumentRef(String type, long id) {
        // TODO: Validate type
        Preconditions.checkArgument(!Strings.isNullOrEmpty(type), "docType is null or empty");
        this.type = type;
        this.id = id;
    }

    private DocumentRef() {
        this.type = "";
        this.id = 0;
    }

    /**
     * @throws IllegalArgumentException Argument 'text' do not complain to format '[type]#[id]'.
     */
    public static DocumentRef parse(String docOrDocTypeId) {
        if (docOrDocTypeId == null || docOrDocTypeId.length() == 0)
            return NULL;
        try {
            final int at = docOrDocTypeId.indexOf('@');
            if (at > 0)
                return new DocumentRef(docOrDocTypeId.substring(0, at), Long.parseLong(docOrDocTypeId.substring(at + 1)));
            else
                return new DocumentRef(docOrDocTypeId, 0);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(docOrDocTypeId);
        }
    }

    /**
     * Return existing or new document with appropriate rights check.
     */
    public <T extends DocumentPersistent> T safeGetDocument() throws DocumentAccessException {

        if (doc != null)
            return (T) doc;

        if (type == null)
            return null;

        final DocType docType = DocflowConfig.instance.documents.get(type.toUpperCase());
        if (docType == null)
            throw new DocumentAccessException(DocumentAccessException.Type.UNKNOWN_DOCUMENT_TYPE, this);

        final boolean inActionScope = CurrentUser.getInstance().inActionScope;

        if (!inActionScope) {
            final DocumentAccessActionsRights fullRights = RightsCalculator.instance.calculate(docType, CurrentUser.getInstance().getUserRoles());
            if (!fullRights.actionsMask.get(CrudActions.RETRIEVE.index))
                throw new DocumentAccessException(DocumentAccessException.Type.NO_ACCESS_TO_DOCUMENT_TYPE, this);
        }

        final RecordAccessor accessor = docType.jsonBinder.recordAccessor;
        doc = (id != 0) ? accessor.findById(id) : accessor.newRecord();
        if (doc == null)
            throw new DocumentAccessException(DocumentAccessException.Type.NO_SUCH_DOCUMENT, this);

        if (!inActionScope) {
            final DocumentAccessActionsRights rights = RightsCalculator.instance.calculate(doc, CurrentUser.getInstance());
            if (!rights.actionsMask.get(CrudActions.RETRIEVE.index))
                throw new DocumentAccessException(DocumentAccessException.Type.NO_ACCESS_TO_DOCUMENT, this);
        }

        return (T) doc;
    }

    public static <T extends DocumentPersistent> T getDocumentByFullId(String fullId, Class<T> dictClass) {
        if (!Strings.isNullOrEmpty(fullId)) {
            DocumentRef ref = DocumentRef.parse(fullId);
            if (ref != NULL) {
                DocType docType = DocflowConfig.getDocumentTypeByClass(dictClass);
                Preconditions.checkState(docType != null);
                if (ref.type.equalsIgnoreCase(docType.name)) {
                    return (T) ref.getDocumentUnsafe();
                }
            }
        }
        return null;
    }

    // TODO: Remove after 2015-03-01
    @Deprecated
    /**
     * @deprecated
     * This method was renamed to @DocumentRef#getDocument But since it in use in old queries, it will stay around for awail.
     */
    public <T extends DocumentPersistent> T getDocumentUnsafe() {
        return getDocument();
    }

    /**
     * Returns existing or new document without rights check.
     */
    public <T extends DocumentPersistent> T getDocument() {

        if (doc != null)
            return (T) doc;

        if (type == null)
            return null;

        final DocType docType = DocflowConfig.instance.documents.get(type.toUpperCase());
        if (docType == null)
            return null;

        final RecordAccessor accessor = docType.jsonBinder.recordAccessor;
        return (T) (doc = ((id != 0) ? accessor.findById(id) : accessor.newRecord()));
    }

    public boolean isNew() {
        return id == 0;
    }

    @Override
    public String toString() {
        return (id != 0) ? (type + "@" + id) : type;
    }

    @Override
    public int compareTo(DocumentRef o) {
        int r = type.compareTo(type);
        if (r == 0)
            r = id < o.id ? -1 : (id == o.id ? 0 : 1);
        return r;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DocumentRef that = (DocumentRef) o;

        if (type != null ? !type.equals(that.type) : that.type != null) return false;
        if (type != null && id != that.id) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        if (type != null)
            result = 31 * result + (int) (id ^ (id >>> 32));
        return result;
    }
}
