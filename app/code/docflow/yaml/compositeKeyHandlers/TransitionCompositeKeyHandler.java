package code.docflow.yaml.compositeKeyHandlers;

import code.docflow.controlflow.Result;
import code.docflow.model.Transition;
import code.docflow.yaml.CompositeKeyHandler;
import code.docflow.yaml.YamlMessages;
import code.docflow.yaml.YamlParser;

import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class TransitionCompositeKeyHandler implements CompositeKeyHandler<String, Transition> {

    // Pattern: action [preconditions] -> new-state ... , where '[preconditions]' are optional
    static Pattern keyPattern = Pattern.compile("^([^\\[\\-]*)(\\[([^\\]]*)\\])?\\s*->\\s*([^\\s]*)");

    public static final TransitionCompositeKeyHandler INSTANCE = new TransitionCompositeKeyHandler();

    public Transition parse(String value, final HashSet<String> accessedFields, Class collectionType, YamlParser parser, final Result result) {
        final Matcher matcher = keyPattern.matcher(value);
        if (!matcher.find()) {
            result.addMsg(YamlMessages.error_InvalidTransitionFormat, parser.getSavedFilePosition(), parser.getSavedValue());
            return null;
        }

        final Transition tran = new Transition();
        tran.name = matcher.group(1).trim();
        accessedFields.add("NAME");

        final String predicates = matcher.group(3);
        if (predicates != null) {
            final String[] words = matcher.group(3).split(",");
            if (words.length > 0) {
                tran.preconditions = new String[words.length];
                for (int i = 0; i < words.length; i++) {
                    tran.preconditions[i] = words[i].trim();
                }
            }
        }
        accessedFields.add("PRECONDITIONS");

        tran.endState = matcher.group(4).trim();
        accessedFields.add("ENDSTATE");

        return tran;
    }

    @Override
    public String key(Transition transition) {
        if (transition.preconditions == null)
            return transition.name.toUpperCase();

        StringBuilder sb = new StringBuilder();
        sb.append(transition.name);
        sb.append("[");
        for (int i = 0; i < transition.preconditions.length; i++) {
            if (i > 0)
                sb.append(",");
            sb.append(transition.preconditions[i]);
        }
        sb.append("]");
        transition.keyInNormalCase = sb.toString();
        return transition.keyInNormalCase.toUpperCase();
    }
}
