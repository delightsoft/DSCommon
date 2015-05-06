package code.docflow.jsonBinding.binders.field;

import code.docflow.action.DocumentUpdateImpl;
import code.docflow.controlflow.Result;
import code.docflow.controlflow.Result2Json;
import code.docflow.jsonBinding.JsonTypeBinder;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.types.DocumentRef;
import code.docflow.utils.BitArray;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import docflow.DocflowMessages;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Stack;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */

public final class ResultBinder extends JsonTypeBinder.FieldAccessor {
    public ResultBinder(Field fld, Method getter, Method setter, String fldName) {
        super(fld, getter, setter, fldName);
    }

    public void copyFromJson(final Object obj, final JsonNode node, DocumentAccessActionsRights rights, BitArray mask,
                             final String fldPrefix, DocumentUpdateImpl update, DocumentRef docId, Object outerStructure, final Result result) throws Exception {
        final String v = node.isNull() ? null : node.asText();
        if (v == null) {
            if (!nullable) {
                result.addMsg(DocflowMessages.error_ValidationFieldIncorrectValue_2, fldPrefix + fldName, node.asText());
                return;
            }
        }
        final String t = (String) getter.invoke(obj);
        if (!Objects.equal(v, t)) {
            setter.invoke(obj, v);
            // 'result' is always 'derived', so it does not show in the history
            // TODO: Remove this, once updates of derived fields will be saved without history
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
        if (Strings.isNullOrEmpty(v))
            out.putNull(fldName);
        else
            try {
                final Result r = Result2Json.toResult(v);
                final ObjectNode resultNode = JsonNodeFactory.instance.objectNode();
                resultNode.put("result", r.getCode() == Result.Ok ? "success" : "error");
                final int messagesCount = r.getMessagesCount();
                if (messagesCount > 0) {
                    final ObjectNode messagesNode = JsonNodeFactory.instance.objectNode();
                    messagesNode.put("count", messagesCount);
                    messagesNode.put("html", r.toHtml());
                    resultNode.put("messages", messagesNode);
                }
                out.put(fldName, resultNode);
            } catch (IllegalArgumentException e) {
                final ObjectNode errorNode = JsonNodeFactory.instance.objectNode();
                errorNode.put("__invalid_json", e.getMessage());
                out.put(fldName, errorNode);
            }
    }
}
