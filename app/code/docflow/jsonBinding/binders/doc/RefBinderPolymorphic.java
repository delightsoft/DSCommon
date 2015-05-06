package code.docflow.jsonBinding.binders.doc;

import code.docflow.action.DocumentUpdateImpl;
import code.docflow.controlflow.Result;
import code.docflow.model.FieldPolymorphicReference;
import code.docflow.docs.Document;
import code.docflow.types.DocumentRef;
import com.fasterxml.jackson.databind.JsonNode;
import docflow.DocflowMessages;
import models.DocflowFile;
import play.exceptions.UnexpectedException;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class RefBinderPolymorphic extends RefBinderBase {

    private FieldPolymorphicReference field;

    public RefBinderPolymorphic(Field fld, Method getter, Method setter, String fldName) {
        super(fld, getter, setter, fldName);
    }

    @Override
    public void setField(code.docflow.model.Field field) {
        super.setField(field);
        this.field = (FieldPolymorphicReference) field;
    }

    @Override
    protected boolean idRequired() {
        return true;
    }

    @Override
    protected DocumentRef checkAndFixType(DocumentRef v, String fldPrefix, JsonNode node, Result result) {
        if (field == null || field.refDocumentsNames == null || field.refDocumentsNames.contains(v.type.toUpperCase()))
            return v;
        result.addMsg(DocflowMessages.error_ValidationFieldIncorrectValue_2, fldPrefix + fldName, node.asText());
        return null;
    }

    @Override
    protected DocumentRef retrieveDocFromField(Object obj) {
        try {
            final DocumentRef val = (DocumentRef) getter.invoke(obj);
            return val == null ? DocumentRef.NULL : val;
        } catch (IllegalAccessException e) {
            throw new UnexpectedException(e);
        } catch (InvocationTargetException e) {
            throw new UnexpectedException(e);
        }
    }

    @Override
    protected void assignDocToField(Object obj, DocumentRef newVal, Document newDoc, DocumentRef oldVal, DocumentRef docId, DocumentUpdateImpl update) {
        try {
            if (newDoc != null && newDoc instanceof DocflowFile) {
                newDoc = RefBinderStrict.cloneDocflowFile(obj, newVal, newDoc, field, docId, update);
                newVal = newDoc._ref();
            }
            setter.invoke(obj, newVal);
        } catch (IllegalAccessException e) {
            throw new UnexpectedException(e);
        } catch (InvocationTargetException e) {
            throw new UnexpectedException(e);
        }
    }
}
