package code.docflow.jsonBinding.binders.field;

import code.docflow.action.DocumentUpdateImpl;
import code.docflow.controlflow.Result;
import code.docflow.jsonBinding.JsonTypeBinder;
import code.docflow.model.FieldSimple;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.types.DocumentRef;
import code.docflow.utils.BitArray;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Objects;
import docflow.DocflowMessages;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Stack;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class StringBinder extends JsonTypeBinder.FieldAccessor {
    public StringBinder(Field fld, Method getter, Method setter, String fldName) {
        super(fld, getter, setter, fldName);
    }

    Integer minLength;
    Integer maxLength;

    public void setField(code.docflow.model.Field field) {
        super.setField(field);
        if (field instanceof FieldSimple) { // otherwise that would be FieldJava
            FieldSimple fs = (FieldSimple) field;
            if (fs.accessedFields != null) {
                if (field.accessedFields.contains("MINLENGTH"))
                    minLength = fs.minLength;
                if (field.accessedFields.contains("MAXLENGTH"))
                    maxLength = fs.maxLength;
            }
        }
    }

    public void copyFromJson(final Object obj, final JsonNode node, DocumentAccessActionsRights rights, BitArray mask,
                             final String fldPrefix, DocumentUpdateImpl update, DocumentRef docId, Object outerStructure, final Result result) throws Exception {
        final String v = node.isNull() ? null : node.asText();
        if (v == null) {
            if (!nullable) {
                result.addMsg(DocflowMessages.error_ValidationFieldIncorrectValue_2, fldPrefix + fldName, node.asText());
                return;
            }
        } else {
            if (minLength != null && v.length() < minLength) {
                result.addMsg(DocflowMessages.error_ValidationFieldMinLength_2, fldPrefix + fldName, minLength);
                return;
            }
            if (maxLength != null && v.length() > maxLength) {
                result.addMsg(DocflowMessages.error_ValidationFieldMaxLength_2, fldPrefix + fldName, maxLength);
                return;
            }
        }

        final String t = (String) getter.invoke(obj);
        if (!Objects.equal(v, t)) {
            setter.invoke(obj, v);
            if (update != null) {
                update.wasUpdate = true;
                if (!derived) {
                    if (update.undoNode != null && !update.undoNode.has(fldName))
                        if (t == null) update.undoNode.putNull(fldName);
                        else update.undoNode.put(fldName, t);
                    if (update.changesNode != null)
                        if (v == null) update.changesNode.putNull(fldName);
                        else update.changesNode.put(fldName, v);
                }
            }
        }
    }

    public void copyToJson(Object obj, Template template, ObjectNode out, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {
        final String v = (String) getter.invoke(obj);
        if (v == null) out.putNull(fldName);
        else out.put(fldName, v);
    }
}
