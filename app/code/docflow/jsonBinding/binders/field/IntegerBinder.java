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
public final class IntegerBinder extends JsonTypeBinder.FieldAccessor {
    public IntegerBinder(Field fld, Method getter, Method setter, String fldName) {
        super(fld, getter, setter, fldName);
    }

    Integer min;
    Integer max;

    public void setField(code.docflow.model.Field field) {
        super.setField(field);
        if (field instanceof FieldSimple) { // otherwise that would be FieldJava
            FieldSimple fs = (FieldSimple) field;
            if (fs.accessedFields != null) {
                if (field.accessedFields.contains("MIN"))
                    min = (int) fs.min;
                if (field.accessedFields.contains("MAX"))
                    max = (int) fs.max;
            }
        }
    }

    public void copyFromJson(final Object obj, final JsonNode node, DocumentAccessActionsRights rights, BitArray mask,
                             final String fldPrefix, DocumentUpdateImpl update, DocumentRef docId, Object outerStructure, final Result result) throws Exception {
        boolean ok = true;
        Integer v = null;
        if (node.canConvertToInt())
            v = node.intValue();
        else if (node.isTextual())
            try {
                final String s = node.asText();
                if (!"".equals(s))
                    v = Integer.parseInt(s);
            } catch (NumberFormatException e) {
                ok = false;
            }
        else
            ok = node.isNull() && nullable;
        if (!ok) {
            result.addMsg(DocflowMessages.error_ValidationFieldIncorrectValue_2, fldPrefix + fldName, node.asText());
            return;
        }

        if (min != null) {
            if (max != null) {
                if (v < min || max < v) {
                    result.addMsg(DocflowMessages.error_ValidationFieldNotInRange_3, fldPrefix + fldName, min, max);
                    return;
                }
            } else if (v < min) {
                result.addMsg(DocflowMessages.error_ValidationFieldMin_2, fldPrefix + fldName, min);
                return;
            }
        } else if (max != null && max < v) {
            result.addMsg(DocflowMessages.error_ValidationFieldMax_2, fldPrefix + fldName, max);
            return;
        }

        final Integer t = (Integer) getter.invoke(obj);
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
        final Integer v = (Integer) getter.invoke(obj);
        if (v == null)
            out.putNull(fldName);
        else
            out.put(fldName, v);
    }
}
