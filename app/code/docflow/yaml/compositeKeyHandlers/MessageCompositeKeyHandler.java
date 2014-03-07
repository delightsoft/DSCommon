package code.docflow.yaml.compositeKeyHandlers;

import code.controlflow.Result;
import code.docflow.model.Message;
import code.docflow.yaml.CompositeKeyHandler;
import code.docflow.yaml.YamlMessages;
import code.docflow.yaml.YamlParser;
import com.google.common.base.Strings;

import java.util.HashSet;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class MessageCompositeKeyHandler implements CompositeKeyHandler<String, Message> {

    // Example: error message(param1, param2)
    // TODO: Fix regex.  Works wrong i.e.: '- updateSucceeded info:', but works right for '- updateSucceeded() info:'
    public static Pattern messageKeyAndParams = Pattern.compile("^([^\\(]*)(\\(([^\\)]*)\\))?\\s*(\\S*)");

    @Override
    public Message parse(String value, HashSet<String> accessedFields, Class collectionType, YamlParser parser, Result result) {
        final Matcher matcher = messageKeyAndParams.matcher(value);
        if (!matcher.find()) {
            result.addMsg(YamlMessages.error_InvalidMessageDescription, parser.getSavedFilePosition(), parser.getSavedValue());
            parser.skipNextValue();
            return null;
        }

        final Message msg = new Message();

        msg.key = matcher.group(1).trim();
        accessedFields.add("KEY");

        if (!Strings.isNullOrEmpty(matcher.group(3))) {
            final String[] params = matcher.group(3).split(",");
            if (params.length > 0) {
                msg.params = new TreeMap<String, String>();
                for (int i = 0; i < params.length; i++) {
                    String param = params[i];
                    msg.params.put(param.trim(), "%" + (i + 1) + "$s");
                }
            }
        }

        if (Strings.isNullOrEmpty(matcher.group(4)))
            msg.type = Message.Type.ERROR;
        else {
            try {
                msg.type = Message.Type.valueOf(matcher.group(4).toUpperCase());
            } catch (IllegalArgumentException e) {
                result.addMsg(YamlMessages.error_UnknownMessageType, parser.getSavedFilePosition(), matcher.group(4));
                parser.skipNextValue();
                return null;
            }
        }

        return msg;
    }

    @Override
    public String key(Message msg) {
        return msg.key.toUpperCase();
    }
}
