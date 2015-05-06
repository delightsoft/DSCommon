package code.docflow.compiler.enums;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

public enum BuiltInTypes {

    STRING(new String[]{"length"}, new String[]{"maxLength", "minLength", "pattern"}),
    TEXT(null, null),

    INT(null, new String[]{"min", "max"}),
    LONG(null, new String[]{"min", "max"}),
    DOUBLE(null, new String[]{"min", "max"}),
    BOOL(null, null), // synonym: boolean

    ENUM(null, null),

    PHONE(null, null),
    EMAIL(null, null),
    UUID(null, null),

    JSON(null, null),
    JSONTEXT(null, null),

    REFERS(null, new String[]{"refDocument"}), // synonym: ref
    POLYMORPHIC_REFERS(null, new String[]{"refDocuments"}), // synonym: refers

    STRUCTURE(null, null), // synonym: struct
    SUBTABLE(null, null),
    TAGS(null, null),

    FILE(null, null),

    DATE(null, null),
    TIME(null, null),
    DATETIME(null, new String[]{"local"}),
    PERIOD(null, null),
    DURATION(null, null),
    INTERVAL(null, null),

    PASSWORD(null, null),
    RESULT(null, null),

    JAVA(null, null);

    public final String[] required;
    public final String[] optional;
    private final String[] empty = new String[0];

    BuiltInTypes(String[] required, String[] optional) {
        this.required = required == null ? empty : required;
        this.optional = optional == null ? empty : optional;
    }

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }
}
