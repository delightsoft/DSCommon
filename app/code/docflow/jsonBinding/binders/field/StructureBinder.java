package code.docflow.jsonBinding.binders.field;

import code.docflow.action.DocumentUpdateImpl;
import code.docflow.compiler.enums.BuiltInTemplates;
import code.docflow.controlflow.Result;
import code.docflow.jsonBinding.JsonTypeBinder;
import code.docflow.jsonBinding.annotations.doc.JsonSilentlySkipInaccessibleObjects;
import code.docflow.jsonBinding.binders.doc.RefBinderBase;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.types.DocumentRef;
import code.docflow.utils.BitArray;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import docflow.DocflowMessages;
import play.exceptions.JavaExecutionException;
import play.exceptions.UnexpectedException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Stack;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class StructureBinder extends JsonTypeBinder.FieldAccessor {

    public final JsonTypeBinder typeBinder;
    final Class<?> type;
    final Object emptyInstance;
    final Template newTemplate;
    final boolean silentlySkipInaccessibleObjects;

    public StructureBinder(Field fld, Method getter, Method setter, String fldName) {
        super(fld, getter, setter, fldName);
        this.type = fld.getType();
        try {
            emptyInstance = this.type.newInstance();
        } catch (InstantiationException e) {
            throw new UnexpectedException(e);
        } catch (IllegalAccessException e) {
            throw new UnexpectedException(e);
        }
        this.typeBinder = JsonTypeBinder.factory.get(fld.getType());
        newTemplate = RefBinderBase.processJsonTemplate(fld);
        silentlySkipInaccessibleObjects = fld.isAnnotationPresent(JsonSilentlySkipInaccessibleObjects.class);
    }

    public void copyFromJson(final Object obj, final JsonNode node,
                             DocumentAccessActionsRights rights, BitArray mask, final String fldPrefix,
                             DocumentUpdateImpl update, DocumentRef docId, Object outerStructure, final Result result) throws Exception {

        if (node.isNull()) {

            if (!nullable) {
                result.addMsg(DocflowMessages.error_ValidationFieldIncorrectValue_2, fldPrefix + fldName, node.asText());
                return;
            }

            final Object t = getter.invoke(obj);

            if (t == null) return;

            setter.invoke(obj, null);
            if (update != null) {
                update.wasUpdate = true;
                if (!derived) {
                    if (update.undoNode != null || !update.undoNode.has(fldName))
                        if (t == null) update.undoNode.putNull(fldName);
                        else {
                            final Template historyTemplate =
                                    docId.safeGetDocument()._docType().
                                            templates.get(BuiltInTemplates.HISTORY.getUpperCase());
                            update.undoNode.put(fldName, (ObjectNode) typeBinder.toJson(t, historyTemplate, rights, mask));
                        }

                    if (update.changesNode != null)
                        update.changesNode.putNull(fldName);
                }
            }
            return;
        }

        if (!node.isObject()) {
            result.addMsg(DocflowMessages.error_ValidationFieldIncorrectValue_2, fldPrefix + fldName, node.asText());
            return;
        }

        Object v = getter.invoke(obj);
        if (v == null) { // make new structure instance, if null
            try {
                v = type.newInstance();
            } catch (InstantiationException e) {
                throw new JavaExecutionException(String.format("Failed instantiate '%1$s' for field '%2$s' in class '%3$s'.",
                        type.getName(), fld.getName(), fld.getDeclaringClass().getName()), e);
            } catch (IllegalAccessException e) {
                throw new JavaExecutionException(String.format("Failed instantiate '%1$s' for field '%2$s' in class '%3$s'.",
                        type.getName(), fld.getName(), fld.getDeclaringClass().getName()), e);
            }
            setter.invoke(obj, v);
        }

        final ObjectNode originalUndoNode = update != null ? update.undoNode : null;
        final ObjectNode originalChangesNode = update != null ? update.changesNode : null;
        final boolean originalWasUpdate = update != null ? update.wasUpdate : false;
        if (update != null) {
            update.changesNode = originalChangesNode != null ? JsonNodeFactory.instance.objectNode() : null;
            update.undoNode = originalUndoNode != null ? JsonNodeFactory.instance.objectNode() : null;
            update.wasUpdate = false;
        }

        // deserialize json to existing (or created above) structure
        typeBinder.fromJson(v, (ObjectNode) node, rights, mask, fldPrefix + fldName + ".", update, null, outerStructure, result);

        if (update != null) {
            if (update.wasUpdate) {
                if (originalUndoNode != null && !originalUndoNode.has(fldName))
                    originalUndoNode.put(fldName, update.undoNode);
                if (originalChangesNode != null)
                    originalChangesNode.put(fldName, update.changesNode);
            } else
                update.wasUpdate = originalWasUpdate;
            update.changesNode = originalChangesNode;
            update.undoNode = originalUndoNode;
        }
    }

    public void copyToJson(Object obj, Template template, ObjectNode out, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {

        if (silentlySkipInaccessibleObjects)
            mode |= JsonTypeBinder.SILENT_SKIP_INACCESSIBLE_DOCS;

        final Object v = getter.invoke(obj);
        if (v == null)
            if ((mode & JsonTypeBinder.GENERATE__U) != 0) // in case of null, we still expect empty structure in UI logic
                out.put(fldName, typeBinder.toJson(emptyInstance, template, stack, mode, rights, mask));
            else out.putNull(fldName);
        else
            out.put(fldName, typeBinder.toJson(v, newTemplate != null ? newTemplate : template, stack, mode, rights, mask));
    }
}
