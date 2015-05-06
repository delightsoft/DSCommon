package code.docflow.play;

import code.docflow.types.DocumentRef;
import com.google.common.base.Strings;
import play.data.binding.Global;
import play.data.binding.TypeBinder;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
@Global
public class DocumentRefPlayBinder implements TypeBinder<DocumentRef> {
    @Override
    public DocumentRef bind(String name, Annotation[] annotations, String value, Class actualClass, Type genericType) throws Exception {
        if (Strings.isNullOrEmpty(value))
            return null;
        return DocumentRef.parse(value);
    }
}
