package code.jsonBinding.binders.field;

import code.controlflow.Result;
import code.docflow.action.DocumentUpdate;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.jsonBinding.JsonTypeBinder;
import code.types.PolymorphicRef;
import code.utils.BitArray;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Objects;
import docflow.DocflowMessages;
import play.libs.Crypto;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Stack;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class PasswordBinder extends JsonTypeBinder.FieldAccessor {
    public PasswordBinder(Field fld, Method getter, Method setter, String fldName) {
        super(fld, getter, setter, fldName);
    }

    public void copyFromJson(final Object obj, final JsonNode node, DocumentAccessActionsRights rights, BitArray mask,
                             final String fldPrefix, DocumentUpdate update, PolymorphicRef docId, Object outerStructure, final Result result) throws Exception {

        if (!node.isTextual()) {
            result.addMsg(DocflowMessages.error_ValidationFieldIncorrectValue_2, fldPrefix + fldName, node.asText());
            return;
        }

        final String pwd = node.isNull() ? null : node.asText().trim();

        if (pwd.equals(""))
            return;

        final String v = Crypto.passwordHash(pwd);
        final String t = (String) getter.invoke(obj);
        if (!Objects.equal(v, t)) {
            setter.invoke(obj, v);
            if (update != null && update.changesGenerator != null)
                update.changesGenerator.writeStringField(fldName, v);
        }
    }

    public void copyToJson(Object obj, Template template, JsonGenerator generator, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {
        // output password only if it's editable in update template mode
        if ((mode & (JsonTypeBinder.GENERATE__U | JsonTypeBinder._U_FIELD)) == (JsonTypeBinder.GENERATE__U | JsonTypeBinder._U_FIELD))
            generator.writeStringField(fldName, "");
    }
}
