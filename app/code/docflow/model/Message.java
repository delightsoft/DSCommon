package code.docflow.model;

import code.docflow.action.Transaction;
import code.docflow.controlflow.Result;
import code.docflow.controlflow.ResultCode;
import code.docflow.types.DocumentRef;
import code.docflow.yaml.annotations.NotYamlField;
import code.docflow.yaml.annotations.TargetField;
import code.docflow.yaml.annotations.WithCompositeKeyHandler;
import code.docflow.yaml.compositeKeyHandlers.MessageCompositeKeyHandler;
import com.google.common.base.Strings;
import play.exceptions.UnexpectedException;
import play.i18n.Messages;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.TreeMap;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
@WithCompositeKeyHandler(MessageCompositeKeyHandler.class)
public class Message {

    public enum Type {
        ERROR,
        WARN,
        INFO,
    }

    public enum Formatter {
        /**
         * String ref to give docId.
         */
        REF,
        /**
         * String docId to calculated text field for given document, or null if none.
         */
        TEXT,
        /**
         * String time as day.
         */
        DAY,
        /**
         * String time as time.
         */
        TIME,
        /**
         * String time as date time.
         */
        DATETIME,
        /**
         * Localization of string value.
         */
        L18N
    }

    @NotYamlField
    public String key;

    @NotYamlField
    public Type type;

    @NotYamlField
    public ResultCode resultCode;

    @NotYamlField
    public String fieldName;

    @NotYamlField
    public String[] params;

    @NotYamlField
    public TreeMap<String, String> paramsMap;

    @TargetField
    public LinkedHashMap<String, String> rawMessages;

    public static class ParamWithFormatter {
        /**
         * Index of source parameter.
         */
        public int srcParam;
        /**
         * Modifier that should be applied to the parameter.
         */
        public Formatter formatter;
    }

    public static class Text {
        /**
         * List of tags listed after locale (i.e. ru.html, ru_RU.html.special).
         */
        public String[] tags;
        /**
         * Message text with named parameters replaced by %&lt;parameter index&gt;$s.
         */
        public String text;
        /**
         * List of extra paramters, that are calculated by running formatter methods on
         * basic paramters.  Null, if there were no modifiers within text.
         */
        public ParamWithFormatter[] paramWithFormatters;
    }

    /**
     * Texts for this message, grouped by language code (i.e. en, ru, ru_RU), with list tagged text
     * varitions in order as it was in original file.
     */
    @NotYamlField
    public TreeMap<String, Text[]> localizedTexts;

    /**
     * It's locale what comes first in yaml file for this message.
     */
    @NotYamlField
    public String defaultLocale;

    public String formatLogMessage(String[] params) {
        return formatMessage("en", null, params);
    }

    public String formatMessage(String[] params) {
        return formatMessage(null, null, params);
    }

    public String formatMessage(String[] tags, String[] params) {
        return formatMessage(null, tags, params);
    }

    public String formatMessage(final String locale, final String[] tags, final String[] params) {

        Text text = selectMessageText(locale, tags);

        // work with modifiers, if any
        if (text.paramWithFormatters != null) {
            final int base = params.length;
            final Object[] newParams = new Object[base + text.paramWithFormatters.length];
            for (int i = 0; i < base; i++)
                newParams[i] = params[i];
            for (int i = 0; i < text.paramWithFormatters.length; i++) {
                final ParamWithFormatter paramWithFormatter = text.paramWithFormatters[i];
                switch (paramWithFormatter.formatter) {
                    case REF:
                        final DocumentRef ref = DocumentRef.parse(params[paramWithFormatter.srcParam]);
                        newParams[base + i] = "/doc/" + ref.type + "/" + (ref.isNew() ? "new" : ref.id);
                        break;
                    case TEXT:
                        // TODO: Task 2752135: Update when task https://delightsoft.teamworkpm.net/tasks/2752135 will be accomplished
                        final Result localResult = new Result();
                        final int index = base + i;
                        if (Transaction.instance().isWithinScope())
                            getText(params, paramWithFormatter, newParams, index);
                        else
                            Transaction.readOnlylScope(localResult, true, new Transaction.Delegate<Object>() {
                                @Override
                                public Object body(int attempt, Result result) {
                                    return getText(params, paramWithFormatter, newParams, index);
                                }
                            });
                        if (localResult.isError())
                            localResult.toLogger("Message.formatMessage");
                        break;
                    case L18N:
                        newParams[base + i] = Messages.get(Messages.get(params[paramWithFormatter.srcParam]));
                        // TODO: Instead of logic, we just use L18N in appropriate locale messages
//                        if (locale == null || "en".equals(locale)) // values like fields are not localized for 'en' or default
//                            newParams[base + i] = params[paramWithFormatter.srcParam];
//                        else // otherwise, at the moment this can only be the one localization (supposedly 'ru')
//                            newParams[base + i] = Messages.get(params[paramWithFormatter.srcParam]);
                        break;
                    case DAY:
                        // TODO: Implement
                        newParams[base + i] = "Day(" + params[paramWithFormatter.srcParam] + ")";
                        break;
                    case TIME:
                        // TODO: Implement
                        newParams[base + i] = "Time(" + params[paramWithFormatter.srcParam] + ")";
                        break;
                    case DATETIME:
                        // TODO: Implement
                        newParams[base + i] = "DateTime(" + params[paramWithFormatter.srcParam] + ")";
                        break;
                    default:
                        throw new UnexpectedException(String.format("Unexpected formatter %s.", paramWithFormatter.formatter.toString()));
                }
            }
            return String.format(text.text, newParams);
        }

        // simply format string
        return String.format(text.text, params);
    }

    private Object getText(String[] params, ParamWithFormatter paramWithFormatter, Object[] newParams, int index) {
        final DocumentRef docRef = DocumentRef.parse(params[paramWithFormatter.srcParam]);
        final Method textGetter = docRef.getDocument()._docType().jsonBinder.recordAccessor.fldTextGetter;
        try {
            newParams[index] = textGetter == null ? docRef.toString() : (String) textGetter.invoke(docRef.getDocument());
        } catch (IllegalAccessException e) {
            throw new UnexpectedException(e);
        } catch (InvocationTargetException e) {
            throw new UnexpectedException(e.getCause());
        }
        return null;
    }

    public Text selectMessageText(String locale, String[] tags) {
        Text text;// choose text according to locale
        Text[] texts = null;
        if (!Strings.isNullOrEmpty(locale))
            texts = localizedTexts.get(locale.toUpperCase());
        if (texts == null)
            texts = localizedTexts.get(defaultLocale);

        text = texts[0];

        // work with tags, if given.  select variant of text best matching given tags.  if two texts has same tags weight, last is winning
        if (texts.length > 1) {
            int w = textTagsWeight(tags, text);
            for (int i = 1; i < texts.length; i++) {
                int t = textTagsWeight(tags, texts[i]);
                if (t >= w) {
                    text = texts[i];
                    w = t;
                }
            }
        }
        return text;
    }

    /**
     * Number of tags[] elements found withing text.tags. -1 - when text.tags contains tag not mentioned in tags[].
     */
    public static int textTagsWeight(String[] tags, Text text) {

        if (tags == null || tags.length == 0)
            return text.tags != null ? -1 : 0;

        int w = 0;
        if (text.tags != null)
            nextTextTag:
                    for (String tt : text.tags) {
                        for (String st : tags)
                            if (st.equalsIgnoreCase(tt)) {
                                w++;
                                continue nextTextTag;
                            }
                        return -1;
                    }

        return w;
    }
}