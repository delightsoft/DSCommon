package code.docflow.jsonBinding.binders.doc;

import code.docflow.action.DocumentUpdateImpl;
import code.docflow.controlflow.Result;
import code.docflow.Docflow;
import code.docflow.DocflowConfig;
import code.docflow.api.DocflowApiCreate;
import code.docflow.model.DocType;
import code.docflow.model.Template;
import code.docflow.docs.DocumentPersistent;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.jsonBinding.JsonTypeBinder;
import code.docflow.jsonBinding.annotations.doc.JsonSilentlySkipInaccessibleObjects;
import code.docflow.jsonBinding.annotations.doc.JsonTemplate;
import code.docflow.jsonBinding.binders.type.TypeBinder;
import code.docflow.docs.Document;
import code.docflow.types.DocumentRef;
import code.docflow.users.CurrentUser;
import code.docflow.utils.BitArray;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import docflow.DocflowMessages;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Stack;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public abstract class RefBinderBase extends JsonTypeBinder.FieldAccessor {

    final TypeBinder valueBinder;
    final Template newTemplate;
    final boolean silentlySkipInaccessibleObjects;

    public RefBinderBase(Field fld, Method getter, Method setter, String fldName) {
        super(fld, getter, setter, fldName);
        valueBinder = TypeBinder.factory.get(fld);
        newTemplate = processJsonTemplate(fld);
        silentlySkipInaccessibleObjects = fld.isAnnotationPresent(JsonSilentlySkipInaccessibleObjects.class);
    }

    static DocumentRef getId(JsonNode node, String fldPrefix, String fldName, boolean idRequired, Result result) {
        final JsonNode idNode = node.isObject() ? node.get("id") : node;
        if (idNode == null || idNode.isNull())
            if (!idRequired) return DocumentRef.NULL;
        if (idNode != null && idNode.isTextual())
            try {
                final DocumentRef v = DocumentRef.parse(idNode.asText());
                if (v != DocumentRef.NULL)
                    return v;
            } catch (IllegalArgumentException e) {
                // nothing
            }
        result.addMsg(DocflowMessages.error_ValidationFieldIncorrectValue_2, fldPrefix + fldName, node);
        return null;
    }

    public static Template processJsonTemplate(Field fld) {
        JsonTemplate jsonTemplate = fld.getAnnotation(JsonTemplate.class);
        if (jsonTemplate != null && !Strings.isNullOrEmpty(jsonTemplate.value())) {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(jsonTemplate.value()));
            return new JsonTypeBinder.TemplateName(jsonTemplate.value());
        }
        return null;
    }

    public void copyFromJson(Object obj, JsonNode node, DocumentAccessActionsRights rights, BitArray mask, String fldPrefix, DocumentUpdateImpl update, DocumentRef docId, Object outerStructure, Result result) throws Exception {

        if (!(node.isTextual() || node.isObject() || (node.isNull() && nullable))) {
            result.addMsg(DocflowMessages.error_ValidationFieldIncorrectValue_2, fldPrefix + fldName, node.asText());
            return;
        }

        DocumentRef v = getId(node, fldPrefix, fldName, idRequired(), result);
        if (result.isError())
            return;

        v = checkAndFixType(v, fldPrefix, node, result);
        if (result.isError())
            return;

        final DocumentRef t = retrieveDocFromField(obj);
        DocumentPersistent doc = null;

        if (node.isObject()) { // update linked object
            DocType docType = DocflowConfig.instance.documents.get(v.type.toUpperCase());
            if (!CurrentUser.getInstance().inActionScope && docType.linkedDocument && t != DocumentRef.NULL && !t.equals(v)) {
                result.addMsg(DocflowMessages.error_ValidationFieldNotAllowedReplaceLinkedDocument_1, fldPrefix + fldName);
                return;
            }
            if (v.isNew()) {
                DocumentRef subjId = docType.linkedDocument ? docId : null;
                doc = DocflowApiCreate._create(docType, null, (ObjectNode) node, null, subjId, result);
                if (result.isError())
                    return;
                v = doc._ref();
            } else {
                doc = v.getDocumentUnsafe();
                if (doc == null)
                    result.addMsg(DocflowMessages.error_ValidationFieldIncorrectValue_2, fldPrefix + fldName, node.asText());
                else {
                    Docflow.update(doc, (ObjectNode) node, result);
                    if (result.isError())
                        return;
                }
            }
        }

        if (!Objects.equal(v, t)) {
            if (v != null && node.isTextual()) // check access rights, only then id is changed. Otherwiese rights were checked above
                try {
                    doc = v.safeGetDocument(); // check the document
                } catch (DocumentRef.DocumentAccessException e) {
                    switch (e.type) {
                        case UNKNOWN_DOCUMENT_TYPE:
                            result.addMsg(DocflowMessages.error_ValidationFieldUnknownDocType_2, fldPrefix + fldName, node.asText());
                            break;
                        case NO_ACCESS_TO_DOCUMENT_TYPE:
                            result.addMsg(DocflowMessages.error_ValidationFieldNoAccessToDocType_2, fldPrefix + fldName, node.asText());
                            break;
                        case NO_ACCESS_TO_DOCUMENT:
                            result.addMsg(DocflowMessages.error_ValidationFieldNoAccessToTheDocument_2, fldPrefix + fldName, node.asText());
                            break;
                        case NO_SUCH_DOCUMENT:
                            result.addMsg(DocflowMessages.error_ValidationFieldNoSuchDocument_2, fldPrefix + fldName, node.asText());
                            break;
                    }
                    return;
                }
                if (result.isError())
                    return;

            assignDocToField(obj, v, doc, t, docId, update);
            if (update != null) {
                update.wasUpdate = true;
                if (update.undoNode != null)
                    if (v == null) update.undoNode.putNull(fldName);
                    else update.undoNode.put(fldName, v.toString());
                if (update.changesNode != null)
                    if (!update.changesNode.has(fldName))
                        if (t == null) update.changesNode.putNull(fldName);
                        else update.changesNode.put(fldName, t.toString());
            }
        }
    }

    public void copyToJson(Object obj, Template template, ObjectNode out, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {

        if (silentlySkipInaccessibleObjects)
            mode |= JsonTypeBinder.SILENT_SKIP_INACCESSIBLE_DOCS;

        final DocumentRef v = retrieveDocFromField(obj);
        if (v == null)
            out.putNull(fldName);
        else {
            final Document doc = v.getDocumentUnsafe();
            if (doc == null) out.putNull(fldName);
            else out.put(fldName, valueBinder.copyToJson(doc, newTemplate != null ? newTemplate : template, stack, mode));
        }
    }

    protected abstract boolean idRequired();

    protected abstract DocumentRef checkAndFixType(DocumentRef v, String fldPrefix, JsonNode node, Result result);

    protected abstract DocumentRef retrieveDocFromField(Object obj);

    protected abstract void assignDocToField(Object obj, DocumentRef newVal, Document newDoc, DocumentRef oldVal, DocumentRef docId, DocumentUpdateImpl update);

}
