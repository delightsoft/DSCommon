package code.docflow.jsonBinding.annotations.field;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */

/**
 * Field should be excluded from json binding.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface JsonExclude {
}
