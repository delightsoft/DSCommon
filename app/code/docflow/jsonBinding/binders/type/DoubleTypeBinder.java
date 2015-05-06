package code.docflow.jsonBinding.binders.type;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.model.Template;
import code.docflow.jsonBinding.binders.field.DoubleBinder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.math.BigDecimal;
import java.util.Stack;

public class DoubleTypeBinder extends TypeBinder {
    @Override
    public JsonNode copyToJson(Object value, Template template, Stack<String> stack, int mode) throws Exception {
        if (value == null) return JsonNodeFactory.instance.nullNode();
        else {
            final Double v = (Double) value;
            return JsonNodeFactory.instance.numberNode(new BigDecimal(v, DoubleBinder.getProperMathContext(v, DoubleBinder.defaultScale)));
        }
    }
}
