package code.docflow.controlflow;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.jsonBinding.JsonBinding;
import code.docflow.messages.Message;
import code.docflow.messages.MessageTemplate;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.common.base.Strings;
import play.exceptions.UnexpectedException;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;

public class Result2Json {

    public static String toJson(Result result) {
        StringWriter sw = new StringWriter();
        try {
            final JsonGenerator gen = JsonBinding.factory.createGenerator(sw);
            toJson(result, gen);
            gen.flush();
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
        return sw.toString();
    }

    /**
     * Serializes Result to Json form, what later can be localied by Result2Json.toHtml() method.
     */
    public static void toJson(Result result, JsonGenerator gen) {
        try {
            result.outputException();
            gen.writeStartObject();
            gen.writeFieldName(result.getCode().toString());
            if (!result.anyMessage())
                gen.writeNull();
            else {
                gen.writeStartObject();
                for (Message msg : result.getMessages()) {
                    gen.writeFieldName(msg.messageTemplate.l18nKey);
                    if (msg.params == null)
                        gen.writeNull();
                    else {
                        gen.writeStartArray();
                        for (String param : msg.params)
                            gen.writeString(param);
                        gen.writeEndArray();
                    }
                }
                gen.writeEndObject();
            }
            gen.writeEndObject();
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    private static void wrongJson(String json) {
        throw new IllegalArgumentException(json);
    }

    public static Result toResult(String json) {

        if (Strings.isNullOrEmpty(json))
            return null; // no result

        JsonParser parser = null;
        try {
            parser = JsonBinding.factory.createParser(json);
        } catch (IOException e) {
            wrongJson(json);
        }

        final Result res = new Result();
        try {
            switch (parser.nextToken()) {
                case VALUE_NULL:
                    return null;
                case START_OBJECT:
                    if (parser.nextToken() != JsonToken.FIELD_NAME)
                        wrongJson(json);
                    res.setCode(ResultCode.parse(parser.getCurrentName()));
                    switch (parser.nextToken()) {
                        case VALUE_NULL:
                            break;
                        case START_OBJECT:
                            messages:
                            while (true) {
                                switch (parser.nextToken()) {
                                    case END_OBJECT:
                                        break messages;
                                    case FIELD_NAME:
                                        final String msgKey = parser.getCurrentName();
                                        final MessageTemplate messageTemplate = MessageTemplate.templateByKey.get(msgKey);
                                        if (messageTemplate == null)
                                            throw new IllegalArgumentException(String.format("Unknown message '%s' in '%s'", msgKey, json));
                                        switch (parser.nextToken()) {
                                            case VALUE_NULL:
                                                res.addMsg(messageTemplate);
                                                break;
                                            case START_ARRAY:
                                                final ArrayList<String> params = new ArrayList<String>();
                                                params:
                                                while (true) {
                                                    switch (parser.nextToken()) {
                                                        case END_ARRAY:
                                                            break params;
                                                        case VALUE_STRING:
                                                            params.add(parser.getValueAsString());
                                                            break;
                                                        default:
                                                            wrongJson(json);
                                                    }
                                                }
                                                res.addMsg(messageTemplate, params.toArray(new String[0]));
                                                break;
                                            default:
                                                wrongJson(json);
                                        }
                                        break;
                                    default:
                                        wrongJson(json);
                                }
                            }
                            if (parser.nextToken() != JsonToken.END_OBJECT)
                                wrongJson(json);
                    }
                    break;
            }
        } catch (JsonParseException e) {
            throw new UnexpectedException(e);
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
        return res;
    }
}
