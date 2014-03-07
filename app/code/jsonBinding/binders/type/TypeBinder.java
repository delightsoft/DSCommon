package code.jsonBinding.binders.type;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.model.Template;
import code.utils.Builder;
import code.utils.PrimitiveType;
import code.utils.TypeBuildersFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.collect.Multimap;

import java.util.List;
import java.util.Map;
import java.util.Stack;

public abstract class TypeBinder extends Builder {

    protected TypeBinder() {
    }

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
                case DateTimeType:
                    return new DateTimeTypeBinder();
                case DoubleType:
                    return new DoubleTypeBinder();
                case FloatType:
                    return new FloatTypeBinder();
                case IntegerType:
                    return new IntegerTypeBinder();
                case LongType:
                    return new LongTypeBinder();
                case NotPrimitiveOrPrimitiveWrapper:
                    if (List.class.isAssignableFrom(typeDesc.type))
                        return new ListTypeBinder(typeDesc.parameters);
                    if (Map.class.isAssignableFrom(typeDesc.type))
                        return new MapTypeBinder(typeDesc.parameters);
                    if (Multimap.class.isAssignableFrom(typeDesc.type))
                        return new MultiMapTypeBinder(typeDesc.parameters);
                    return new ObjectTypeBinder();
            }
            return new StringTypeBinder();
        }
    };

    public abstract void copyToJson(Object value, Template template, JsonGenerator generator, Stack<String> stack, int mode) throws Exception;
}
