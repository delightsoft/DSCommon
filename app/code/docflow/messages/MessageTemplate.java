package code.docflow.messages;

import code.docflow.controlflow.Result;
import code.docflow.controlflow.ResultCode;
import com.google.common.base.Objects;
import com.google.common.base.Strings;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Шаблон сообщения, необходимы для создания объектов Message.
 */
public class MessageTemplate {

    public final String format;
    public final ResultCode code;
    public final String l18nKey;

    public MessageTemplate(String format) {
        checkArgument(!Strings.isNullOrEmpty(format), "format");
        this.format = format;
        this.code = Result.Failed;
        this.l18nKey = null;
    }

    public MessageTemplate(ResultCode code, String format) {
        checkArgument(!Strings.isNullOrEmpty(format), "format");
        this.format = format;
        this.code = code;
        this.l18nKey = null;

    }

    public static final ConcurrentMap<String, MessageTemplate> templateByKey = new ConcurrentHashMap<String, MessageTemplate>();

    public MessageTemplate(ResultCode code, String format, String l18nKey) {
        checkArgument(!Strings.isNullOrEmpty(format), "format");
        checkArgument(!Strings.isNullOrEmpty(l18nKey), "key");

        this.format = format;
        this.code = code;
        this.l18nKey = l18nKey;

        templateByKey.put(l18nKey, this);
    }

    public String toString() {
        return Objects.toStringHelper(this)
                .add("format", format)
                .add("code", code)
                .toString();
    }
}
