package code.docflow.yaml.compositeKeyHandlers;

import code.docflow.controlflow.Result;
import code.docflow.model.DocType;
import code.docflow.model.Role;
import code.docflow.model.RootElement;
import code.docflow.yaml.CompositeKeyHandler;
import code.docflow.yaml.YamlMessages;
import code.docflow.yaml.YamlParser;

import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class RootElementCompositeKeyHandler implements CompositeKeyHandler<String, RootElement> {

    // Pattern: object-type object-name ....
    static Pattern keyPattern = Pattern.compile("^\\s*(\\S*)\\s+(\\S*)(\\s+(.*))?$");

    @Override
    public RootElement parse(String value, final HashSet<String> accessedFields, Class collectionTypeClass, YamlParser parser, final Result result) {
        checkNotNull(accessedFields);

        Matcher matcher = keyPattern.matcher(value.trim());
        if (!matcher.find()) {
            result.addMsg(YamlMessages.error_InvalidRootElementKey, parser.getSavedFilePosition(), parser.getSavedValue());
            return null;
        }
        String type = matcher.group(1);
        String name = matcher.group(2);

        RootElement res = null;
        if ("DOCUMENT".equalsIgnoreCase(type))
            res = new DocType();
        else if ("ROLE".equalsIgnoreCase(type))
            res = new Role();
        else {
            result.addMsg(YamlMessages.error_InvalidRootElementKey, parser.getSavedFilePosition(), parser.getSavedValue());
            return null;
        }

        if (res != null)
            res.name = name;

        accessedFields.add("NAME");

        if (matcher.group(4) != null)
            FieldCompositeKeyHandler.processFlags(res, matcher.group(4), accessedFields, parser, result);

        return res;
    }

    @Override
    public String key(RootElement re) {
        checkNotNull(re);
        final String res = expectedBegging(re);
        if (res == null)
            throw new UnsupportedOperationException(String.format("Unexpected type of re %s", re.getClass().getName()));
        return res.toUpperCase();
    }

    public static String expectedBegging(RootElement re) {
        if (re instanceof DocType)
            return String.format("document %s", re.name);
        else if (re instanceof Role)
            return String.format("role %s", re.name);
        return null;
    }
}
