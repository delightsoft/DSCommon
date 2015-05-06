package code.docflow.compiler.enums;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.utils.EnumUtil;

/**
 * List of implicit fields, hardcoded in this solution.
 */
public enum BuiltInFieldsGroups implements EnumUtil.ComparableEnum {
    ALL("all"),
    UPDATABLE("updatable"),
    NONRESULT("nonResult"),
    IMPLICIT("implicit"),
    IMPLICIT_TOP_LEVEL("implicitTopLevel");

    private final String name;
    private final String upperCase;

    BuiltInFieldsGroups(String name) {
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
