package code.docflow.yaml.compositeKeyHandlers;

import code.docflow.controlflow.Result;
import code.docflow.collections.Item;
import code.docflow.yaml.CompositeKeyHandler;
import code.docflow.yaml.YamlParser;
import code.docflow.yaml.annotations.FlagName;
import code.docflow.yaml.annotations.NotYamlField;
import code.docflow.utils.Builder;
import code.docflow.utils.TypeBuildersFactory;
import play.exceptions.UnexpectedException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class ItemCompositeKeyHandler implements CompositeKeyHandler<String, Item> {

    // Pattern: item-name ....
    static Pattern keyPattern = Pattern.compile("^\\s*(\\S*)(\\s+(.*))?$");

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
            for (Field fld : fields) {
                final Class<?> fldType = fld.getType();
                final int modifiers = fld.getModifiers();
                if (Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers) &&
                        !fld.isAnnotationPresent(NotYamlField.class) &&
                        (boolean.class.isAssignableFrom(fldType) || Boolean.class.isAssignableFrom(fldType))) {
                    FlagName flagName = fld.getAnnotation(FlagName.class);
                    flags.put(flagName != null ? flagName.value() : fld.getName(), fld);
                }
            }
        }
    }

    public Item parse(String value, final HashSet<String> accessedFields, Class collectionType, YamlParser parser, final Result result) {
        Matcher matcher = keyPattern.matcher(value.trim());
        matcher.find();
        try {
            final Item item = (Item) collectionType.newInstance();
            item.name = matcher.group(1);
            accessedFields.add("NAME");
            if (matcher.group(3) != null)
                FieldCompositeKeyHandler.processFlags(item, matcher.group(3), accessedFields, parser, result);
            return item;
        } catch (InstantiationException e) {
            throw new UnexpectedException(e);
        } catch (IllegalAccessException e) {
            throw new UnexpectedException(e);
        }
    }

    @Override
    public String key(Item item) {
        return item.name.toUpperCase();
    }
}
