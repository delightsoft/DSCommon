package code.jsonBinding.binders.doc;

import code.controlflow.Result;
import code.docflow.Docflow;
import code.docflow.DocflowConfig;
import code.docflow.action.DocumentUpdate;
import code.docflow.model.DocType;
import code.docflow.model.FieldReference;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.jsonBinding.JsonTypeBinder;
import code.jsonBinding.RecordAccessor;
import code.jsonBinding.annotations.doc.JsonSilentlySkipInaccessibleObjects;
import code.jsonBinding.annotations.doc.JsonTemplate;
import code.jsonBinding.binders.type.TypeBinder;
import code.models.Document;
import code.models.PersistentDocument;
import code.types.PolymorphicRef;
import code.users.CurrentUser;
import code.utils.BitArray;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import docflow.DocflowMessages;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Stack;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class RefBinder extends JsonTypeBinder.FieldAccessor {

    FieldReference field;
    final TypeBinder valueBinder;
    final Template newTemplate;
    final boolean silentlySkipInaccessibleObjects;


    public RefBinder(Field fld, Method getter, Method setter, String fldName) {
        super(fld, getter, setter, fldName);
        valueBinder = TypeBinder.factory.get(fld);
        newTemplate = processJsonTemplate(fld);
        silentlySkipInaccessibleObjects = fld.isAnnotationPresent(JsonSilentlySkipInaccessibleObjects.class);
    }

    @Override
    public void setField(code.docflow.model.Field field) {
        super.setField(field);
        this.field = (FieldReference) field;
    }

    public void copyFromJson(Object obj, JsonNode node, DocumentAccessActionsRights rights, BitArray mask, String fldPrefix, DocumentUpdate update, PolymorphicRef docId, Object outerStructure, Result result) throws Exception {

        PolymorphicRef v = null;
        if (field != null) {
            v = getId(node, fldPrefix, fldName, field.dbRequired, result);
            if (result.isError())
                return;
            if (v != null && !v.type.equalsIgnoreCase(field.refDocument)) {
                result.addMsg(DocflowMessages.error_ValidationFieldIncorrectValue_2, fldPrefix + fldName, node.asText());
                return;
            }
        } else { // Hack: Reference within action parameters.  Will be fixed later by turing params structs into docs
            v = getId(node, fldPrefix, fldName, false, result);
        }

        final Document t = (Document) getter.invoke(obj);

        PersistentDocument doc = null;
        if (node.isObject()) { // update linked object
            DocType docType = DocflowConfig.instance.documents.get(v == null ? field.refDocument.toUpperCase() : v.type.toUpperCase());
            if (!CurrentUser.getInstance().inActionScope && docType.linkedDocument && t != null && !docType.jsonBinder.recordAccessor.getPolymorphicRef(t).equals(v)) {
                result.addMsg(DocflowMessages.error_ValidationFieldNotAllowedReplaceLinkedDocument_1, fldPrefix + fldName);
                return;
            }
            if (v == null || v.isNew()) {
                doc = Docflow.create(docType, null, node, null, docType.linkedDocument ? docId : null, result);
                if (result.isError())
                    return;
                v = RecordAccessor.factory.get(doc.getClass()).getPolymorphicRef(doc);
            } else {
                doc = v.getDocumentUnsafe();
                if (doc == null)
                    result.addMsg(DocflowMessages.error_ValidationFieldIncorrectValue_2, fldPrefix + fldName, node.asText());
                else
                    Docflow.update(doc, node, null, null, null, result);
            }
            if (result.isError())
                return;
        }

        if ((v == null) ? (t != null) : (t == null || v.id != t._docType().jsonBinder.recordAccessor.getId(t))) {
            if (v != null && node.isTextual())
                try {
                    doc = v.getDocument(); // check the document
                } catch (PolymorphicRef.DocumentAccessException e) {
                    switch (e.type) {
                        case UNKNOWN_DOCUMENT_TYPE:
                        case NO_ACCESS_TO_DOCUMENT_TYPE:
                        case NO_ACCESS_TO_DOCUMENT:
                        case NO_SUCH_DOCUMENT:
                            result.addMsg(DocflowMessages.error_ValidationFieldIncorrectValue_2, fldPrefix + fldName, node.asText());
                            return;
                    }
                    return;
                }

            setter.invoke(obj, doc);
            if (update != null && update.changesGenerator != null)
                if (v == null)
                    update.changesGenerator.writeNullField(fldName);
                else
                    update.changesGenerator.writeStringField(fldName, v.toString());
        }
    }

    public void copyToJson(Object obj, Template template, JsonGenerator generator, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {

        if (silentlySkipInaccessibleObjects)
            mode |= JsonTypeBinder.SILENT_SKIP_INACCESSIBLE_DOCS;

        final Document doc = (Document) getter.invoke(obj);
        if (doc == null)
            generator.writeNullField(fldName);
        else {
            generator.writeFieldName(fldName);
            valueBinder.copyToJson(doc, newTemplate != null ? newTemplate : template, generator, stack, mode);
        }
    }

    static PolymorphicRef getId(JsonNode node, String fldPrefix, String fldName, boolean idRequired, Result result) {

        final JsonNode idNode = node.isObject() ? node.get("id") : node;
        if (idNode == null || idNode.isNull()) {
            if (idRequired)
                result.addMsg(DocflowMessages.error_ValidationFieldIncorrectValue_2, fldPrefix + fldName, node.asText());
            return null;
        }

        try {
            return PolymorphicRef.parse(idNode.asText());
        } catch (IllegalArgumentException e) {
            result.addMsg(DocflowMessages.error_ValidationFieldIncorrectValue_2, fldPrefix + fldName, node.asText());
            return null;
        }
    }

    public static Template processJsonTemplate(Field fld) {
        JsonTemplate jsonTemplate = fld.getAnnotation(JsonTemplate.class);
        if (jsonTemplate != null && !Strings.isNullOrEmpty(jsonTemplate.value())) {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(jsonTemplate.value()));
            return new JsonTypeBinder.TemplateName(jsonTemplate.value());
        }
        return null;
    }
}
