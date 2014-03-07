package code.types;

import code.docflow.DocflowConfig;
import code.docflow.model.DocType;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.jsonBinding.RecordAccessor;
import code.models.PersistentDocument;
import code.users.CurrentUser;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import javax.persistence.Column;
import javax.persistence.Embeddable;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
@Embeddable
public final class PolymorphicRef implements Comparable<PolymorphicRef> {

    public static class DocumentAccessException extends Exception {

        public enum Type {
            UNKNOWN_DOCUMENT_TYPE,
            NO_ACCESS_TO_DOCUMENT_TYPE,
            NO_ACCESS_TO_DOCUMENT,
            NO_SUCH_DOCUMENT
        }

        public Type type;
        public PolymorphicRef ref;

        public DocumentAccessException(Type type, PolymorphicRef ref) {
            this.type = type;
            this.ref = ref;
        }

        @Override
        public String toString() {
            return "DocumentAccessException{type=" + type + ", ref=" + ref + '}';
        }
    }

    @Column(length = 100, nullable = true)
    public String type;

    public long id;

    public PolymorphicRef(DocType type, long id) {
        this.type = type.name;
        this.id = id;
    }

    public PolymorphicRef(String type, long id) {
        this.type = type;
        this.id = id;
    }

    public static final PolymorphicRef Null = new PolymorphicRef((String) null, 0);

    /**
     * @throws IllegalArgumentException Argument 'text' do not complain to format '[type]#[id]'.
     */
    public static PolymorphicRef parse(String text) {
        if (text == null || text.length() == 0)
            return Null;
        final int p = text.indexOf('@');
        if (p > 0) {
            final String type = text.substring(0, p);
            final String idStr = text.substring(p + 1);
            try {
                final long id = Long.parseLong(idStr);
                return new PolymorphicRef(type, id);
            } catch (NumberFormatException e) {
            }
        }
        else {
            return new PolymorphicRef(text, 0);
        }
        throw new IllegalArgumentException(text);
    }

    /**
     * Return existing or new document with appropriate rights check.
     */
    public <T extends PersistentDocument> T getDocument() throws DocumentAccessException {

        if (type == null)
            return null;

        final DocType docType = DocflowConfig.instance.documents.get(type.toUpperCase());
        if (docType == null)
            throw new DocumentAccessException(DocumentAccessException.Type.UNKNOWN_DOCUMENT_TYPE, this);

        final DocumentAccessActionsRights fullRights = RightsCalculator.instance.calculate(docType, CurrentUser.getInstance().getUserRoles());
        if (!fullRights.actionsMask.get(DocflowConfig.ImplicitActions.RETRIEVE.index))
            throw new DocumentAccessException(DocumentAccessException.Type.NO_ACCESS_TO_DOCUMENT_TYPE, this);

        final RecordAccessor accessor = docType.jsonBinder.recordAccessor;
        final PersistentDocument doc = (id != 0) ? accessor.findById(id) : accessor.newRecord();
        if (doc == null)
            throw new DocumentAccessException(DocumentAccessException.Type.NO_SUCH_DOCUMENT, this);

        final DocumentAccessActionsRights rights = RightsCalculator.instance.calculate(doc, CurrentUser.getInstance());
        if (!rights.actionsMask.get(DocflowConfig.ImplicitActions.RETRIEVE.index))
            throw new DocumentAccessException(DocumentAccessException.Type.NO_ACCESS_TO_DOCUMENT, this);

        return (T) doc;
    }

    public static <T extends PersistentDocument> T getDocumentByFullId(String fullId, Class<T> dictClass) {
        if (!Strings.isNullOrEmpty(fullId)) {
            PolymorphicRef ref = PolymorphicRef.parse(fullId);
            if (ref != null) {
                DocType docType = DocflowConfig.getDocumentTypeByClass(dictClass);
                Preconditions.checkState(docType != null);
                if (ref.type.equalsIgnoreCase(docType.name)) {
                    return (T) ref.getDocumentUnsafe();
                }
            }
        }
        return null;
    }

    /**
     * Returns existing or new document without rights check.
     */
    public <T extends PersistentDocument> T getDocumentUnsafe() {

        if (type == null)
            return null;

        final DocType docType = DocflowConfig.instance.documents.get(type.toUpperCase());
        if (docType == null)
            return null;

        final RecordAccessor accessor = docType.jsonBinder.recordAccessor;
        return (T) ((id != 0) ? accessor.findById(id) : accessor.newRecord());
    }

    public boolean isNew() {
        return id == 0;
    }

    @Override
    public String toString() {
        return (id != 0) ? (type +  "@" + id) : type;
    }

    @Override
    public int compareTo(PolymorphicRef o) {
        int r = type.compareTo(type);
        if (r == 0)
            r = id < o.id ? -1 : (id == o.id ? 0 : 1);
        return r;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PolymorphicRef that = (PolymorphicRef) o;

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
