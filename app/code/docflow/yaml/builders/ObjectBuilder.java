package code.docflow.yaml.builders;

import code.controlflow.Result;
import code.docflow.yaml.YamlMessages;
import code.docflow.yaml.YamlParser;
import code.docflow.yaml.annotations.NotYamlField;
import code.docflow.yaml.annotations.TargetField;
import code.utils.NamesUtil;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.ScalarEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.LinkedHashMap;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class ObjectBuilder extends ItemBuilder {

    final Class type;
    final LinkedHashMap<String, FieldAccessor> builders = new LinkedHashMap<String, FieldAccessor>();
    FieldAccessor targetFieldBuilder;

    public static class FieldAccessor {
        public final Field fld;
        public final String fldName;
        public final ItemBuilder itemBuilder;

        public FieldAccessor(Field fld, String fldName, ItemBuilder itemBuilder) {
            this.fld = fld;
            this.fldName = fldName;
            this.itemBuilder = itemBuilder;
        }
    }

    protected ObjectBuilder(Class type) {
        this.type = type;
    }

    @Override
    public void init() {
        final Field[] fl = type.getFields();
        for (int i = 0; i < fl.length; i++) {
            final Field fld = fl[i];

            if (fld.isAnnotationPresent(NotYamlField.class))
                continue;

            final int m = fld.getModifiers();
            if (Modifier.isStatic(m) || !Modifier.isPublic(m))
                continue;

            final ItemBuilder fieldBuilder;
            try {
                fieldBuilder = ItemBuilder.factory.get(fld);
            } catch (Exception e) {
                throw new UnsupportedOperationException(String.format("Failed to get builder for " +
                        "field '%2$s' of type '%1$s'.", fld.getDeclaringClass().getName(), fld.getName()), e);
            }
            final FieldAccessor fldAccessor = new FieldAccessor(fld, fld.getName(), fieldBuilder);
            builders.put(fld.getName(), fldAccessor);

            if (fld.isAnnotationPresent(TargetField.class))
                targetFieldBuilder = fldAccessor;
        }
    }

    @Override
    public String getTypeTitle() {
        return type.getName();
    }

    @Override
    public Object build(final YamlParser parser, final Result result) {
        final HashSet<String> accessFields = new HashSet<String>();
        return build(parser, null, accessFields, result);
    }

    protected Object build(final YamlParser parser, Object res, HashSet<String> accessedFields, final Result result) {

        final Result localResult = new Result();

        // Can be not null for CompositeKey objects
        if (res == null)
            try {
                res = type.newInstance();
            } catch (InstantiationException e) {
                throw new UnsupportedOperationException(e);
            } catch (IllegalAccessException e) {
                throw new UnsupportedOperationException(e);
            }

        // If class has one field annotated by @TargetField
        if (targetFieldBuilder != null) {

            if (accessedFields.contains(targetFieldBuilder.fldName.toUpperCase())) {
                result.addMsg(YamlMessages.error_DuplicatedPropertyAssignment, parser.getSavedFilePosition(), targetFieldBuilder.fldName);
                parser.skipNextValue();
                return res;
            }

            localResult.clear();
            final Object el = targetFieldBuilder.itemBuilder.build(parser, localResult);
            if (localResult.isNotOk())
                if (localResult.getCode() != Result.WrongValue)
                    result.append(localResult);
                else
                    result.addMsg(YamlMessages.error_WrongValueForField,
                            parser.getSavedFilePosition(),
                            targetFieldBuilder.fldName,
                            targetFieldBuilder.itemBuilder.getTypeTitle(),
                            parser.getSavedValue());
            else {
                try {
                    targetFieldBuilder.fld.set(res, el);
                } catch (IllegalAccessException e) {
                    throw new UnsupportedOperationException(e);
                }
            }
            return res;
        }

        // Regular "key: value" syntax
        Event ev = parser.next();
        if (res != null && ev.is(Event.ID.Scalar))
            // Special case - composite key object with semicolon without any prop after.  Example:
            //    document(test):
            //    roles(aaaa):
            return res;

        if (!parser.isExpectedBeginning(Event.ID.MappingStart, result))
            return null;

        while (parser.hasNext()) {
            ev = parser.next();
            if (ev.is(Event.ID.MappingEnd))
                return res;

            String key = ((ScalarEvent) ev).getValue();
            key = NamesUtil.wordsToCamelCase(key);

            parser.savePosition();
            final FieldAccessor fldAccessor = builders.get(key);
            if (fldAccessor == null) {
                result.addMsg(YamlMessages.error_InvalidProperty, parser.getSavedFilePosition(), parser.getSavedValue());
                parser.skipNextValue();
                continue;
            }
            if (accessedFields.contains(key)) {
                result.addMsg(YamlMessages.error_DuplicatedPropertyAssignment, parser.getSavedFilePosition(), key);
                parser.skipNextValue();
                continue;
            }
            accessedFields.add(key.toUpperCase());

            localResult.clear();
            final Object el = fldAccessor.itemBuilder.build(parser, localResult);
            if (localResult.isNotOk())
                if (localResult.getCode() != Result.WrongValue)
                    result.append(localResult);
                else
                    result.addMsg(YamlMessages.error_WrongValueForField,
                            parser.getSavedFilePosition(),
                            fldAccessor.fldName,
                            fldAccessor.itemBuilder.getTypeTitle(),
                            parser.getSavedValue());
            else {
                try {
                    fldAccessor.fld.set(res, el);
                } catch (IllegalAccessException e) {
                    throw new UnsupportedOperationException(e);
                }
            }
        }
        return null;
    }
}
