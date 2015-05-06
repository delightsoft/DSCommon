package code.docflow.jsonBinding.binders.field;

import code.docflow.action.DocumentUpdateImpl;
import code.docflow.controlflow.Result;
import code.docflow.jsonBinding.JsonTypeBinder;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.types.DocumentRef;
import code.docflow.utils.BitArray;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import docflow.DocflowMessages;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Stack;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class PhoneBinder extends JsonTypeBinder.FieldAccessor {
    public PhoneBinder(Field fld, Method getter, Method setter, String fldName) {
        super(fld, getter, setter, fldName);
    }

    public void copyFromJson(final Object obj, final JsonNode node, DocumentAccessActionsRights rights, BitArray mask,
                             final String fldPrefix, DocumentUpdateImpl update, DocumentRef docId, Object outerStructure, final Result result) throws Exception {
        String v = node.isNull() ? null : node.asText().trim();
        if (Strings.isNullOrEmpty(v)) {
            if (!nullable) {
                result.addMsg(DocflowMessages.error_ValidationFieldIncorrectValue_2, fldPrefix + fldName, node.asText());
                return;
            }
        } else {
            try {
                // TODO: Locale: Hardcoded region
                final Phonenumber.PhoneNumber n = PhoneNumberUtil.getInstance().parse(v, "ru");
                v = PhoneNumberUtil.getInstance().format(n, PhoneNumberUtil.PhoneNumberFormat.E164);
                if (n.hasExtension())
                    v += "x" + n.getExtension();
            } catch (NumberParseException e) {
                result.addMsg(DocflowMessages.error_ValidationFieldInvalidPhone_1, fldPrefix + fldName);
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
