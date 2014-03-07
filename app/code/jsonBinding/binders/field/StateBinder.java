package code.jsonBinding.binders.field;

import code.controlflow.Result;
import code.docflow.DocflowConfig;
import code.docflow.action.DocumentUpdate;
import code.docflow.model.State;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.jsonBinding.JsonTypeBinder;
import code.models.Document;
import code.types.PolymorphicRef;
import code.utils.BitArray;
import code.utils.EnumCaseInsensitiveIndex;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.sun.org.apache.bcel.internal.generic.NEW;
import docflow.DocflowMessages;
import play.exceptions.JavaExecutionException;

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
            throw new JavaExecutionException(e);
        } catch (NoSuchFieldException e) {
            throw new JavaExecutionException(e);
        }
    }

    public void copyFromJson(Object obj, JsonNode node, DocumentAccessActionsRights rights, BitArray mask, String fldPrefix, DocumentUpdate update, PolymorphicRef docId, Object outerStructure, Result result) throws Exception {
        final Document doc = (Document) obj;
        final State docState = doc._state();
        if (!DocflowConfig.ImplicitStates.NEW.toString().equals(docState.name)) {
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

    public void copyToJson(Object obj, Template template, JsonGenerator generator, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {
        final Document doc = (Document) obj;
        final Object v = getter.invoke(obj);
        checkNotNull(v);
        // TODO: Reconsider: Should we write state to History
        generator.writeStringField(fldName, v.toString());
    }
}
