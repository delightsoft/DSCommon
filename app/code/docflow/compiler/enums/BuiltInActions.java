package code.docflow.compiler.enums;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.utils.EnumUtil;

public enum BuiltInActions implements EnumUtil.ComparableEnum {
    NEWINSTANCE("newInstance"),
    PRECREATE("preCreate"),
    PREUPDATE("preUpdate");
    private final String name;
    private final String upperCase;

    BuiltInActions(String name) {
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
