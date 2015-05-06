package code.docflow.controlflow;

import code.docflow.DocflowConfig;
import code.docflow.action.Transaction;
import code.docflow.messages.Message;
import code.docflow.messages.MessageTemplate;
import code.docflow.yaml.YamlMessages;
import com.google.common.base.Strings;
import docflow.DocflowMessages;
import play.Logger;
import play.exceptions.PlayException;
import play.exceptions.UnexpectedException;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Result of an operation, including accompanying messages.  Also, has standard result code as static fields, and
 * provides few methods for formatting and output result of an operations to log, as html etc.
 */
public final class Result {
    /**
     * Action was skipped, since target document already in the action stack within current documents update sequence.
     * Note: This logic allowes to resolve circular action dependencies, so it's an error.
     */
    public static final ResultCode ActionSkipped = new ResultCode("ActionSkipped", -2);

    /**
     * Actions was ommited, since no data was changed.
     */
    public static final ResultCode ActionOmitted = new ResultCode("ActionOmitted", -1);

    /**
     * Action succeeded.
     */
    public static final ResultCode Ok = new ResultCode("Ok", 0);

    /**
     * To tell logger that this message should be considered as warning.  Has no meaning for control logic.
     */
    public static final ResultCode Warning = new ResultCode("Warning", 1);

    /**
     * Generail failure of action.
     */
    public static final ResultCode Error = new ResultCode("Error", 100);

    /**
     * Generail failure of action.
     */
    public static final ResultCode Failed = new ResultCode("Failed", 200);

    /**
     * Actions&lt;docType&gt;.preCreated directed to return an existing document which is
     * equal the one what was ordered to create.
     */
    public static final ResultCode PreCreatedFoundEqualDocument = new ResultCode("PreCreatedFoundEqualDocument", 20);

    /**
     * Document or another resource is not found.
     */
    public static final ResultCode NotFound = new ResultCode("NotFound", Error.severity);

    /**
     * User is not authenticated.  Any action that requires authorization will refuse.
     */
    public static final ResultCode NotAuthenticated = new ResultCode("NotAuthenticated", Error.severity);

    /**
     * Action cannot be executed cause inappropriate document state.
     */
    public static final ResultCode WrongState = new ResultCode("WrongState", Error.severity + 10);

    /**
     * Invalid either action parameter or document update.
     */
    public static final ResultCode InvalidArguments = new ResultCode("InvalidArguments", Failed.severity + 10);

    /**
     * User action is prohibited due to security settings.
     */
    public static final ResultCode ActionProhibited = new ResultCode("ActionProhibited", Failed.severity);

    /**
     * Wrong value (i.e. while parsing Yaml).
     */
    public static final ResultCode WrongValue = new ResultCode("WrongValue", Failed.severity);

    /**
     * Parallel document update has being happend.
     */
    public static final ResultCode ConcurrentUpdate = new ResultCode("ConcurrentUpdate", Failed.severity);

    private ResultCode code = Ok;
    private List<Message> messages;

    // TODO: Make this option controlled from conf/application.conf
    private boolean logOkResultsAsInfo = true;

    private PlayException exception;

    public static class Failed extends PlayException {

        Result _result;

        public Failed(final Result result, final Throwable cause) {
            super("Error Message in Result", cause);
            _result = result;
        }

        public Failed(final Result result) {
            _result = result;
        }

        @Override
        public String getErrorTitle() {
            return "Error Message in Result";
        }

        @Override
        public String getErrorDescription() {
            return _result.toHtml();
        }
    }

    private boolean throwExceptionOnError;

    private boolean setLogToLogger;

    public void setThrowExceptionOnError() {
        throwExceptionOnError = true;
    }

    public void setLogToLogger() {
        setLogToLogger = true;
    }

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
        return this.code.severity >= Result.Error.severity;
    }

    /**
     * Adds message to the end of messages list.
     */
    public void addMsg(PlayException exception, MessageTemplate messageTemplate, Object... params) {
        addMsg(messageTemplate, params);
        this.exception = exception;
    }

    /**
     * Adds standard message that an exception is being cought.
     */
    public void addException(Throwable e) {
        if (!(e instanceof PlayException))
            e = new UnexpectedException((e instanceof InvocationTargetException) ? e.getCause() : e);
        addMsg((PlayException) e, DocflowMessages.error_FailedWithException_1, ((PlayException) e).getId());

    }

    /**
     * True, if some exceptions is logged to the result.
     */
    public boolean hasException() {
        return exception != null;
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
        // TODO: Faile, if on
        // TODO: Log, if on
    }

    /**
     * Adds context of the above all other messages.
     */
    public void addContext(String context, Object... params) {
        if (messages == null)
            messages = new ArrayList<Message>();
        messages.add(0, new Message(DocflowMessages.info_FailureContext_1, String.format(context, params)));
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
        value = null;
        if (messages != null)
            messages.clear();
    }

    /**
     * Combines all messages to one LF delimited string.
     */
    public String combineMessages() {

        outputException();

        if (!anyMessage())
            return "";

        StringBuilder sb = new StringBuilder();
        for (Message message : messages) {
            if (message.messageTemplate.code.severity >= Result.Error.severity)
                sb.append("\tERROR: ");
            else if (message.messageTemplate.code.severity > Result.Ok.severity)
                sb.append("\tWARN: ");
            else
                sb.append("\tINFO: ");
            sb.append(message).append("\n");
        }

        return sb.toString();
    }

    /**
     * Outputs to System.out debug information, which includes: name of operation, result code and all messages within Result object.
     */
    public void debugOutToConsole(String operationName) {
        outputException();
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

    private static final String msgResultOfOpWODetails = "Operation '%1$s': %2$s";
    private static final String msgResultOfOpWithDetails = "Operation '%1$s': %2$s:\n%3$s";

    public void toLogger(String operationName) {
        outputException();
        if (anyMessage()) {
            if (code.severity >= Result.Error.severity)
                Logger.error(msgResultOfOpWithDetails, operationName, code, combineMessages());
            else if (logOkResultsAsInfo || code.severity > Result.Ok.severity)
                Logger.info(msgResultOfOpWithDetails, operationName, code, combineMessages());
            else if (Logger.isDebugEnabled())
                Logger.debug(msgResultOfOpWithDetails, operationName, code, combineMessages());
        } else if (code.severity >= Result.Error.severity)
            Logger.error(msgResultOfOpWODetails, operationName, code);
        else if (logOkResultsAsInfo || code.severity > Result.Ok.severity)
            Logger.info(msgResultOfOpWODetails, operationName, code);
        else if (Logger.isDebugEnabled())
            Logger.debug(msgResultOfOpWODetails, operationName, code);
    }

    public void toLogger() {
        outputException();
        if (anyMessage()) {
            for (Message message : messages) {
                if (message.messageTemplate.code.severity >= Result.Error.severity)
                    Logger.error(message.messageTemplate.format, (Object[]) message.params);
                else if (logOkResultsAsInfo || message.messageTemplate.code.severity > Result.Ok.severity)
                    Logger.info(message.messageTemplate.format, (Object[]) message.params);
                else if (Logger.isDebugEnabled())
                    Logger.debug(message.messageTemplate.format, (Object[]) message.params);
            }
        }
    }

    /**
     * Renders messages to HTML msgResultOfOpWithDetails, applying Docflow localization from Messages.yaml, whenever it's possible.
     */
    public String toHtml() {
        if (!anyMessage())
            return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<ul class='");
        if (code.severity >= Result.Error.severity)
            sb.append("error");
        else if (code.severity > Result.Ok.severity)
            sb.append("warn");
        else
            sb.append("info");
        sb.append("'>");
        for (Message message : messages) {

            sb.append("<li class='");
            if (message.messageTemplate.code.severity >= Result.Error.severity)
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
                } else
                    sb.append(msg.formatMessage("ru", new String[]{"html"}, message.params));
            } else
                sb.append(message.toString());
            sb.append("</li>");
        }
        sb.append("</ul>");

        outputException();
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
        if (exception == null)
            exception = localResult.exception;
        setCodeWithSeverity(localResult.code);
        value = localResult.value;

        // complete code block, if autocompletion is on
        if (throwExceptionOnError && localResult.code.severity >= Result.Error.severity)
            if (Transaction.instance().isWithinScope())
                throw new Transaction.Failed(localResult);
            else
                throw new Result.Failed(localResult);
    }

    /**
     * Appends results of file processing.
     */
    public void appendFileScope(Result localResult, String filename) {

        checkNotNull(localResult);
        checkArgument(!Strings.isNullOrEmpty(filename));

        if (localResult.messages != null) {

            if (messages == null)
                messages = new ArrayList<Message>();

            boolean isWarn = false;
            boolean isError = false;
            for (Message message : localResult.messages)
                if (message.messageTemplate.code.severity >= Result.Error.severity) {
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

    private Object value;

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public String toString() {
        outputException();
        return code + ":\n" + combineMessages();
    }

    public void outputException() {
        if (exception != null) {
            Logger.error(exception, "Failure");
            exception = null;
        }
    }
}
