package code.docflow.yaml.compositeKeyHandlers;

import code.docflow.controlflow.Result;
import code.docflow.model.HtmlElement;
import code.docflow.yaml.CompositeKeyHandler;
import code.docflow.yaml.YamlParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class HtmlElementCompositeKeyHandler implements CompositeKeyHandler<Integer, HtmlElement> {

    // <field name>(.<clas name>)* - field with optional classes
    //      f1
    //
    //      f1 .smaf1.smallll .bigOne
    // _<field group name>(.<clas name>)* - field's group with optional classes, which suppose to set on each field
    // .<class name>(.<class name>)* - it's div with classes
    // #{<tag name with dots> <optional params> <optional '/'>} - it's Play tag


    public static final String CSS_CLASS = "(?:\\.([a-zA-Z]+[_a-zA-Z0-9-]*))";

    public static final String FIELD = "([\\w&&[^_]]\\w*(?:\\.\\w+)*)";
    public static final String GROUP_NAME = "(?:_(\\w+))";
    public static final String GROUP_EXPR = "(?:_\\{(.*?)\\})";
    public static final String DIV = CSS_CLASS;
    public static final String TAG = "#\\{\\s*(\\w+(?:\\.\\w+)*)";

    public static final Pattern regexSelect = Pattern.compile("^\\s*" + FIELD + "|" + GROUP_NAME + "|" + GROUP_EXPR + "|" + DIV + "|" + TAG + "\\s*");
    public static final Pattern regexClass = Pattern.compile("\\s*" + CSS_CLASS);
    public static final Pattern regexTagArgs = Pattern.compile("(.*?)\\s*\\/?\\}\\s*$");

    @Override
    public HtmlElement parse(String value, HashSet<String> accessedFields, Class collectionType, YamlParser parser, Result result) {
        // TODO: 2 parse content
        // TODO: 3 link back to source code
        // TODO: 4 compile to groovy
        // TODO: 5 test

        final Matcher select = regexSelect.matcher(value);
        if (!select.find())
            return null;

        final HtmlElement res = new HtmlElement();

        res.line = parser.getSavedFilePosition().line;

        if (select.group(1) != null) { // Field
            res.type = HtmlElement.Type.FIELD;
            res.value = select.group(1);
            parseClasses(value.substring(select.end()), null, res);
        } else if (select.group(2) != null) { // Field's group
            res.type = HtmlElement.Type.GROUP_FIELD;
            res.value = select.group(2);
            parseClasses(value.substring(select.end()), null, res);
        } else if (select.group(3) != null) { // Field's group
            res.type = HtmlElement.Type.GROUP_EXPR;
            res.value = select.group(3);
            parseClasses(value.substring(select.end()), null, res);
        } else if (select.group(4) != null) { // Div (other name is Block)
            res.type = HtmlElement.Type.DIV;
            res.value = null;
            parseClasses(value.substring(select.end()), select.group(4), res);
        } else if (select.group(5) != null) { // Tag
            res.type = HtmlElement.Type.TAG;
            res.value = select.group(5);
            final Matcher argMatcher = regexTagArgs.matcher(value.substring(select.end()));
            if (argMatcher.find()) {
                res.args = argMatcher.group(1);
                // copied from play.templates.GroovyTemplateCompiler(254)
                if (!res.args.matches("^[_a-zA-Z0-9]+\\s*:.*$"))
                    res.args = "arg:" + res.args;
            }
        } else {
            // unexpected
            return null;
        }

        return res;
    }

    private void parseClasses(String rest, String firstClass, HtmlElement res) {
        final Matcher clazz = regexClass.matcher(rest);
        ArrayList<String> classes = null;
        StringBuilder sb = new StringBuilder();
        if (firstClass != null)
            sb.append(firstClass);
        while (clazz.find()) {
            if (sb.length()> 0)
                sb.append(" ");
            sb.append(clazz.group(1));
        }
        res.classes = sb.toString();
    }

    @Override
    public Integer key(HtmlElement fld) {
        return fld.line;
    }
}
