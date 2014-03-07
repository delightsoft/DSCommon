package code.jsonBinding;

import code.controlflow.Result;
import code.types.PolymorphicRef;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.NullNode;
import com.google.common.base.Strings;
import play.Logger;
import play.exceptions.JavaExecutionException;
import play.exceptions.UnexpectedException;
import play.mvc.Http;
import play.test.FunctionalTest;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class JsonBinding {

    public final static JsonFactory factory;
    public final static ObjectMapper objectMapper;
    public final static ObjectReader nodeReader;

    static {
        factory = new JsonFactory();
        objectMapper = new ObjectMapper(factory);
        objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        nodeReader = objectMapper.reader(JsonNode.class);
    }

    /**
     * Returns Jackson's JsonNode object for given json, in string form.
     */
    public static JsonNode toJsonNode(String json) {
        try {
            if (Strings.isNullOrEmpty(json))
                return NullNode.instance;
            return nodeReader.readValue(json);
        } catch (IOException e) {
            throw new JavaExecutionException(e);
        }
    }

    /**
     * Binds jsonNode content to the obj object.
     */
    public static void fromJson(Object obj, JsonNode jsonNode, Result result) {
        checkNotNull(obj, "obj");
        checkNotNull(jsonNode, "jsonNode");
        final JsonTypeBinder binder = JsonTypeBinder.factory.get(obj.getClass());
        binder.fromJson(obj, jsonNode, null, null, null, null, null, null, result);
    }

    /**
     * Simple binder from object to json string.
     */
    public static String toJson(Object obj) {
        return toJson(obj, null);
    }

    /**
     * Simple binder from object to json string.
     */
    public static String toJson(Object obj, String templateName) {
        checkNotNull(obj, "obj");
        final JsonTypeBinder binder = JsonTypeBinder.factory.get(obj.getClass());
        StringWriter sw = new StringWriter();
        try {
            final JsonGenerator gen = JsonBinding.factory.createGenerator(sw);
            binder.toJson(obj, templateName, gen, null, null);
            gen.flush();
        } catch (IOException e) {
            throw new JavaExecutionException(e);
        }
        return sw.toString();
    }

    /**
     * Make right Json out of simplified Json.  Simplify Json allows single qoutas and unqouted names.
     */
    public static String testJson(String handWrittenJson) {

        checkArgument(!Strings.isNullOrEmpty(handWrittenJson), "handWrittenJson");

        try {
            final JsonParser parser = factory.createParser(handWrittenJson);
            parser.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
            parser.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
            parser.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
            parser.nextToken();

            final StringWriter sw = new StringWriter();
            final JsonGenerator generator = factory.createGenerator(sw);
            generator.copyCurrentStructure(parser);
            generator.flush();

            return sw.toString();
        } catch (Exception e) {
            throw new JavaExecutionException(String.format("Failed on Json: %s", handWrittenJson), e);
        }
    }

    /**
     * Makes normolized Json for test purposes.
     */
    public static String testJsonCleanup(String handWrittenJson) {

        checkArgument(!Strings.isNullOrEmpty(handWrittenJson), "handWrittenJson");

        try {
            final JsonParser parser = factory.createParser(handWrittenJson);
            parser.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
            parser.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
            parser.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);

            final StringWriter sw = new StringWriter();
            final JsonGenerator generator = factory.createGenerator(sw);

            while (parser.nextToken() != null) {
                switch (parser.getCurrentToken()) {
                    case FIELD_NAME:
                        final String name = parser.getCurrentName();
                        if (name.equals("id") || name.equals("rev") || name.equals("created") || name.equals("modified") || name.equals("deleted")) {
                            parser.nextToken(); // skip value
                            break;
                        }
                        // fallthrough
                    default:
                        generator.copyCurrentEvent(parser);
                }
            }

            generator.flush();
            return sw.toString();
        } catch (Exception e) {
            throw new JavaExecutionException(String.format("Failed on Json: %s", handWrittenJson), e);
        }
    }

    public static class ActionTestResult {
        public String code;
        public String message;

        public PolymorphicRef id;
        public Integer rev;
        public String doc;
        public String result;

        public List<Long> structIdes = new ArrayList<Long>();
        public List<PolymorphicRef> objIdes = new ArrayList<PolymorphicRef>();

        @Override
        public String toString() {
            return "ActionTestResult{" +
                    "code='" + code + '\'' +
                    ", message='" + message + '\'' +
                    ", id=" + id +
                    ", rev=" + rev +
                    ", doc='" + doc + '\'' +
                    ", result='" + result + '\'' +
                    '}';
        }

        public ActionTestResult(boolean isPostResult, Http.Response resp) {
            final String content = FunctionalTest.getContent(resp);
            try {
                final StringWriter sw = new StringWriter();
                final JsonGenerator generator = factory.createGenerator(sw);
                final StringWriter sw2 = new StringWriter();
                final JsonGenerator generator2 = factory.createGenerator(sw2);

                final JsonParser parser = JsonBinding.factory.createParser(content);

                if (!isPostResult) { // then it's just GET
                    parser.nextToken();
                    processObject(parser, generator);
                    generator.flush();
                    doc = sw.toString();
                } else
                    while (parser.nextToken() != null) {
                        switch (parser.getCurrentToken()) {
                            case FIELD_NAME:
                                final String name = parser.getCurrentName();
                                if (name.equals("code")) {
                                    code = parser.nextTextValue();
                                    continue;
                                }
                                if (name.equals("message")) {
                                    if (parser.nextToken() != JsonToken.VALUE_NULL)
                                        message = parser.getText();
                                    continue;
                                }
                                if (name.equals("doc")) {
                                    parser.nextToken();
                                    processObject(parser, generator);
                                    generator.flush();
                                    doc = sw.toString();
                                    continue;
                                }
                                if (name.equals("result")) {
                                    parser.nextToken();
                                    generator2.copyCurrentStructure(parser);
                                    result = sw2.toString();
                                    continue;
                                }
                                skipJsonValue(parser);
                                break;
                            default:

                        }
                    }
            } catch (JsonParseException e) {
                Logger.error("Wrong JSON: %s", content);
            } catch (IOException e) {
                throw new JavaExecutionException(e);
            }
        }

        private void processObject(JsonParser parser, JsonGenerator generator) throws IOException {

            if (parser.getCurrentToken() != JsonToken.START_OBJECT)
                throw new UnexpectedException("Expected JsonToken.START_OBJECT");

            generator.writeStartObject();

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                switch (parser.getCurrentToken()) {
                    case FIELD_NAME:
                        final String name = parser.getCurrentName();
                        if (name.equals("id")) {
                            if (parser.nextToken() == JsonToken.VALUE_NUMBER_INT) {
                                try {
                                    structIdes.add(Long.parseLong(parser.getText()));
                                } catch (IllegalArgumentException e) {
                                    structIdes.add(-1L);
                                }
                            } else {
                                PolymorphicRef t;
                                try {
                                    t = PolymorphicRef.parse(parser.getText());
                                } catch (IllegalArgumentException e) {
                                    t = PolymorphicRef.Null;
                                }
                                if (id == null) // take only first id
                                    id = t;
                                else
                                    objIdes.add(t);
                            }
                            continue;
                        }
                        if (name.equals("rev")) {
                            if (rev == null)
                                rev = parser.nextIntValue(0);
                            continue;
                        }
                        if (name.equals("created") || name.equals("modified") || name.equals("deleted")) {
                            skipJsonValue(parser);
                            continue;
                        }
                        generator.writeFieldName(name);
                        parser.nextToken();
                        if (parser.getCurrentToken() == JsonToken.START_OBJECT)
                            processObject(parser, generator);
                        else if (parser.getCurrentToken() == JsonToken.START_ARRAY)
                            processArray(parser, generator);
                        else
                            generator.copyCurrentEvent(parser);
                        break;
                }
            }

            generator.writeEndObject();
        }

        private void processArray(JsonParser parser, JsonGenerator generator) throws IOException {
            generator.writeStartArray();
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (parser.getCurrentToken() == JsonToken.START_OBJECT)
                    processObject(parser, generator);
                else if (parser.getCurrentToken() == JsonToken.START_ARRAY)
                    processArray(parser, generator);
                else
                    //???
                    generator.copyCurrentStructure(parser);
            }
            generator.writeEndArray();
        }
    }

    public static void skipJsonValue(JsonParser parser) {
        try {
            JsonToken t = parser.getCurrentToken();

            if (t == JsonToken.FIELD_NAME)
                t = parser.nextToken();

            switch (t) {
                case START_ARRAY:
                    while (parser.nextToken() != JsonToken.END_ARRAY)
                        skipJsonValue(parser);
                    break;
                case START_OBJECT:
                    while (parser.nextToken() != JsonToken.END_OBJECT)
                        skipJsonValue(parser);
                    break;
            }
        } catch (IOException e) {
            throw new JavaExecutionException(e);
        }
    }
}
