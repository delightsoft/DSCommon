package code.jsonBinding;

import code.controlflow.Result;
import code.docflow.DocflowConfig;
import code.docflow.messages.GeneralMessages;
import code.docflow.model.DocType;
import code.models.Document;
import code.models.PersistentDocument;
import code.types.PolymorphicRef;
import code.utils.TypeBuildersFactory;
import play.exceptions.JavaExecutionException;
import play.exceptions.UnexpectedException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static com.google.common.base.Preconditions.checkState;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class RecordAccessor extends RecordAccessorCommon {

    public final DocType docType;

    public Method fldSubjSetter;
    public Method fldSubjGetter;

    public Field fldSubjRef;
    public Field fldSubjPolyRef;

    public static final TypeBuildersFactory<RecordAccessor> factory = new TypeBuildersFactory<RecordAccessor>() {
        public RecordAccessor newInstance(TypeDescription typeDesc) {
            checkState(typeDesc.parameters == null);
            return new RecordAccessor(typeDesc.type);
        }
    };

    protected RecordAccessor(final Class<?> type) {
        super(type);
        docType = DocflowConfig.getDocumentTypeByClass(type);
    }

    @Override
    protected void init() {
        final Result result = new Result();

        super.init(result);

        if (!Document.class.isAssignableFrom(type)) {
            result.addMsg(GeneralMessages.ClassMustBeDerivedFromEntityBase, type.getName());
        }

        try {
            fldSubjRef = type.getField("subj");
            if (!Document.class.isAssignableFrom(fldSubjRef.getType())) {
                if (!fldSubjRef.getType().isAssignableFrom(PolymorphicRef.class))
                   result.addMsg(GeneralMessages.FieldHasWrongTypeInClass, type.getName(), fldSubjRef.getName(), long.class.getName());
                fldSubjPolyRef = fldSubjRef;
                fldSubjRef = null;
            }

            try {
                fldSubjGetter = type.getMethod("getSubj");
                fldSubjSetter = type.getMethod("setSubj", fldSubjRef != null ? fldSubjRef.getType() : PolymorphicRef.class);
            } catch (NoSuchMethodException e) {
                throw new JavaExecutionException(e);
            }

        } catch (NoSuchFieldException e) {
            // nothing
        }

        if (result.isError())
            throw new UnexpectedException(result.combineMessages());
    }


    public PersistentDocument findById(long id) {
        checkState(PersistentDocument.class.isAssignableFrom(type), "Must be OneStateDocumentBase or it's child");
        return (PersistentDocument) super.findById(id);
    }

    public PersistentDocument newRecord() {
        checkState(PersistentDocument.class.isAssignableFrom(type), "Must be OneStateDocumentBase or it's child");
        return (PersistentDocument) super.newRecord();
    }

    public String getFullId(PersistentDocument doc) {
        if (doc.isPersistent())
            return docType.name + "@" + getId(doc);
        return docType.name;
    }

    /**
     * Returns polymorphic reference to given document.  Document must be that same type as RecordAccessor was created for.
     */
    public PolymorphicRef getPolymorphicRef(Document doc) {
        if (doc.isPersistent())
            return new PolymorphicRef(docType, getId(doc));
        return new PolymorphicRef(docType, 0);
    }
}
