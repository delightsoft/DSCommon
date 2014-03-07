package code.jsonBinding.binders.field;

import code.controlflow.Result;
import code.docflow.action.DocumentUpdate;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.jsonBinding.JsonTypeBinder;
import code.types.PolymorphicRef;
import code.utils.BitArray;
import code.utils.NamesUtil;
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
public final class EnumBinder extends JsonTypeBinder.FieldAccessor {
    public final Class fldType;

    public EnumBinder(Field fld, Method getter, Method setter, String fldName) {
        super(fld, getter, setter, fldName);
        this.fldType = fld.getType();
    }

    public void copyFromJson(Object obj, JsonNode node, DocumentAccessActionsRights rights, BitArray mask, String fldPrefix, DocumentUpdate update, PolymorphicRef docId, Object outerStructure, Result result) throws Exception {
        boolean ok = true;
        Object v = null;
        if (node.isTextual() || node.isBoolean())
            try {
                final String s = node.asText();
                if (!"".equals(s))
                    v = Enum.valueOf(fldType, NamesUtil.wordsToUpperUnderscoreSeparated(s));
            } catch (IllegalArgumentException e) {
                ok = false;
            }
        else
            ok = node.isNull();
        if (!ok) {
            result.addMsg(DocflowMessages.error_ValidationFieldIncorrectValue_2, fldPrefix + fldName, node.asText());
            return;
        }
        final Object t = getter.invoke(obj);
        if (!Objects.equal(v, t)) {
            setter.invoke(obj, v);
            if (update != null && update.changesGenerator != null)
                if (v == null)
                    update.changesGenerator.writeNullField(fldName);
                else
                    update.changesGenerator.writeStringField(fldName, v.toString());
        }
    }

    public void copyToJson(Object obj, Template template, JsonGenerator generator, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {
        final Object v = getter.invoke(obj);
        if (v == null)
            generator.writeNullField(fldName);
        else
            generator.writeStringField(fldName, v.toString());
    }
}
