package code.docflow.jsonBinding;

import code.docflow.compiler.enums.BuiltInTemplates;
import code.docflow.controlflow.Result;
import code.docflow.jsonBinding.binders.type.TypeBinder;
import code.docflow.types.DocumentRef;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import com.google.gson.JsonObject;
import play.Logger;
import play.exceptions.JavaExecutionException;
import play.exceptions.UnexpectedException;
import play.mvc.Http;
import play.test.FunctionalTest;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

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
        objectMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        nodeReader = objectMapper.reader(JsonNode.class);
    }

    public static final String VALUE = "value";

    /**
     * Returns Jackson's JsonNode object for given json, in string form.
     */
    public static JsonNode toJsonNode(String json) {
        try {
            if (Strings.isNullOrEmpty(json))
                return NullNode.instance;
            return nodeReader.readValue(json);
        } catch (IOException e) {
            return null;
        }
    }

    public static JsonNode toJsonNode(Object value, String template) {
        checkNotNull(value, "value");
        final TypeBinder valueBinder = TypeBinder.factory.get(value.getClass());
        final String templateName = Strings.isNullOrEmpty(template) ? BuiltInTemplates.LIST.toString() : template;
        try {
            return valueBinder.copyToJson(value, new JsonTypeBinder.TemplateName(templateName), null, 0);
        } catch (Exception e) {
            throw new UnexpectedException(e);
        }
    }

    /**
     * Binds jsonNode content to the obj object.
     */
    public static void fromJson(Object obj, ObjectNode objectNode, Result result) {
        checkNotNull(obj, "obj");
        checkNotNull(objectNode, "objectNode");
        final JsonTypeBinder binder = JsonTypeBinder.factory.get(obj.getClass());
        binder.fromJson(obj, objectNode, null, null, null, null, null, null, result);
    }

    public static void nodeToStream(final JsonNode node, final OutputStream out) {
        try {
            final JsonGenerator generator = JsonBinding.factory.createGenerator(out);
            try {
                generator.writeTree(node);
                generator.flush();
            } finally {
                generator.close();
            }
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }

    }

    public static ObjectNode extend(String... options) {
        ObjectNode res = JsonNodeFactory.instance.objectNode();
        for (String v : options) {
            if (v != null && v.trim().length() > 0)
                try {
                    final JsonNode n = nodeReader.readValue(v);
                    if (n.isObject()) {
                        final Iterator<Map.Entry<String, JsonNode>> fields = n.fields();
                        while (fields.hasNext()) {
                            Map.Entry<String, JsonNode> pair = fields.next();
                            final String key = pair.getKey();
                            res.put(key, pair.getValue());
                        }
                    } else {
                        res.put(VALUE, n);
                    }
                } catch (IOException e) {
                    Logger.error("JsonBinding.extend: Invalid json: %s", v);
                    throw new UnexpectedException(e);
                }
        }
        return res;
    }

    /**
     * Make right Json out of simplified Json.  Simplify Json allows single qoutas and unqouted names.
     */
    public static String testJson(String handWrittenJson) {

        checkArgument(!Strings.isNullOrEmpty(handWrittenJson), "handWrittenJson");

        try {
            final JsonParser parser = factory.createParser(handWrittenJson);
            parser.enable(JsonParser.Feature.ALLOW_COMMENTS);
            parser.enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES);
            parser.enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES);
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

        JsonParser parser = null;
        JsonGenerator generator = null;
        try {
            parser = factory.createParser(handWrittenJson);
            parser.enable(JsonParser.Feature.ALLOW_COMMENTS);
            parser.enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES);
            parser.enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES);

            final StringWriter sw = new StringWriter();
            generator = factory.createGenerator(sw);

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
        } finally {
            try {
                if (parser != null)
                    parser.close();
                if (generator != null)
                    generator.close();
            } catch (IOException e) {
                throw new UnexpectedException(e);
            }
        }
    }

    public static class ActionTestResult {
        public String code;
        public String message;

        public DocumentRef id;
        public Integer rev;
        public String doc;
        public String result;

        public List<Long> structIdes = new ArrayList<Long>();
        public List<DocumentRef> objIdes = new ArrayList<DocumentRef>();

        @Override
        public String toString() {
            return "ActionTestResult{" +
                    "code='" + code + '\'' +
                    ", message='" + message + '\'' +
                    ", id=" + id +
                    ", rev=" + rev +
                    ", docType='" + doc + '\'' +
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
                throw new UnexpectedException(e);
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
                                DocumentRef t;
                                try {
                                    t = DocumentRef.parse(parser.getText());
                                } catch (IllegalArgumentException e) {
                                    t = null;
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
            throw new UnexpectedException(e);
        }
    }
}
