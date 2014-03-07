package code.jsonBinding.binders.doc;

import code.controlflow.Result;
import code.docflow.Docflow;
import code.docflow.DocflowConfig;
import code.docflow.action.DocumentUpdate;
import code.docflow.model.DocType;
import code.docflow.model.FieldPolymorphicReference;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.jsonBinding.JsonTypeBinder;
import code.jsonBinding.RecordAccessor;
import code.jsonBinding.annotations.doc.JsonSilentlySkipInaccessibleObjects;
import code.jsonBinding.binders.type.TypeBinder;
import code.models.Document;
import code.models.PersistentDocument;
import code.types.PolymorphicRef;
import code.utils.BitArray;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Objects;
import docflow.DocflowMessages;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Stack;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class PolymorphicRefBinder extends JsonTypeBinder.FieldAccessor {

    FieldPolymorphicReference field;
    final TypeBinder valueBinder;
    final Template newTemplate;
    final boolean silentlySkipInaccessibleObjects;

    public PolymorphicRefBinder(Field fld, Method getter, Method setter, String fldName) {
        super(fld, getter, setter, fldName);
        valueBinder = TypeBinder.factory.get(fld);
        newTemplate = RefBinder.processJsonTemplate(fld);
        silentlySkipInaccessibleObjects = fld.isAnnotationPresent(JsonSilentlySkipInaccessibleObjects.class);
    }

    @Override
    public void setField(code.docflow.model.Field field) {
        super.setField(field);
        this.field = (FieldPolymorphicReference) field;
    }

    public void copyFromJson(Object obj, JsonNode node, DocumentAccessActionsRights rights, BitArray mask, String fldPrefix, DocumentUpdate update, PolymorphicRef docId, Object outerStructure, Result result) throws Exception {

        PolymorphicRef v = RefBinder.getId(node, fldPrefix, fldName, true, result);
        if (result.isError())
            return;

        if (field != null) { // Hack: Reference not within action parameters.  Will be fixed later by turing params structs into docs
            if (field.refDocumentsNames != null && !field.refDocumentsNames.contains(v.type.toUpperCase())) {
                result.addMsg(DocflowMessages.error_ValidationFieldIncorrectValue_2, fldPrefix + fldName, node.asText());
                return;
            }
        }

        final PolymorphicRef t = (PolymorphicRef) getter.invoke(obj);

        if (node.isObject()) { // update linked object
            DocType docType = DocflowConfig.instance.documents.get(v.type.toUpperCase());
            if (docType.linkedDocument && t != null && !t.equals(v))
                result.addMsg(DocflowMessages.error_ValidationFieldNotAllowedReplaceLinkedDocument_1, fldPrefix + fldName);
            if (v.isNew()) {
                Document doc = Docflow.create(docType, null, node, null, docType.linkedDocument ? docId : null, result);
                v = RecordAccessor.factory.get(doc.getClass()).getPolymorphicRef(doc);
            } else {
                PersistentDocument doc = v.getDocumentUnsafe();
                if (doc == null)
                    result.addMsg(DocflowMessages.error_ValidationFieldIncorrectValue_2, fldPrefix + fldName, node.asText());
                else
                    Docflow.update(doc, node, null, null, null, result);
            }
            if (result.isError())
                return;
        }

        if (!Objects.equal(v, t)) {
            if (node.isTextual()) // check access rights, only then id is changed
                try {
                    v.getDocument(); // check the document
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

            setter.invoke(obj, v);
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

        final PolymorphicRef v = (PolymorphicRef) getter.invoke(obj);
        if (v == null || PolymorphicRef.Null.equals(v))
            generator.writeNullField(fldName);
        else {
            final Document doc = v.getDocumentUnsafe();
            if (doc == null)
                generator.writeNullField(fldName);
            else {
                generator.writeFieldName(fldName);
                valueBinder.copyToJson(doc, newTemplate != null ? newTemplate : template, generator, stack, mode);
            }
        }
    }
}
