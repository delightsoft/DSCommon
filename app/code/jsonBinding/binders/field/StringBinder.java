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
        if (field instanceof FieldSimple) { // otherwise that would be FieldCalculated
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
                             final String fldPrefix, DocumentUpdate update, PolymorphicRef docId, Object outerStructure, final Result result) throws Exception {
        final String v = node.isNull() ? null : node.asText();
        if (v != null) {
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
            if (update != null && update.changesGenerator != null)
                if (v == null)
                    update.changesGenerator.writeNullField(fldName);
                else
                    update.changesGenerator.writeStringField(fldName, v);
        }
    }

    public void copyToJson(Object obj, Template template, JsonGenerator generator, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {
        final String v = (String) getter.invoke(obj);
        if (v == null)
            generator.writeNullField(fldName);
        else
            generator.writeStringField(fldName, v);
    }
}
