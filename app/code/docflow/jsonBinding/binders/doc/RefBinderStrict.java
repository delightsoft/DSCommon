package code.docflow.jsonBinding.binders.doc;

import code.docflow.DocflowConfig;
import code.docflow.action.Transaction;
import code.docflow.action.DocumentUpdateImpl;
import code.docflow.controlflow.Result;
import code.docflow.docs.Document;
import code.docflow.docs.DocumentPersistent;
import code.docflow.types.DocumentRef;
import com.fasterxml.jackson.databind.JsonNode;
import docflow.DocflowMessages;
import models.DocflowFile;
import play.exceptions.UnexpectedException;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class RefBinderStrict extends RefBinderBase {

    private final String docType;

    private code.docflow.model.Field field;

    public RefBinderStrict(Field fld, Method getter, Method setter, String fldName) {
        super(fld, getter, setter, fldName);
        docType = DocflowConfig.getDocumentTypeByClass(fld.getType()).name;
    }

    @Override
    public void setField(code.docflow.model.Field field) {
        super.setField(field);
        this.field = field;
    }

    @Override
    protected boolean idRequired() {
        return false;
    }

    @Override
    protected DocumentRef checkAndFixType(DocumentRef v, String fldPrefix, JsonNode node, Result result) {
        if (v == DocumentRef.NULL)
            return new DocumentRef(docType, 0);
        if (v.type.equalsIgnoreCase(docType))
            return v;
        result.addMsg(DocflowMessages.error_ValidationFieldIncorrectValue_2, fldPrefix + fldName, node.asText());
        return null;
    }

    @Override
    protected DocumentRef retrieveDocFromField(Object obj) {
        final DocumentPersistent doc;
        try {
            doc = (DocumentPersistent) getter.invoke(obj);
            return doc == null ? DocumentRef.NULL : DocumentRef.parse(doc._fullId());
        } catch (IllegalAccessException e) {
            throw new UnexpectedException(e);
        } catch (InvocationTargetException e) {
            throw new UnexpectedException(e);
        }
    }

    @Override
    protected void assignDocToField(Object obj, DocumentRef newVal, Document newDoc, DocumentRef oldVal, DocumentRef docId, DocumentUpdateImpl update) {
        try {
            if (newDoc instanceof DocflowFile)
                newDoc = cloneDocflowFile(obj, newVal, newDoc, field, docId, update);
            setter.invoke(obj, newDoc);
        } catch (IllegalAccessException e) {
            throw new UnexpectedException(e);
        } catch (InvocationTargetException e) {
            throw new UnexpectedException(e);
        }
    }

    public static Document cloneDocflowFile(Object obj, DocumentRef newVal, Document newDoc, code.docflow.model.Field field, DocumentRef docId, DocumentUpdateImpl update) {
        DocflowFile fileDoc = (DocflowFile) newDoc;
        if (field != null) { // a docflow document field - not just a field of arbitrary java class
            if (fileDoc.document.equals(DocumentRef.NULL)) { // it's not linked to a document field DocflowFile document
                DocumentPersistent documentPersistent = (DocumentPersistent) obj;
                setDocument(fileDoc, docId, (DocumentPersistent) obj);
                fileDoc.field = field.fullname;
                fileDoc.blocked = true;
                fileDoc.save();

            } else if (!(fileDoc.document != null && fileDoc.document.equals(newVal) && fileDoc.field.equals(field.fullname))) { // it's linked to another field or another document
                DocflowFile newFileDoc = DocflowFile.find(
                        "filename = ?1 AND document.type = ?2 AND document.id = ?3 AND field = ?4",
                        fileDoc.filename, docId.type, docId.id, field.fullname).first(); // it's possible that such linked DocflowFile was created before
                if (newFileDoc == null) {
                    // clone the original DocflowFile document for this field
                    newFileDoc = new DocflowFile();
                    newFileDoc.filename = fileDoc.filename;
                    newFileDoc.title = fileDoc.title;
                    setDocument(newFileDoc, docId, (DocumentPersistent) obj);
                    newFileDoc.field = field.fullname;
                    newFileDoc.blocked = true;
                    newFileDoc.save();
                }
                fileDoc = newFileDoc;
            }
        }
        return fileDoc;
    }

    private static void setDocument(final DocflowFile fileDoc, DocumentRef docId, DocumentPersistent documentPersistent) {
        if (!docId.isNew())
            fileDoc.document = docId;
        else
            Transaction.instance().backRefOnSave(documentPersistent, new DocumentUpdateImpl.BackReference() {
                @Override
                public void set(DocumentRef ref) {
                    fileDoc.document = ref;
                    fileDoc.save();
                }
            });
    }
}
