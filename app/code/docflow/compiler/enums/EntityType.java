package code.docflow.compiler.enums;

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
    LIGHT_DOCUMENT,
    /**
     * ID, Creator and Created only document.
     */
    SIMPLE_DOCUMENT,
    /**
     * Fully equiped (ID, Rev, Created, Modified, Deleted ...) document.
     */
    DOCUMENT,
    /**
     * Structure build for SUBTABLE field of a document.
     */
    SUBTABLE,
    /**
     * Structure build for STRUCTURE field of a document.
     */
    STRUCTURE
}
