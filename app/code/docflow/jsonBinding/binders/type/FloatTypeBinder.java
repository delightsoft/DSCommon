package code.docflow.jsonBinding.binders.type;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.model.Template;
import code.docflow.jsonBinding.binders.field.DoubleBinder;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.math.BigDecimal;
import java.util.Stack;

public class FloatTypeBinder extends TypeBinder {
    @Override
    public com.fasterxml.jackson.databind.JsonNode copyToJson(Object value, Template template, Stack<String> stack, int mode) throws Exception {
        if (value == null) return JsonNodeFactory.instance.nullNode();
        else {
            final Float v = (Float) value;
            return JsonNodeFactory.instance.numberNode(new BigDecimal(v, DoubleBinder.getProperMathContext(v, DoubleBinder.defaultScale)));
        }
    }
}
