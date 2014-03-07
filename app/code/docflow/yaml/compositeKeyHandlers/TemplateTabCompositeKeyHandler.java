package code.docflow.yaml.compositeKeyHandlers;

import code.controlflow.Result;
import code.docflow.model.TemplateField;
import code.docflow.model.TemplateTab;
import code.docflow.yaml.CompositeKeyHandler;
import code.docflow.yaml.YamlMessages;
import code.docflow.yaml.YamlParser;

import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class TemplateTabCompositeKeyHandler implements CompositeKeyHandler<String, TemplateTab> {

    public static Pattern fieldNameAndPattern = Pattern.compile("^\\s*(\\w*)\\s+(\\w*).(\\w*)\\s*$");

    @Override
    public TemplateTab parse(String value, HashSet<String> accessedFields, Class collectionType, YamlParser parser, Result result) {
        final Matcher matcher = fieldNameAndPattern.matcher(value);
        if (!matcher.find()) {
            result.addMsg(YamlMessages.error_InvalidFieldDescription, parser.getSavedFilePosition(), parser.getSavedValue());
            return null;
        }

        final TemplateTab fld = new TemplateTab();
        fld.name = matcher.group(1);
        fld.docType = matcher.group(2);
        fld.template = matcher.group(3);

        return fld;
    }

    @Override
    public String key(TemplateTab fld) {
        return fld.name.toUpperCase();
    }
}
