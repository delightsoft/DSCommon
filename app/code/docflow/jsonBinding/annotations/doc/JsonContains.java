package code.docflow.jsonBinding.annotations.doc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */

/**
 * Referenced entity is aggregated. Note: Reference class should be labled by @JsonPartOfStructure.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface JsonContains {
}
