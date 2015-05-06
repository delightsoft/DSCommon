package code.docflow.jsonBinding.binders.type;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.model.Template;
import code.docflow.jsonBinding.binders.time.type.*;
import code.docflow.utils.Builder;
import code.docflow.utils.PrimitiveType;
import code.docflow.utils.TypeBuildersFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Multimap;

import java.util.List;
import java.util.Map;
import java.util.Stack;

public abstract class TypeBinder extends Builder {

    @Override
    protected void init() {
        // nothing
    }

    public static TypeBuildersFactory<TypeBinder> factory = new TypeBuildersFactory<TypeBinder>() {
        public TypeBinder newInstance(TypeDescription typeDesc) {
            PrimitiveType primitiveType = PrimitiveType.get(typeDesc.type);
            switch (primitiveType) {
                case BooleanType:
                    return new BooleanTypeBinder();
                case TimeType:
                    return new TimeTypeBinder();
                case IntegerType:
                    return new IntegerTypeBinder();
                case LongType:
                    return new LongTypeBinder();
                case FloatType:
                    return new FloatTypeBinder();
                case DoubleType:
                    return new DoubleTypeBinder();
                case DateType:
                    return new DateTypeBinder();
                case DateTimeType:
                    return new DateTimeTypeBinder();
                case LocalDateTimeType:
                    return new LocalDateTimeTypeBinder();
                case IntervalType:
                    return new IntervalTypeBinder();
                case PeriodType:
                    return new PeriodTypeBinder();
                case DurationType:
                    return new DurationTypeBinder();
                case UUIDType:
                    return new UUIDTypeBinder();
                case NotPrimitiveOrPrimitiveWrapper:
                    if (List.class.isAssignableFrom(typeDesc.type))
                        return new ListTypeBinder(typeDesc.parameters);
                    if (Map.class.isAssignableFrom(typeDesc.type))
                        return new MapTypeBinder(typeDesc.parameters);
                    if (Multimap.class.isAssignableFrom(typeDesc.type))
                        return new MultiMapTypeBinder(typeDesc.parameters);
                    if (JsonNode.class.isAssignableFrom(typeDesc.type))
                        return new JsonNodeTypeBinder();
                    return new ObjectTypeBinder();
            }
            return new StringTypeBinder();
        }
    };

    public abstract com.fasterxml.jackson.databind.JsonNode copyToJson(Object value, Template template, Stack<String> stack, int mode) throws Exception;
}
