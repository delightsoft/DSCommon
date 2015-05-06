package code.docflow.yaml.compositeKeyHandlers;

import code.docflow.controlflow.Result;
import code.docflow.model.RoleRight;
import code.docflow.yaml.CompositeKeyHandler;
import code.docflow.yaml.YamlMessages;
import code.docflow.yaml.YamlParser;

import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class RoleRightCompositeKeyHandler implements CompositeKeyHandler<String, RoleRight> {

    // Pattern: rights [relations]
    static Pattern keyPattern = Pattern.compile("^([^\\[\\-]*)(\\[([^\\]]*)\\])?");

    public RoleRight parse(String value, final HashSet<String> accessedFields, Class collectionType, YamlParser parser, final Result result) {
        final Matcher matcher = keyPattern.matcher(value);
        if (!matcher.find()) {
            result.addMsg(YamlMessages.error_InvalidRoleRightFormat, parser.getSavedFilePosition(), parser.getSavedValue());
            return null;
        }

        final RoleRight rr = new RoleRight();
        rr.name = matcher.group(1).trim();
        accessedFields.add("NAME");

        final String relations = matcher.group(3);
        if (relations != null) {
            final String[] words = matcher.group(3).split(",");
            if (words.length > 0) {
                rr.relations = new String[words.length];
                for (int i = 0; i < words.length; i++) {
                    rr.relations[i] = words[i].trim();
                }
            }
        }
        accessedFields.add("RELATIONS");

        return rr;
    }

    @Override
    public String key(RoleRight rr) {
        return rr.name.toUpperCase();
    }
}
