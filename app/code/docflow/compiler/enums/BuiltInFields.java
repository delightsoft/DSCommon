package code.docflow.compiler.enums;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.utils.EnumUtil;

/**
 * List of implicit fields, hardcoded in this solution.
 */
public enum BuiltInFields implements EnumUtil.ComparableEnum {
    ID("id"),
    SUBRECORD_ID("_id"),
    REV("rev"),
    I("i"),
    STATE("state"),
    SUBJ("subj"),
    CREATED("created"),
    MODIFIED("modified"),
    DELETED("deleted"),
    TEXT_STORAGE("textStorage"),
    TEXT("text"),
    FK("fk"),
    RESULT("result"),
    CREATOR("creator");

    private final String name;
    private final String upperCase;

    BuiltInFields(String name) {
        this.name = name;
        this.upperCase = name.toUpperCase();
    }

    @Override
    public String getUpperCase() {
        return upperCase;
    }

    @Override
    public String toString() {
        return name;
    }
}
