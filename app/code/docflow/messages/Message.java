package code.docflow.messages;

import org.joda.time.DateTime;
import play.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Message describing a result of business operation. I.e. "File not found" or "Missing required data in the file".
 */
public class Message {

    public final MessageTemplate messageTemplate;

    public final String[] params;

    public Message(MessageTemplate messageTemplate, Object... params) {
        checkNotNull(messageTemplate, "messageTemplate");
        this.messageTemplate = messageTemplate;
        String[] s = null;
        if (params != null && params.length > 0) {
            // Forcebly convert all parameters to strings.  This to ensure that there will be
            // no difference between original Message and messages restored from Json.
            s = new String[params.length];
            for (int i = 0; i < params.length; i++) {
                final Object param = params[i];
                if (param != null)
                    if (param instanceof DateTime)
                        s[i] = Long.toString(((DateTime) param).getMillis());
                    else
                        s[i] = param.toString();
            }
        }
        this.params = s;
    }

    @Override
    public String toString() {
        try {
            return String.format(messageTemplate.format, (Object[]) params);
        } catch (Exception e) {
            StringBuilder sb = new StringBuilder("Failed format message:");
            if (messageTemplate.l18nKey != null)
                sb.append(" l18nKey: ").append(messageTemplate.l18nKey).append(";");
            sb.append(" format: ").append(messageTemplate.format).append(";");
            if (params != null) {
                sb.append(" params:[");
                for (int i = 0; i < params.length; i++) {
                    String param = params[i];
                    sb.append("\"").append(param.replace("\"", "\\\"")).append("\"");
                    if (i + 1 < params.length)
                        sb.append(", ");
                }
                sb.append("];");
            }
            Logger.error(sb.toString());
            return "[Msg: Failed format (See log)]";
        }
    }

    /**
     * Messages related to specific fields of input, takes as first parameter the field name.
     */
    public String getModelField() {
        return (params != null && params.length > 0) ? params[0] : null;
    }
}
