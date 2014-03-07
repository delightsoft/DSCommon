package code.jsonBinding.binders.field;

import code.controlflow.Result;
import code.docflow.action.DocumentUpdate;
import code.docflow.model.FieldSimple;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.jsonBinding.JsonTypeBinder;
import code.jsonBinding.annotations.field.JsonPrecision;
import code.types.PolymorphicRef;
import code.utils.BitArray;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import docflow.DocflowMessages;
import play.exceptions.UnexpectedException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Stack;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class DoubleOrNullBinder extends JsonTypeBinder.FieldAccessor {
    public DoubleOrNullBinder(Field fld, Method getter, Method setter, String fldName) {
        super(fld, getter, setter, fldName);
        final JsonPrecision precisionAnnot = fld.getAnnotation(JsonPrecision.class);
        if (precisionAnnot != null) {
            scale = precisionAnnot.value();
            if (!(0 < scale && scale <= 10))
                throw new UnexpectedException(String.format("Field '%s': Annotation JsonPrecision: value is out range 1 to 10.", fldName));
            delta = Math.pow(10, -scale) / 2;
        }
    }

    Double min;
    Double max;

    int scale = DoubleBinder.defaultScale;
    double delta = DoubleBinder.defaultDelta;

    public void setField(code.docflow.model.Field field) {
        super.setField(field);
        if (field instanceof FieldSimple) { // otherwise that would be FieldCalculated
            FieldSimple fs = (FieldSimple) field;
            if (fs.accessedFields != null) {
                if (field.accessedFields.contains("MIN"))
                    min = fs.min;
                if (field.accessedFields.contains("MAX"))
                    max = fs.max;
            }
        }
    }

    public void copyFromJson(final Object obj, final JsonNode node, DocumentAccessActionsRights rights, BitArray mask,
                             String fldPrefix, DocumentUpdate update, PolymorphicRef docId, Object outerStructure, final Result result) throws Exception {
        boolean ok = true;
        Double v = null;
        if (node.isDouble() || node.canConvertToLong())
            v = node.doubleValue();
        else if (node.isTextual())
            try {
                final String s = node.asText();
                if (!"".equals(s))
                    v = Double.parseDouble(s);
            } catch (NumberFormatException e) {
                ok = false;
            }
        else
            ok = node.isNull();

        if (!ok) {
            result.addMsg(DocflowMessages.error_ValidationFieldIncorrectValue_2, fldPrefix + fldName, node.asText());
            return;
        }

        if (v != null)
            if (min != null) {
                if (max != null) {
                    if (v < min || max < v) {
                        result.addMsg(DocflowMessages.error_ValidationFieldNotInRange_3, fldPrefix + fldName, min, max);
                        return;
                    }
                } else if (v < min) {
                    result.addMsg(DocflowMessages.error_ValidationFieldMin_2, fldPrefix + fldName, min);
                    return;
                }
            } else if (max != null && max < v) {
                result.addMsg(DocflowMessages.error_ValidationFieldMax_2, fldPrefix + fldName, max);
                return;
            }

        final Double t = (Double) getter.invoke(obj);
        if ((v == null && t != null) || (v != null && (t == null || Math.abs(t - v) >= delta))) {
            setter.invoke(obj, v);
            if (update != null && update.changesGenerator != null)
                // TODO: Consider using rounding down to requested precision, like below.
                if (v == null)
                    update.changesGenerator.writeNullField(fldName);
                else {
                    final BigDecimal bigDecimal = new BigDecimal(v, DoubleBinder.getProperMathContext(v, scale));
                    update.changesGenerator.writeNumberField(fldName, bigDecimal);
                }
        }
    }

    public void copyToJson(Object obj, Template template, JsonGenerator generator, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {
        final Double v = (Double) getter.invoke(obj);
        if (v == null)
            generator.writeNullField(fldName);
        else {
            final BigDecimal bigDecimal = new BigDecimal(v, DoubleBinder.getProperMathContext(v, scale));
            generator.writeNumberField(fldName, bigDecimal);
        }
    }
}
