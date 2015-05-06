package code.docflow.jsonBinding.binders.field;

import code.docflow.action.DocumentUpdateImpl;
import code.docflow.controlflow.Result;
import code.docflow.jsonBinding.JsonTypeBinder;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.types.DocumentRef;
import code.docflow.utils.BitArray;
import code.docflow.utils.NamesUtil;
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
public final class EnumBinder extends JsonTypeBinder.FieldAccessor {
    public final Class fldType;

    public EnumBinder(Field fld, Method getter, Method setter, String fldName) {
        super(fld, getter, setter, fldName);
        this.fldType = fld.getType();
    }

    public void copyFromJson(Object obj, JsonNode node, DocumentAccessActionsRights rights, BitArray mask, String fldPrefix, DocumentUpdateImpl update, DocumentRef docId, Object outerStructure, Result result) throws Exception {
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
            if (update != null) {
                update.wasUpdate = true;
                if (!derived) {
                    if (update.undoNode != null && !update.undoNode.has(fldName))
                        if (t == null) update.undoNode.putNull(fldName);
                        else update.undoNode.put(fldName, t.toString());
                    if (update.changesNode != null)
                        if (v == null) update.changesNode.putNull(fldName);
                        else update.changesNode.put(fldName, v.toString());
                }
            }
        }
    }

    public void copyToJson(Object obj, Template template, ObjectNode out, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {
        final Object v = getter.invoke(obj);
        if (v == null) out.putNull(fldName);
        else out.put(fldName, v.toString());
    }
}
