package code.docflow.yaml.converters;

import code.docflow.utils.Builder;
import code.docflow.utils.TypeBuildersFactory;

import java.lang.reflect.Array;
import java.util.ArrayList;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class ArrayConverter extends Builder implements SequenceConverter {

    Class elementType;

    public static final TypeBuildersFactory<ArrayConverter> factory = new TypeBuildersFactory<ArrayConverter>() {
        @Override
        public ArrayConverter newInstance(TypeDescription typeDesc) {
            return new ArrayConverter(typeDesc.type);
        }
    };

    protected ArrayConverter(Class elementType) {
        this.elementType = elementType;
    }

    @Override
    protected void init() {
        // nothing
    }

    @Override
    public Object convert(ArrayList al) {
        final Object array = Array.newInstance(elementType, al.size());
        for (int i = 0; i < al.size(); i++)
            Array.set(array, i, al.get(i));
        return array;
    }
}
