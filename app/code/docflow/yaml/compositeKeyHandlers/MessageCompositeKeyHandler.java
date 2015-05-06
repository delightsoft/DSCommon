package code.docflow.yaml.compositeKeyHandlers;

import code.docflow.controlflow.Result;
import code.docflow.controlflow.ResultCode;
import code.docflow.model.Message;
import code.docflow.yaml.CompositeKeyHandler;
import code.docflow.yaml.YamlMessages;
import code.docflow.yaml.YamlParser;
import com.google.common.base.Strings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class MessageCompositeKeyHandler implements CompositeKeyHandler<String, Message> {

    // Sample strings:
    //    message
    //    infoMessage(param1, params2) info
    //    message warn
    public static Pattern messageKeyAndParams = Pattern.compile("^\\s*(\\w+)\\s*(?:\\(([^\\)]*)\\))?(?:\\s+(\\w+))?\\s*$");

    @Override
    public Message parse(String value, HashSet<String> accessedFields, Class collectionType, YamlParser parser, Result result) {

        final Matcher matcher = messageKeyAndParams.matcher(value);
        if (!matcher.find()) {
            result.addMsg(YamlMessages.error_InvalidMessageDescription, parser.getSavedFilePosition(), parser.getSavedValue());
            return null;
        }

        final Message msg = new Message();

        msg.key = matcher.group(1).trim();
        accessedFields.add("KEY");

        final String paramsString = matcher.group(2);
        if (!Strings.isNullOrEmpty(paramsString)) {
            final String[] params = paramsString.split(",");
            if (params.length > 0) {
                final ArrayList<String> paramsArray = new ArrayList<String>();
                msg.paramsMap = new TreeMap<String, String>();
                for (int i = 0; i < params.length; i++) {
                    String param = params[i];
                    String paramName = param.trim();
                    paramsArray.add(paramName);
                    msg.paramsMap.put(paramName.toUpperCase(), "%" + (i + 1) + "$s");
                }
                msg.params = paramsArray.toArray(new String[0]);
            }
        }

        final String resultCode = matcher.group(3);
        if (Strings.isNullOrEmpty(resultCode)) {
            msg.type = Message.Type.ERROR;
            msg.resultCode = Result.Error;
        } else {
            try {
                msg.resultCode = ResultCode.parse(resultCode);
                if (msg.resultCode == Result.Ok)
                    msg.type = Message.Type.INFO;
                else if (msg.resultCode.severity >= Result.Error.severity)
                    msg.type = Message.Type.ERROR;
                else
                    msg.type = Message.Type.WARN;
            } catch (IllegalArgumentException e) {
                try {
                    msg.type = Message.Type.valueOf(resultCode.toUpperCase());
                    switch (msg.type) {
                        case INFO:
                            msg.resultCode = Result.Ok;
                            break;
                        case WARN:
                            msg.resultCode = Result.Warning;
                            break;
                        case ERROR:
                            msg.resultCode = Result.Error;
                            break;
                    }
                } catch (IllegalArgumentException e2) {
                    result.addMsg(YamlMessages.error_UnknownMessageType, parser.getSavedFilePosition(), resultCode);
                    return null;
                }
            }
        }

        return msg;
    }

    @Override
    public String key(Message msg) {
        return msg.key.toUpperCase();
    }
}
