package code.docflow.play;

import code.docflow.jsonBinding.JsonBinding;
import com.fasterxml.jackson.databind.JsonNode;
import play.data.binding.Global;
import play.data.binding.TypeBinder;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
@Global
public class JsonNodePlayBinder implements TypeBinder<JsonNode> {
    @Override
    public Object bind(String name, Annotation[] annotations, String value, Class actualClass, Type genericType) throws Exception {
        return JsonBinding.toJsonNode(value);
    }
}
