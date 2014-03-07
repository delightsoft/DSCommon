package code.jsonBinding;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.jsonBinding.annotations.doc.JsonTemplate;
import code.models.Document;
import code.utils.BitArray;
import code.utils.PrimitiveType;
import com.fasterxml.jackson.core.JsonGenerator;
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
        newTemplate = annotation.value();
    }

    @Override
    public void copyToJson(Object obj, Template template, JsonGenerator generator, Stack<String> stack, int mode,
                           DocumentAccessActionsRights rights, BitArray mask) throws Exception {
        Object fldValue = getter.invoke(obj);
        for (Map.Entry<String, Object> entry : ((Map<String, Object>) fldValue).entrySet())
            if (entry.getValue() == null)
                generator.writeNullField(entry.getKey());
            else {
                String key = entry.getKey();
                Object value = entry.getValue();
                PrimitiveType valueType = PrimitiveType.get(value.getClass());
                switch (valueType) {
                    case BooleanType:
                        generator.writeBooleanField(key, (Boolean) value);
                        break;
                    case IntegerType:
                        generator.writeNumberField(key, (Integer) value);
                        break;
                    case LongType:
                        generator.writeNumberField(key, (Long) value);
                        break;
                    case FloatType:
                        generator.writeNumberField(key, (Float) value);
                        break;
                    case DoubleType:
                        generator.writeNumberField(key, (Double) value);
                        break;
                    case DateTimeType:
                        generator.writeNumberField(key, ((DateTime) value).getMillis());
                        break;
                    case NotPrimitiveOrPrimitiveWrapper:
                        generator.writeFieldName(key);
                        JsonTypeBinder.factory.get(value.getClass()).toJson(value, Strings.isNullOrEmpty(newTemplate) ? newTemplate : template.name, generator, stack, mode, rights, mask);
                        break;
                    default:
                        generator.writeStringField(key, value.toString());
                }
            }
    }
}
