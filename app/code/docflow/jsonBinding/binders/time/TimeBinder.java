package code.docflow.jsonBinding.binders.time;

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
import docflow.DocflowMessages;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalTime;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Stack;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class TimeBinder extends JsonTypeBinder.FieldAccessor {

    static final DateTime ZERO_DAY = new DateTime(0L, DateTimeZone.UTC);

    public TimeBinder(Field fld, Method getter, Method setter, String fldName) {
        super(fld, getter, setter, fldName);
    }

    public void copyFromJson(final Object obj, final JsonNode node,
                             DocumentAccessActionsRights rights, BitArray mask, final String fldPrefix,
                             DocumentUpdateImpl update, DocumentRef docId, Object outerStructure, final Result result) throws Exception {

        boolean ok = true;
        Long v = null;
        if (node.canConvertToLong())
            v = node.longValue();
        else if (node.isTextual())
            try {
                final String s = node.asText();
                if (!"".equals(s))
                    v = Long.parseLong(s);
            } catch (NumberFormatException e) {
                ok = false;
            }
        else
            ok = node.isNull();
        if (!ok) {
            result.addMsg(DocflowMessages.error_ValidationFieldIncorrectValue_2, fldPrefix + fldName, node.asText());
            return;
        }

        final LocalTime t = (LocalTime) getter.invoke(obj);
        final LocalTime vDate = (v == null) ? null : new LocalTime(v, DateTimeZone.UTC);
        if (!Objects.equal(vDate, t)) {
            setter.invoke(obj, vDate);
            if (update != null) {
                update.wasUpdate = true;
                if (!derived) {
                    if (update.undoNode != null && !update.undoNode.has(fldName))
                        if (t == null) update.undoNode.putNull(fldName);
                        else update.undoNode.put(fldName, t.toDateTime(new DateTime(ZERO_DAY)).getMillis());
                    if (update.changesNode != null)
                        if (v == null) update.changesNode.putNull(fldName);
                        else update.changesNode.put(fldName, v);
                }
            }
        }
    }

    public void copyToJson(final Object obj, Template template, final ObjectNode out, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask)
            throws Exception {
        final LocalTime v = (LocalTime) getter.invoke(obj);
        if (v == null) out.putNull(fldName);
        else out.put(fldName, v.toDateTimeToday(DateTimeZone.UTC).getMillis());
    }
}
