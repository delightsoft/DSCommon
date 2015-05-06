package code.docflow.jsonBinding;

import code.docflow.controlflow.Result;
import code.docflow.DocflowConfig;
import code.docflow.model.DocType;
import code.docflow.docs.Document;
import code.docflow.docs.DocumentPersistent;
import code.docflow.types.DocumentRef;
import code.docflow.utils.TypeBuildersFactory;
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

    public Method fldResultGetter;

    public Method fldTextGetter;
    public Method fldTextSetter;
    public Method fldTextSourceGetter;

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
            result.addMsg(Messages.ClassMustBeDerivedFromEntityBase, type.getName());
        }

        try {
            fldSubjPolyRef = type.getField("subj");
            if (!DocumentRef.class.isAssignableFrom(fldSubjPolyRef.getType()))
               result.addMsg(Messages.FieldHasWrongTypeInClass, type.getName(), fldSubjPolyRef.getName(), long.class.getName());

            try {
                fldSubjGetter = type.getMethod("getSubj");
                fldSubjSetter = type.getMethod("setSubj", DocumentRef.class);
            } catch (NoSuchMethodException e) {
                throw new UnexpectedException(e);
            }
        } catch (NoSuchFieldException e) {
            // nothing
        }

        try {
            fldResultGetter = type.getMethod("getResult");
        } catch (NoSuchMethodException e) {
            // it's Ok
        }

        try {
            fldTextSetter = type.getMethod("setText", String.class);
        } catch (NoSuchMethodException e) {
            // it's Ok
        }

        try {
            fldTextGetter = type.getMethod("getText");
        } catch (NoSuchMethodException e) {
            // it's Ok
        }

        if (result.isError())
            throw new UnexpectedException(result.combineMessages());
    }


    public DocumentPersistent findById(long id) {
        checkState(DocumentPersistent.class.isAssignableFrom(type), "Must be DocumentPersistent or it's child");
        return (DocumentPersistent) super.findById(id);
    }

    public DocumentPersistent newRecord() {
        checkState(DocumentPersistent.class.isAssignableFrom(type), "Must be DocumentPersistent or it's child");
        return (DocumentPersistent) super.newRecord();
    }
}
