package code.docflow.yaml.compositeKeyHandlers;

import code.controlflow.Result;
import code.docflow.model.*;
import code.docflow.yaml.CompositeKeyHandler;
import code.docflow.yaml.YamlMessages;
import code.docflow.yaml.YamlParser;

import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class TemplateFieldCompositeKeyHandler implements CompositeKeyHandler<String, TemplateField> {

    public static Pattern fieldNameAndPattern = Pattern.compile("^\\s*(\\w*)\\s+(\\w*)\\s*$");

    @Override
    public TemplateField parse(String value, HashSet<String> accessedFields, Class collectionType, YamlParser parser, Result result) {
        final Matcher matcher = fieldNameAndPattern.matcher(value);
        if (!matcher.find()) {
            result.addMsg(YamlMessages.error_InvalidFieldDescription, parser.getSavedFilePosition(), parser.getSavedValue());
            return null;
        }

        final TemplateField fld = new TemplateField();
        fld.name = matcher.group(1);
        fld.template = matcher.group(2);

        return fld;
    }

    @Override
    public String key(TemplateField fld) {
        return fld.name.toUpperCase();
    }
}
