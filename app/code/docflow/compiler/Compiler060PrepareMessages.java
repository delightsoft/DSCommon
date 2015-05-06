package code.docflow.compiler;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.DocflowConfig;
import code.docflow.controlflow.Result;
import code.docflow.model.Message;
import code.docflow.utils.NamesUtil;
import code.docflow.yaml.YamlMessages;
import com.google.common.base.Strings;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test strings:
 * Message with paramter $param and formatted parameter $doc|text
 * $col: message start and end with parameter
 * Messages contains $$dollar sign, and this is not a paramter
 */

public class Compiler060PrepareMessages {

    public static Pattern messageNamedParam = Pattern.compile("(^|[^\\$])(?:\\$(\\w+))(?:\\|(\\w*))?");

    public static void doJob(DocflowConfig docflowConfig, Result result) {
        if (docflowConfig.messages != null)
            for (Message msg : docflowConfig.messages.values()) {
                final String postfix = msg.paramsMap != null ? ("_" + msg.paramsMap.size()) : "";
                if (msg.resultCode == Result.Ok)
                    msg.fieldName = "info_" + NamesUtil.turnFirstLetterInUpperCase(msg.key) + postfix;
                else if (msg.resultCode.severity >= Result.Error.severity)
                    msg.fieldName = "error_" + NamesUtil.turnFirstLetterInUpperCase(msg.key) + postfix;
                else
                    msg.fieldName = "warning_" + NamesUtil.turnFirstLetterInUpperCase(msg.key) + postfix;

                // process localized version and group by language
                TreeMap<String, ArrayList<Message.Text>> byLocale = new TreeMap<String, ArrayList<Message.Text>>();
                Result localResult = new Result();
                for (Map.Entry<String, String> pair : msg.rawMessages.entrySet()) {
                    String key = pair.getKey();
                    String value = pair.getValue();

                    // Backword compatibility.  ruHtml was the way to ru.html in original (partially completed) version of code
                    // TODO: Obsolete: Remove ruHtml support after 2015-02-01
                    if (key.equals("ruHtml"))
                        key = "ru.html";

                    Message.Text text = new Message.Text();

                    // process message text
                    localResult.clear();
                    replaceParams(msg, text, value, localResult);
                    if (localResult.isNotOk()) {
                        result.append(localResult);
                        if (localResult.isError())
                            continue;
                    }

                    // split message key for locale and tags
                    String[] split = key.split("\\.");
                    if (split.length > 1) {
                        final String[] tags = new String[split.length - 1];
                        for (int i = 1; i < split.length; i++)
                            tags[i - 1] = split[i].toUpperCase();
                        text.tags = tags;
                    }

                    // add list
                    final String locale = split[0].toUpperCase();
                    if (msg.defaultLocale == null)
                        msg.defaultLocale = locale;
                    ArrayList<Message.Text> al = byLocale.get(locale);
                    if (al == null)
                        byLocale.put(locale, al = new ArrayList<Message.Text>());
                    al.add(text);
                }

                // copy to final structure
                final TreeMap<String, Message.Text[]> map = msg.localizedTexts = new TreeMap<String, Message.Text[]>();
                for (Map.Entry<String, ArrayList<Message.Text>> pair : byLocale.entrySet())
                    map.put(pair.getKey(), pair.getValue().toArray(new Message.Text[0]));

                if (msg.localizedTexts.size() == 0) {
                    result.addMsg(YamlMessages.error_MessageMustHaveAtLeastOneVariantOfText, msg.key);
                    continue;
                }

                final Message.Text[] localizationForLogs = msg.localizedTexts.get("EN");
                if (localizationForLogs == null)
                    result.addMsg(YamlMessages.warning_MessageShouldHaveEnLocalization, msg.key);
            }
    }

    private static void replaceParams(Message msg, Message.Text txt, String src, Result result) {

        // support for '\n'
        src = src.replace("\\n", "\n");

        final Matcher matcher = messageNamedParam.matcher(src);
        StringBuilder res = null;
        TreeMap<String, String> paramsWithFormattersMap = null;
        ArrayList<Message.ParamWithFormatter> paramsWithFormatters = null;
        int p = 0;
        while (matcher.find()) {
            final String lead = matcher.group(1);
            final String paramName = matcher.group(2);
            final String paramModifier = matcher.group(3);
            final String replacement = msg.paramsMap.get(paramName.toUpperCase());
            if (replacement == null) {
                result.addMsg(YamlMessages.error_MessageUnknownParameter, msg.key, paramName);
                continue;
            }
            if (res == null)
                res = new StringBuilder();
            final int s = matcher.start() + lead.length();
            if (p < s)
                res.append(src, p, s);
            if (Strings.isNullOrEmpty(paramModifier))
                res.append(replacement);
            else {
                final String fullParamName = matcher.group(0);
                Message.Formatter formatter;
                try {
                    formatter = Message.Formatter.valueOf(paramModifier.toUpperCase());
                } catch (IllegalArgumentException e) {
                    result.addMsg(YamlMessages.error_MessageUnknownModifier, msg.key, paramModifier, fullParamName);
                    continue;
                }
                if (paramsWithFormattersMap == null) {
                    paramsWithFormatters = new ArrayList<Message.ParamWithFormatter>();
                    paramsWithFormattersMap = new TreeMap<String, String>();
                }
                String replacement2 = paramsWithFormattersMap.get(fullParamName.toUpperCase());
                if (replacement2 == null) {
                    replacement2 = "%" + (msg.params.length + paramsWithFormattersMap.size() + 1) + "$s";
                    paramsWithFormattersMap.put(fullParamName.toUpperCase(), replacement2);
                    final Message.ParamWithFormatter paramWithFormatter = new Message.ParamWithFormatter();
                    for (int i = 0; i < msg.params.length; i++)
                        if (msg.params[i].equalsIgnoreCase(paramName))
                            paramWithFormatter.srcParam = i;
                    paramWithFormatter.formatter = formatter;
                    paramsWithFormatters.add(paramWithFormatter);
                }
                res.append(replacement2);
            }
            p = matcher.end();
        }
        if (res == null)
            txt.text = src;
        else {
            res.append(src.substring(p));
            txt.text = res.toString();
            if (paramsWithFormatters != null)
                txt.paramWithFormatters = paramsWithFormatters.toArray(new Message.ParamWithFormatter[0]);
        }
    }
}
