package code.docflow.model;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public enum EntityType {
    /**
     * Report document.  Not persisted.  Nothing about ID.
     */
    REPORT,
    /**
     * Report document structure class.  Not persisted.  Nothing about ID.
     */
    REPORT_STRUCTURE,
    /**
     * ID only document.
     */
    SIMPLE_DOCUMENT,
    /**
     * Document without state persistence, since it can be only new or persisted.
     */
    ONE_STATE_DOCUMENT,
    /**
     * Fully equiped (ID, Rev, Created, Modified, Deleted ...) document.
     */
    DOCUMENT,
    /**
     * Structure build for STRUCTURE field of a document.
     */
    STRUCTURE,
    /**
     * Structure build for STRUCTURE field of a document.
     */
    EMBEDDED_STRUCTURE
}
