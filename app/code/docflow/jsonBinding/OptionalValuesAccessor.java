package code.docflow.jsonBinding;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.jsonBinding.annotations.doc.JsonTemplate;
import code.docflow.docs.Document;
import code.docflow.utils.BitArray;
import code.docflow.utils.PrimitiveType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.joda.time.DateTime;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Stack;

public class OptionalValuesAccessor<D extends Document> extends JsonTypeBinder.FieldAccessor {

    private String newTemplate;

    public OptionalValuesAccessor(Field fld, Method getter, Method setter, String fldName) {
        super(fld, getter, setter, fldName);

        Preconditions.checkArgument(Map.class.isAssignableFrom(fld.getType()));
        Type genericType = fld.getGenericType();
        Preconditions.checkArgument(genericType instanceof ParameterizedType);
        Preconditions.checkArgument(String.class.isAssignableFrom((Class)((ParameterizedType) genericType).getActualTypeArguments()[0]));

        JsonTemplate annotation = fld.getAnnotation(JsonTemplate.class);
        newTemplate = annotation == null ? "list" : annotation.value();
    }

    @Override
    public void copyToJson(Object obj, Template template, ObjectNode out, Stack<String> stack, int mode,
                           DocumentAccessActionsRights rights, BitArray mask) throws Exception {
        Object fldValue = getter.invoke(obj);
        for (Map.Entry<String, Object> entry : ((Map<String, Object>) fldValue).entrySet())
            if (entry.getValue() == null)
                out.putNull(entry.getKey());
            else {
                String key = entry.getKey();
                Object value = entry.getValue();
                PrimitiveType valueType = PrimitiveType.get(value.getClass());
                switch (valueType) {
                    case BooleanType:
                        out.put(key, (Boolean) value);
                        break;
                    case IntegerType:
                        out.put(key, (Integer) value);
                        break;
                    case LongType:
                        out.put(key, (Long) value);
                        break;
                    case FloatType:
                        out.put(key, (Float) value);
                        break;
                    case DoubleType:
                        out.put(key, (Double) value);
                        break;
                    case DateTimeType:
                        out.put(key, ((DateTime) value).getMillis());
                        break;
                    case NotPrimitiveOrPrimitiveWrapper:
                        out.put(key, JsonTypeBinder.factory.get(value.getClass()).toJson(value, Strings.isNullOrEmpty(newTemplate) ? newTemplate : template.name, stack, mode, rights, mask));
                        break;
                    default:
                        out.put(key, value.toString());
                }
            }
    }
}
