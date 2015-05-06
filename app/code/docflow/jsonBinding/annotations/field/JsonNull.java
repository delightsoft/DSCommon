package code.docflow.jsonBinding.annotations.field;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */

/**
 * Tells, that String field should be considered as Json serialization of Result object.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface JsonNull {
}
