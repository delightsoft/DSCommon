package code.jsonBinding.binders.field;

import code.controlflow.Result;
import code.docflow.action.DocumentUpdate;
import code.docflow.model.FieldSimple;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.jsonBinding.JsonTypeBinder;
import code.types.PolymorphicRef;
import code.utils.BitArray;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import docflow.DocflowMessages;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Stack;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class LongBinder extends JsonTypeBinder.FieldAccessor {
    public LongBinder(Field fld, Method getter, Method setter, String fldName) {
        super(fld, getter, setter, fldName);
    }

    Long min;
    Long max;

    public void setField(code.docflow.model.Field field) {
        super.setField(field);
        if (field instanceof FieldSimple) { // otherwise that would be FieldCalculated
            FieldSimple fs = (FieldSimple) field;
            if (fs.accessedFields != null) {
                if (field.accessedFields.contains("MIN"))
                    min = (long) fs.min;
                if (field.accessedFields.contains("MAX"))
                    max = (long) fs.max;
            }
        }
    }

    public void copyFromJson(final Object obj, final JsonNode node, DocumentAccessActionsRights rights, BitArray mask,
                             final String fldPrefix, DocumentUpdate update, PolymorphicRef docId, Object outerStructure, final Result result) throws Exception {
        boolean ok = true;
        long v = 0;
        if (node.canConvertToLong())
            v = node.longValue();
        else if (node.isTextual())
            try {
                v = Long.parseLong(node.asText());
            } catch (NumberFormatException e) {
                ok = false;
            }
        else
            ok = false;
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

        if ((Long) getter.invoke(obj) != v) {
            setter.invoke(obj, v);
            if (update != null && update.changesGenerator != null)
                update.changesGenerator.writeNumberField(fldName, v);
        }
    }

    public void copyToJson(Object obj, Template template, JsonGenerator generator, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {
        generator.writeNumberField(fldName, (Long) getter.invoke(obj));
    }
}