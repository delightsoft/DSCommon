package code.docflow.rights;

import code.docflow.docs.Document;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */

/**
 * Interface to object that checks precondition rules for given document.
 */
public interface PreconditionEvaluator<D extends Document> {
    /**
     * True, if precondition is satisfied.
     */
    boolean isTrue(D document);
}
