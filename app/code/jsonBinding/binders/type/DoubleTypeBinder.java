package code.jsonBinding.binders.type;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.model.Template;
import code.jsonBinding.binders.field.DoubleBinder;
import com.fasterxml.jackson.core.JsonGenerator;

import java.math.BigDecimal;
import java.util.Stack;

public class DoubleTypeBinder extends TypeBinder {

    protected DoubleTypeBinder() {
    }

    @Override
    protected void init() {
        // nothing
    }

    @Override
    public void copyToJson(Object value, Template template, JsonGenerator generator, Stack<String> stack, int mode) throws Exception {
        if (value == null)
            generator.writeNull();
        else {
            final Double v = (Double) value;
            // Note: JsonPrecision annotation is inaccessable from here.  So can only stick to default precision.
            final BigDecimal bigDecimal = new BigDecimal(v, DoubleBinder.getProperMathContext(v, DoubleBinder.defaultScale));
            generator.writeNumber(bigDecimal);
        }
    }
}
