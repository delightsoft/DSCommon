package code.docflow.yaml.compositeKeyHandlers;

import code.controlflow.Result;
import code.docflow.collections.Item;
import code.docflow.yaml.CompositeKeyHandler;
import code.docflow.yaml.YamlMessages;
import code.docflow.yaml.YamlParser;
import code.docflow.yaml.annotations.NotYamlField;
import code.utils.Builder;
import code.utils.TypeBuildersFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.TreeMap;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class ItemCompositeKeyHandler implements CompositeKeyHandler<String, Item> {

    public static final TypeBuildersFactory<FlagsAccessor> flagsAccessorsFactory = new TypeBuildersFactory<FlagsAccessor>() {
        @Override
        public FlagsAccessor newInstance(TypeDescription typeDesc) {
            checkState(typeDesc.parameters == null);
            return new FlagsAccessor(typeDesc.type);
        }
    };

    public static class FlagsAccessor extends Builder {

        public final Class<?> type;
        public final TreeMap<String, Field> flags = new TreeMap<String, java.lang.reflect.Field>();

        protected FlagsAccessor(Class<?> type) {
            this.type = type;
        }

        @Override
        protected void init() {
            final java.lang.reflect.Field[] fields = type.getFields();
            for (int i = 0; i < fields.length; i++) {
                final java.lang.reflect.Field fld = fields[i];
                final Class<?> fldType = fld.getType();
                final int modifiers = fld.getModifiers();
                if (Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers) &&
                        !fld.isAnnotationPresent(NotYamlField.class) &&
                        (boolean.class.isAssignableFrom(fldType) || Boolean.class.isAssignableFrom(fldType))) {
                    flags.put(fld.getName(), fld);
                }
            }
        }
    }

    public Item parse(String value, final HashSet<String> accessedFields, Class collectionType, YamlParser parser, final Result result) {
        checkArgument(collectionType != null && Item.class.isAssignableFrom(collectionType));
        try {
            final Item item = (Item) collectionType.newInstance();
            String[] words = value.trim().split(" ");
            item.name = words[0];
            accessedFields.add("NAME");
            // process flags
            final FlagsAccessor flagsAccessor = flagsAccessorsFactory.get(collectionType);
            for (int i = 1; i < words.length; i++) {
                final String flag = words[i];
                if (flag.isEmpty())
                    continue;
                final java.lang.reflect.Field f = flagsAccessor.flags.get(flag);
                if (f == null) {
                    result.addMsg(YamlMessages.error_UnknownFlag, parser.getSavedFilePosition(), flag);
                    continue;
                }
                try {
                    f.setBoolean(item, true);
                    accessedFields.add(flag);
                } catch (IllegalAccessException e) {
                    // unexpected
                }
            }
            return item;
        } catch (InstantiationException e) {
            checkState(false);
        } catch (IllegalAccessException e) {
            checkState(false);
        }
        return null;
    }

    @Override
    public String key(Item item) {
        return item.name.toUpperCase();
    }
}
