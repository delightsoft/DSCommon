package code.docflow.compiler.enums;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.utils.EnumUtil;

/**
 * List of implicit states, hardcoded in this solution.
 */
public enum BuiltInStates implements EnumUtil.ComparableEnum {
    NEW(0, "new"),
    PERSISTED(1, "persisted");

    public final int index;
    private final String name;
    private final String upperCase;

    BuiltInStates(int index, String name) {
        this.index = index;
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
