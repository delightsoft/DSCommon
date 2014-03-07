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
import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Stack;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class DateTimeBinder extends JsonTypeBinder.FieldAccessor {
    static final DateTimeFormatter dateTimeFormatter = ISODateTimeFormat.localDateParser();
    static final LocalTime MIDNIGHT = new LocalTime(0, 0);

    public DateTimeBinder(Field fld, Method getter, Method setter, String fldName) {
        super(fld, getter, setter, fldName);
    }

    public void copyFromJson(final Object obj, final JsonNode node,
                             DocumentAccessActionsRights rights, BitArray mask, final String fldPrefix,
                             DocumentUpdate update, PolymorphicRef docId, Object outerStructure, final Result result) throws Exception {

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

//        if (min != null) {
//            if (max != null) {
//                if (v < min || max < v) {
//                    result.addMsg(DocflowMessages.error_ValidationFieldNotInRange_3, fldPrefix + fldName, min, max);
//                    return;
//                }
//            } else if (v < min) {
//                result.addMsg(DocflowMessages.error_ValidationFieldMin_2, fldPrefix + fldName, min);
//                return;
//            }
//        } else if (max != null && max < v) {
//            result.addMsg(DocflowMessages.error_ValidationFieldMax_2, fldPrefix + fldName, max);
//            return;
//        }

        DateTime vDate = (v == null) ? null : new DateTime(v);
        final DateTime t = (DateTime) getter.invoke(obj);
        if (!Objects.equal(vDate, t)) {
            setter.invoke(obj, vDate);
            if (update != null && update.changesGenerator != null)
                if (v == null)
                    update.changesGenerator.writeNullField(fldName);
                else
                    update.changesGenerator.writeNumberField(fldName, v);
        }

//        boolean ok = true;
//        LocalDate v = null;
//        if (node.isTextual())
//            try {
//                final String s = node.asText();
//                if (!"".equals(s))
//                    v = dateTimeFormatter.parseLocalDate(node.asText());
//            } catch (IllegalArgumentException e) {
//                ok = false;
//            }
//        else
//            ok = node.isNull();
//        if (!ok) {
//            result.addMsg(DocflowMessages.error_ValidationFieldIncorrectValue_2, fldPrefix + fldName, node.asText());
//            return;
//        }
//        final DateTime t = (DateTime) getter.invoke(obj);
//
//        if (!Objects.equal(v, t)) {
//            setter.invoke(obj, v.toDateTime(MIDNIGHT));
//            if (update != null && update.changesGenerator != null)
//                if (v == null)
//                    update.changesGenerator.writeNullField(fldName);
//                else
//                    update.changesGenerator.writeStringField(fldName, v.toString());
//        }
    }

    public void copyToJson(final Object obj, Template template, final JsonGenerator generator, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask)
            throws Exception {
        final DateTime v = (DateTime) getter.invoke(obj);
        if (v == null)
            generator.writeNullField(fldName);
        else
            generator.writeNumberField(fldName, v.getMillis());
//            generator.writeStringField(fldName, v.toLocalDate().toString());
    }
}
