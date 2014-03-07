package code.jsonBinding.play;

import com.fasterxml.jackson.databind.JsonNode;
import code.DocumentId;
import com.google.common.base.Strings;
import play.data.binding.Global;
import play.data.binding.TypeBinder;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
@Global
public class DocumentIdBinder implements TypeBinder<DocumentId> {
    @Override
    public DocumentId bind(String name, Annotation[] annotations, String value, Class actualClass, Type genericType) throws Exception {
        if (Strings.isNullOrEmpty(value))
            return null;
        return DocumentId.parse(value);
    }
}
