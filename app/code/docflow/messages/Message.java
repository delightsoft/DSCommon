package code.docflow.messages;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Сообщения о выполнении операции.  Используется исключительно для бизнес-результатов (Например: "Файл не найден" или
 * "Не найден обязательный параметр").
 */
public class Message {

    public final MessageTemplate messageTemplate;

    public final Object[] params;

    public Message(MessageTemplate messageTemplate, Object... params) {
        checkNotNull(messageTemplate, "messageTemplate");
        this.messageTemplate = messageTemplate;
        this.params = params;
    }

    @Override
    public String toString() {
        return String.format(messageTemplate.format, params);
    }

    /**
     * Сообщения привязанные к конкретным полям моделей, первым параметром получают строковое имя поля, одно из
     * строковых констант Field... в описании модели.  Важно, что если результат не null, это не означает что это
     * сообщение обязательно привязано к полю.
     */
    public String getModelField() {
        if (params == null || params.length == 0)
            return null;
        Object p = params[0];
        return p instanceof String ? (String) p : null;
    }
}
