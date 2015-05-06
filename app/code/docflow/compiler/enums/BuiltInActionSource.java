package code.docflow.compiler.enums;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.utils.EnumUtil;

public enum BuiltInActionSource implements EnumUtil.ComparableEnum {
    SYSTEM("[" + BuiltInRoles.SYSTEM.toString() + "]"),
    ANONYMOUS("[" + BuiltInRoles.ANONYMOUS.toString() + "]");
    private final String name;
    private final String upperCase;

    BuiltInActionSource(String name) {
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
