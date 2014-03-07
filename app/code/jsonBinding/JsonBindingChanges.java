package code.jsonBinding;

import code.docflow.DocflowConfig;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.util.JsonGeneratorDelegate;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import play.exceptions.JavaExecutionException;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */

/**
 * Class that wraps logic of maintaining creating changes json by mean of Jackson JsonGenerator.
 */
public class JsonBindingChanges {

    private final StringWriter sw;
    private JsonGenerator changes;
    private String jsonChanges;

    /**
     * @param factory Factory to be reused.  If null, will be used a new factory.
     */
    public JsonBindingChanges(JsonFactory factory) {
        try {
            sw = new StringWriter();
            changes = (factory != null ? factory : new JsonFactory()).createGenerator(sw);
            changes.disable(JsonGenerator.Feature.QUOTE_FIELD_NAMES);
            changes.writeStartObject();
        } catch (IOException e) {
            throw new JavaExecutionException(e);
        }
    }

    /**
     * Return JsonGenerator where to write changed fields.
     */
    public JsonGenerator getJsonGenerator() {
        return changes;
    }

    /**
     * Returns Json with changed values.  Attn: This method MUST BE used only then all changes are applied, since
     * it closes JsonGenerator.
     */
    public String getJson() {
        if (changes != null) {
            if (changes.getOutputContext().getEntryCount() > 0) {
                try {
                    changes.writeEndObject();
                    changes.flush();
                    changes.close();
                } catch (IOException e) {
                    throw new JavaExecutionException(e);
                }
                jsonChanges = sw.toString();
            }
            changes = null;
        }
        return jsonChanges;
    }

    public static class ChangesAttempt {

        private final TokenBuffer buffer;
        private final JsonGeneratorModificationDetector changes;

        public JsonGenerator getJsonGenerator() {
            return changes;
        }

        public ChangesAttempt(Integer index) {
            buffer = new TokenBuffer(null);
            buffer.disable(JsonGenerator.Feature.QUOTE_FIELD_NAMES);
            changes = new JsonGeneratorModificationDetector(buffer, index);
        }

        public boolean anyChange() {
            return changes.modified();
        }

        public void serialize(JsonGenerator generator) {
            try {
                buffer.serialize(generator);
                changes.close();
            } catch (IOException e) {
                throw new JavaExecutionException(e);
            }
        }

        public void close() {
            if (!changes.isClosed())
                try {
                    changes.close();
                } catch (IOException e) {
                    throw new JavaExecutionException(e);
                }
        }
    }

    public static class JsonGeneratorModificationDetector extends JsonGeneratorDelegate {

        private boolean _rootObject = true;
        private boolean _modified = false;
        private Integer index;

        public boolean modified() {
            return _modified;
        }

        public JsonGeneratorModificationDetector(JsonGenerator generator, Integer index) {
            super(generator);
            this.index = index;
        }

        @Override
        public void writeStartArray() throws IOException, JsonGenerationException {
            _modified = true;
            super.writeStartArray();
        }

        @Override
        public void writeStartObject() throws IOException, JsonGenerationException {
            if (_rootObject) { // it's root object that is not considered as a modification, unless it has any content
                _rootObject = false;
                super.writeStartObject();
                if (index != null) // adds index, required by subtable history format
                    super.writeNumberField(DocflowConfig.ImplicitFields.I.toString(), index);
            } else {
                super.writeStartObject();
                _modified = true;
            }
        }

        @Override
        public void writeFieldName(String s) throws IOException, JsonGenerationException {
            _modified = true;
            super.writeFieldName(s);
        }

        @Override
        public void writeFieldName(SerializableString serializableString) throws IOException, JsonGenerationException {
            _modified = true;
            super.writeFieldName(serializableString);
        }

        @Override
        public void writeString(String s) throws IOException, JsonGenerationException {
            _modified = true;
            super.writeString(s);
        }

        @Override
        public void writeString(char[] chars, int i, int i1) throws IOException, JsonGenerationException {
            _modified = true;
            super.writeString(chars, i, i1);
        }

        @Override
        public void writeString(SerializableString serializableString) throws IOException, JsonGenerationException {
            _modified = true;
            super.writeString(serializableString);
        }

        @Override
        public void writeRawUTF8String(byte[] bytes, int i, int i1) throws IOException, JsonGenerationException {
            _modified = true;
            super.writeRawUTF8String(bytes, i, i1);
        }

        @Override
        public void writeUTF8String(byte[] bytes, int i, int i1) throws IOException, JsonGenerationException {
            _modified = true;
            super.writeUTF8String(bytes, i, i1);
        }

        @Override
        public void writeRaw(String s) throws IOException, JsonGenerationException {
            _modified = true;
            super.writeRaw(s);
        }

        @Override
        public void writeRaw(String s, int i, int i1) throws IOException, JsonGenerationException {
            _modified = true;
            super.writeRaw(s, i, i1);
        }

        @Override
        public void writeRaw(SerializableString serializableString) throws IOException, JsonGenerationException {
            _modified = true;
            super.writeRaw(serializableString);
        }

        @Override
        public void writeRaw(char[] chars, int i, int i1) throws IOException, JsonGenerationException {
            _modified = true;
            super.writeRaw(chars, i, i1);
        }

        @Override
        public void writeRaw(char c) throws IOException, JsonGenerationException {
            _modified = true;
            super.writeRaw(c);
        }

        @Override
        public void writeRawValue(String s) throws IOException, JsonGenerationException {
            _modified = true;
            super.writeRawValue(s);
        }

        @Override
        public void writeRawValue(String s, int i, int i1) throws IOException, JsonGenerationException {
            _modified = true;
            super.writeRawValue(s, i, i1);
        }

        @Override
        public void writeRawValue(char[] chars, int i, int i1) throws IOException, JsonGenerationException {
            _modified = true;
            super.writeRawValue(chars, i, i1);
        }

        @Override
        public void writeBinary(Base64Variant base64Variant, byte[] bytes, int i, int i1) throws IOException, JsonGenerationException {
            _modified = true;
            super.writeBinary(base64Variant, bytes, i, i1);
        }

        @Override
        public int writeBinary(Base64Variant base64Variant, InputStream inputStream, int i) throws IOException, JsonGenerationException {
            _modified = true;
            return super.writeBinary(base64Variant, inputStream, i);
        }

        @Override
        public void writeNumber(short i) throws IOException, JsonGenerationException {
            _modified = true;
            super.writeNumber(i);
        }

        @Override
        public void writeNumber(int i) throws IOException, JsonGenerationException {
            _modified = true;
            super.writeNumber(i);
        }

        @Override
        public void writeNumber(long l) throws IOException, JsonGenerationException {
            _modified = true;
            super.writeNumber(l);
        }

        @Override
        public void writeNumber(BigInteger bigInteger) throws IOException, JsonGenerationException {
            _modified = true;
            super.writeNumber(bigInteger);
        }

        @Override
        public void writeNumber(double v) throws IOException, JsonGenerationException {
            _modified = true;
            super.writeNumber(v);
        }

        @Override
        public void writeNumber(float v) throws IOException, JsonGenerationException {
            _modified = true;
            super.writeNumber(v);
        }

        @Override
        public void writeNumber(BigDecimal bigDecimal) throws IOException, JsonGenerationException {
            _modified = true;
            super.writeNumber(bigDecimal);
        }

        @Override
        public void writeNumber(String s) throws IOException, JsonGenerationException, UnsupportedOperationException {
            _modified = true;
            super.writeNumber(s);
        }

        @Override
        public void writeBoolean(boolean b) throws IOException, JsonGenerationException {
            _modified = true;
            super.writeBoolean(b);
        }

        @Override
        public void writeNull() throws IOException, JsonGenerationException {
            _modified = true;
            super.writeNull();
        }

        @Override
        public void writeObject(Object o) throws IOException, JsonProcessingException {
            _modified = true;
            super.writeObject(o);
        }

        @Override
        public void writeTree(TreeNode treeNode) throws IOException, JsonProcessingException {
            _modified = true;
            super.writeTree(treeNode);
        }
    }
}
