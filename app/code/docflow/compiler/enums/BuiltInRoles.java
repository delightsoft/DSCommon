package code.docflow.compiler.enums;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.utils.EnumUtil;

public enum BuiltInRoles implements EnumUtil.ComparableEnum {
    SYSTEM("system"),
    ANONYMOUS("anonymous");
    private final String name;
    private final String upperCase;

    BuiltInRoles(String name) {
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
