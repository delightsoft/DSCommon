package code.docflow.yaml.compositeKeyHandlers;

import code.controlflow.Result;
import code.docflow.model.Template;
import code.docflow.yaml.CompositeKeyHandler;
import code.docflow.yaml.YamlMessages;
import code.docflow.yaml.YamlParser;

import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class TemplateCompositeKeyHandler implements CompositeKeyHandler<String, Template> {

    // Example: f1 type() attr1 attr2
    public static Pattern nameAndFlags = Pattern.compile("^\\s*(\\S*)(\\s+(.*))?$");

    @Override
    public Template parse(String value, HashSet<String> accessedFields, Class collectionType, YamlParser parser, Result result) {
        final Matcher matcher = nameAndFlags.matcher(value);
        if (!matcher.find()) {
            result.addMsg(YamlMessages.error_InvalidFieldDescription, parser.getSavedFilePosition(), parser.getSavedValue());
            return null;
        }

        Template res = new Template();
        res.name = matcher.group(1).trim();

        // keep yaml builder accessed fields info with Template for later use
        res.accessedFields = accessedFields;

        if (matcher.group(3) != null)
            FieldCompositeKeyHandler.processFlags(res, matcher.group(3), accessedFields, parser, result);

        return res;
    }

    @Override
    public String key(Template fld) {
        return fld.name.toUpperCase();
    }
}
