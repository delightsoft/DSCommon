package code.jsonBinding.binders.type;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.model.Template;
import com.fasterxml.jackson.core.JsonGenerator;

import java.util.Stack;

public class BooleanTypeBinder extends TypeBinder {

    @Override
    protected void init() {
        // nothing
    }

    @Override
    public void copyToJson(Object value, Template template, JsonGenerator generator, Stack<String> stack, int mode) throws Exception {
        if (value == null)
            generator.writeNull();
        else
            generator.writeBoolean((Boolean) value);
    }
}
