package code.controlflow;

import code.docflow.DocflowConfig;
import code.docflow.messages.Message;
import code.docflow.messages.MessageTemplate;
import code.docflow.yaml.YamlMessages;
import com.google.common.base.Strings;
import play.Logger;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Объект, содержащий в себе код результата выполняния операции, и, возможно, сопуствующие текстовые сообщения.
 */
public final class Result {
    /**
     * Actions was ommited, since no data was changed.
     */
    public static final ResultCode ActionOmitted = new ResultCode("ActionOmitted", -1);

    /**
     * Action was skipped, since target document already in the action stack within current documents update sequence.
     * Note: This logic allowes to resolve circular action dependencies, so it's an error.
     */
    public static final ResultCode ActionSkipped = new ResultCode("ActionSkipped", -2);

    /**
     * Action succeeded.
     */
    public static final ResultCode Ok = new ResultCode("Ok", 0);

    /**
     * Generail failure of action.
     */
    public static final ResultCode Failed = new ResultCode("Failed", 100);

    /**
     * Document or another resource is not found.
     */
    public static final ResultCode NotFound = new ResultCode("NotFound", 101);

    /**
     * User is not authenticated.  Any action that requires authorization will refuse.
     */
    public static final ResultCode NotAuthenticated = new ResultCode("NotAuthenticated", 200);

    /**
     * To tell logger that this message should be considered as warning.  Has no meaning for control logic.
     */
    public static final ResultCode Warning = new ResultCode("Warning", 1);

    /**
     * Invalid either action parameter or document update.
     */
    public static final ResultCode InvalidArguments = new ResultCode("InvalidArguments", 200);

    /**
     * Action cannot be executed cause inappropriate document state.
     */
    public static final ResultCode WrongState = new ResultCode("WrongState", 110);

    /**
     * User action is prohibited due to security settings.
     */
    public static final ResultCode ActionProhibited = new ResultCode("ActionProhibited", 200);

    /**
     * Wrong value (i.e. while parsing Yaml).
     */
    public static final ResultCode WrongValue = new ResultCode("WrongValue", 200);

    /**
     * Parallel document update has being happend.
     */
    public static final ResultCode ConcurrentUpdate = new ResultCode("ConcurrentUpdate", 200);

    /**
     * Actions&lt;docType&gt;.preCreated directed to return an existing document which is
     * equal the one what was ordered to create.
     */
    public static final ResultCode PreCreatedFoundEqualDocument = new ResultCode("PreCreatedFoundEqualDocument", 20);

    /**
     * Update was rejected, since document was already updated within current documents update sequence.
     * Note: This means a bad design of documents updates.  It's a error that requires redisign of updates logic.
     */
    public static final ResultCode UpdateRejected = new ResultCode("UpdateRejected", 200);

    private ResultCode code = Ok;
    private List<Message> messages;

    /**
     * Возвращает текущий код результата.  Код может быть изменен методом setCode(), и сброшен методом clear().
     */
    public final ResultCode getCode() {
        return code;
    }

    /**
     * Sets result code withOUT respect to severity field.
     */
    public final void setCode(ResultCode code) {
        checkNotNull(code, "code");
        this.code = code;
    }

    /**
     * Sets result code with respect to severity field.
     */
    public final void setCodeWithSeverity(ResultCode code) {
        checkNotNull(code, "code");
        if (code.isWorseThen(this.code))
            this.code = code;
    }

    /**
     * True, if code is not Result.Ok
     */
    public final boolean isNotOk() {
        return this.code != Ok;
    }

    /**
     * True, if code is Failed or worse.
     */
    public final boolean isError() {
        return this.code.severity >= Result.Failed.severity;
    }

    /**
     * Adds message to the end of messages list.
     */
    public void addMsg(MessageTemplate messageTemplate, Object... params) {
        checkNotNull(messageTemplate, "messageTemplate");
        addMsg(new Message(messageTemplate, params));
    }

    /**
     * Adds message to the end of messages list.
     */
    public void addMsg(Message message) {
        checkNotNull(message, "message");
        if (messages == null)
            messages = new ArrayList<Message>();
        messages.add(message);
        final ResultCode newErrorCode = message.messageTemplate.code;
        if (newErrorCode != null)
            setCodeWithSeverity(newErrorCode);
    }

    /**
     * True, if there is an least one message in the list.
     */
    public boolean anyMessage() {
        return messages != null && messages.size() > 0;
    }

    /**
     * List of messages.
     */
    public Message[] getMessages() {
        return messages.toArray(new Message[]{});
    }

    /**
     * Number of messages within collection.
     */
    public int getMessagesCount() {
        return messages == null ? 0 : messages.size();
    }

    /**
     * Resets Result object to initial states.  Allows to reuse Result object among the code.
     */
    public void clear() {
        code = Ok;
        if (messages != null)
            messages.clear();
    }

    /**
     * Combines all messages to one LF delimited string.
     */
    public String combineMessages() {
        if (!anyMessage())
            return "";

        StringBuilder sb = new StringBuilder();
        for (Message message : messages)
            sb.append(message).append("\n");

        return sb.toString();
    }

    /**
     * Outputs to System.out debug information, which includes: name of operation, result code and all messages within Result object.
     */
    public void debugOutToConsole(String operationName) {
        checkArgument(!Strings.isNullOrEmpty(operationName), "operationName");
        if (anyMessage()) {
            System.out.println(String.format("Result of operation '%1$s' is %2$s, with following messages:", operationName, code));
            for (Message message : messages) {
                System.out.print("\t");
                System.out.println(message);
            }
        } else
            System.out.println(String.format("Result of operation '%1$s' is %2$s", operationName, code));
    }

    static MessageTemplate toLoggerTitle = new MessageTemplate(Result.Ok, "Result of operation '%1$s' is %2$s, with following messages:");

    public void toLogger(String operationName) {
        if (anyMessage()) {
            Result withTitle = new Result();
            withTitle.addMsg(toLoggerTitle, operationName, code);
            withTitle.append(this);
            withTitle.toLogger();
        }
        else
            Logger.info("Result of operation '%1$s' is %2$s", operationName, code);
    }

    public void toLogger() {

        if (!anyMessage())
            return;

        for (Message message : messages) {
            if (message.messageTemplate.code.severity >= Result.Failed.severity)
                Logger.error(message.messageTemplate.format, message.params);
            else if (message.messageTemplate.code.severity > Result.Ok.severity)
                Logger.info(message.messageTemplate.format, message.params);
            else if (Logger.isDebugEnabled())
                Logger.debug(message.messageTemplate.format, message.params);
        }
    }

    /**
     * Renders messages to HTML format, applying Docflow localization from Messages.yaml, whenever it's possible.
     */
    public String toHtml() {
        if (!anyMessage())
            return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<ul>");
        for (Message message : messages) {

            sb.append("<li class='");
            if (message.messageTemplate.code.severity >= Result.Failed.severity)
                sb.append("error");
            else if (message.messageTemplate.code.severity > Result.Ok.severity)
                sb.append("warn");
            else
                sb.append("info");
            sb.append("'>");

            final String l18nKey = message.messageTemplate.l18nKey;

            if (l18nKey != null) {
                final code.docflow.model.Message msg = DocflowConfig.instance.messages.get(l18nKey);
                if (msg == null) {
                    Logger.error(String.format("Localization info not found for message with key '%s'", l18nKey));
                    sb.append(message.toString()); // to take attention
                }
                else {
                    sb.append(String.format(msg.ruHtml, message.params));
                }
            }
            else
                sb.append(message.toString());
            sb.append("</li>");
        }
        sb.append("</ul>");

        return sb.toString();
    }

    /**
     * Appends messages from result of an operation to common list.  Helps to build compiler error recovery logic.
     */
    public void append(Result localResult) {

        checkNotNull(localResult);

        if (localResult.messages != null) {
            if (messages == null)
                messages = new ArrayList<Message>();
            messages.addAll(localResult.messages);
        }
        setCodeWithSeverity(localResult.code);
    }

    /**
     * Appends results of file processing.
     */
    public void append(Result localResult, String filename) {

        checkNotNull(localResult);
        checkArgument(!Strings.isNullOrEmpty(filename));

        if (localResult.messages != null) {

            if (messages == null)
                messages = new ArrayList<Message>();

            boolean isWarn = false;
            boolean isError = false;
            for (Message message : localResult.messages)
                if (message.messageTemplate.code.severity >= Result.Failed.severity) {
                    isError = true;
                    break;
                } else if (message.messageTemplate.code.severity > Result.Ok.severity)
                    isWarn = true;

            if (isError)
                addMsg(YamlMessages.error_InFile, filename);
            else if (isWarn)
                addMsg(YamlMessages.warning_InFile, filename);
            else
                addMsg(YamlMessages.debug_InFile, filename);

            messages.addAll(localResult.messages);
        }

        setCodeWithSeverity(localResult.code);
    }

    public String toString() {
        return code + ":\n" + combineMessages();
    }

}
