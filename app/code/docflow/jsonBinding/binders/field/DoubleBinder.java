package code.docflow.jsonBinding.binders.field;

import code.docflow.action.DocumentUpdateImpl;
import code.docflow.controlflow.Result;
import code.docflow.jsonBinding.JsonTypeBinder;
import code.docflow.jsonBinding.annotations.field.JsonPrecision;
import code.docflow.model.FieldSimple;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.types.DocumentRef;
import code.docflow.utils.BitArray;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import docflow.DocflowMessages;
import play.exceptions.UnexpectedException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Stack;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class DoubleBinder extends JsonTypeBinder.FieldAccessor {

    public static final int defaultScale = 6;
    public static final double defaultDelta = Math.pow(10, -6) / 2;
    public static final MathContext mc = new MathContext(18);

    public DoubleBinder(Field fld, Method getter, Method setter, String fldName) {
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
        if (field instanceof FieldSimple) { // otherwise that would be FieldJava
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
                             String fldPrefix, DocumentUpdateImpl update, DocumentRef docId, Object outerStructure, final Result result) throws Exception {
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
            ok = node.isNull() && nullable;

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
            if (update != null) {
                update.wasUpdate = true;
                if (!derived) {
                    if (update.undoNode != null && !update.undoNode.has(fldName))
                        if (t == null) update.undoNode.putNull(fldName);
                        else update.undoNode.put(fldName,
                                new BigDecimal(t, DoubleBinder.getProperMathContext(t, scale)));
                    if (update.changesNode != null)
                        if (v == null) update.changesNode.putNull(fldName);
                        else update.changesNode.put(fldName,
                                new BigDecimal(v, DoubleBinder.getProperMathContext(v, scale)));
                }
            }
        }
    }

    public void copyToJson(Object obj, Template template, ObjectNode out, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {
        final Double v = (Double) getter.invoke(obj);
        if (v == null)
            out.putNull(fldName);
        else {
            final BigDecimal bigDecimal = new BigDecimal(v, DoubleBinder.getProperMathContext(v, scale));
            out.put(fldName, bigDecimal);
        }
    }

    private final static MathContext[] mathContexts = new MathContext[]{
            new MathContext(0),
            new MathContext(1),
            new MathContext(2),
            new MathContext(3),
            new MathContext(4),
            new MathContext(5),
            new MathContext(6),
            new MathContext(7),
            new MathContext(8),
            new MathContext(9),
            new MathContext(10),
            new MathContext(11),
            new MathContext(12),
            new MathContext(13),
            new MathContext(14),
            new MathContext(15),
            new MathContext(16),
            new MathContext(17),
            new MathContext(18)
    };

    public static MathContext getProperMathContext(double v, int scale) {
        final int t = (int) Math.floor(Math.log10(v) + 1.0 + scale);
        return mathContexts[t < 0 ? 1 : t > 18 ? 18 : t];
    }
}
