package code.docflow.yaml.builders;

import code.controlflow.Result;
import code.docflow.collections.ItemsIndexedCollection;
import code.docflow.yaml.CompositeKeyHandler;
import code.docflow.yaml.YamlParser;
import code.docflow.yaml.annotations.WithCompositeKeyHandler;
import code.docflow.yaml.converters.ArrayConverter;
import code.docflow.yaml.converters.ItemsMapConverter;
import code.docflow.yaml.converters.PrimitiveTypeConverter;
import code.docflow.yaml.converters.SequenceConverter;
import code.utils.Builder;
import code.utils.PrimitiveType;
import code.utils.TypeBuildersFactory;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public abstract class ItemBuilder extends Builder {

    private static String format;

    public abstract Object build(YamlParser parser, Result result);

    public abstract String getTypeTitle();

    protected ItemBuilder() {
    }

    @Override
    protected void init() {
        // nothing
    }

    public static final TypeBuildersFactory<ItemBuilder> factory = new TypeBuildersFactory<ItemBuilder>() {
        @Override
        public ItemBuilder newInstance(TypeDescription typeDesc) {
            return ItemBuilder.getTypeBuilder(typeDesc);
        }
    };

    protected static ItemBuilder getTypeBuilder(TypeBuildersFactory.TypeDescription typeDesc) {

        final Class type = typeDesc.type;

        if (PrimitiveType.get(type) != PrimitiveType.NotPrimitiveOrPrimitiveWrapper || type.isEnum() || type == Object.class) {
            final PrimitiveTypeConverter converter = PrimitiveTypeConverter.getConverter(type);
            if (converter == null)
                throw new UnsupportedOperationException(String.format("Type %s is not supported by ItemBuilder", type.getName()));
            return new PrimitiveTypeBuilder(converter);
        }

        if (type.isArray()) {
            final SequenceConverter arrayConverter = ArrayConverter.factory.get(type.getComponentType(), true);
            if (arrayConverter == null)
                throw new UnsupportedOperationException(String.format("No array converter for type %s", type.getName()));
            final ItemBuilder itemBuilder = factory.get(type.getComponentType(), true);
            return new SequenceBuilder(itemBuilder, arrayConverter);
        }

        if (type == ItemsIndexedCollection.class) {
            // basically same logic as for LinkedHashMap below
            if (typeDesc.parameters == null)
                throw new UnsupportedOperationException(String.format("Required parameters for generic type", typeDesc.toString()));

            final ItemBuilder itemBuilder = factory.get(typeDesc.parameters[0], true);
            final ItemsMapConverter typeConverter = new ItemsMapConverter();

            if (itemBuilder instanceof CompositeKeyObjectBuilder)
                return new CompositeKeyMapBuilder((CompositeKeyObjectBuilder) itemBuilder, String.class, typeConverter);

            return new MapBuilder(itemBuilder, null, typeConverter);
        }

        if (Collection.class.isAssignableFrom(type)) {
            throw new UnsupportedOperationException(String.format("Type %s is not supported by ItemBuilder", typeDesc.toString()));
        }

        if (Map.class.isAssignableFrom(type)) {
            if (LinkedHashMap.class.isAssignableFrom(type)) {
                if (typeDesc.parameters == null)
                    throw new UnsupportedOperationException(String.format("Required parameters for generic type", typeDesc.toString()));

                final PrimitiveTypeConverter keyConverter = PrimitiveTypeConverter.getConverter((Class) typeDesc.parameters[0]);
                final ItemBuilder itemBuilder = factory.get(typeDesc.parameters[1], true);

                if (itemBuilder instanceof CompositeKeyObjectBuilder)
                    return new CompositeKeyMapBuilder((CompositeKeyObjectBuilder) itemBuilder, (Class) typeDesc.parameters[0], null);

                return new MapBuilder(itemBuilder, keyConverter, null);
            }
            throw new UnsupportedOperationException(String.format("Type %s is not supported by ItemBuilder", typeDesc.toString()));
        }

        final WithCompositeKeyHandler keyHandler = (WithCompositeKeyHandler) type.getAnnotation(WithCompositeKeyHandler.class);
        if (keyHandler != null) {
            if (keyHandler.value() == null || WithCompositeKeyHandler.class.isAssignableFrom(keyHandler.value()))
                throw new UnsupportedOperationException(
                        String.format("Value() in WithCompositeKeyHandler annotation on type %s must contain " +
                                "subclass of WithCompositeKeyHandler", typeDesc.toString()));
            CompositeKeyHandler h = null;
            try {
                h = (CompositeKeyHandler) keyHandler.value().newInstance();
            } catch (InstantiationException e) {
                throw new UnsupportedOperationException(e);
            } catch (IllegalAccessException e) {
                throw new UnsupportedOperationException(e);
            }
            return new CompositeKeyObjectBuilder(type, h);
        }

        return new ObjectBuilder(type);
    }
}
