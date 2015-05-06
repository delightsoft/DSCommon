package code.docflow.jsonBinding.binders.field;

import code.docflow.action.DocumentUpdateImpl;
import code.docflow.controlflow.Result;
import code.docflow.compiler.enums.BuiltInStates;
import code.docflow.model.State;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.jsonBinding.JsonTypeBinder;
import code.docflow.docs.Document;
import code.docflow.types.DocumentRef;
import code.docflow.utils.BitArray;
import code.docflow.utils.EnumCaseInsensitiveIndex;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import docflow.DocflowMessages;
import play.exceptions.UnexpectedException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Stack;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class StateBinder extends JsonTypeBinder.FieldAccessor {
    public final Class fldType;
    private EnumCaseInsensitiveIndex statesIndex;

    public StateBinder(Field fld, Method getter, Method setter, String fldName) {
        super(fld, getter, setter, fldName);
        this.fldType = fld.getType();
        try {
            final Field statesFld = fld.getDeclaringClass().getField("_states");
            checkState(EnumCaseInsensitiveIndex.class.isAssignableFrom(statesFld.getType()));
            statesIndex = (EnumCaseInsensitiveIndex) statesFld.get(null);
        } catch (IllegalAccessException e) {
            throw new UnexpectedException(e);
        } catch (NoSuchFieldException e) {
            throw new UnexpectedException(e);
        }
    }

    public void copyFromJson(Object obj, JsonNode node, DocumentAccessActionsRights rights, BitArray mask, String fldPrefix, DocumentUpdateImpl update, DocumentRef docId, Object outerStructure, Result result) throws Exception {
        final Document doc = (Document) obj;
        final State docState = doc._state();
        if (!BuiltInStates.NEW.toString().equals(docState.name)) {
            result.addMsg(DocflowMessages.error_ValidationStateCanOnlyBeAssignedToAnewDocument_1, fldName);
            return;
        }
        final String v = node.isNull() ? null : node.asText();
        if (Strings.isNullOrEmpty(v)) {
            result.addMsg(DocflowMessages.error_ValidationStateNullOrEmpty_1, fldName);
            return;
        }
        final Enum state = statesIndex.get(v);
        if (state == null) {
            result.addMsg(DocflowMessages.error_ValidationStateValue_2, fldName, v);
            return;
        }
        setter.invoke(obj, state);
    }

    public void copyToJson(Object obj, Template template, ObjectNode out, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {
        final Document doc = (Document) obj;
        final Object v = getter.invoke(obj);
        checkNotNull(v);
        // TODO: Reconsider: Should we write state to History
        out.put(fldName, v.toString());
    }
}
