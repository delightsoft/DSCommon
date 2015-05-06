package code.docflow.compiler.enums;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.utils.EnumUtil;

public enum TaskStates implements EnumUtil.ComparableEnum {
    AWAIT("await"),
    RUNNING("running"),
    CANCELED("canceled"),
    ERROR("error"),
    SUCCESS("success");

    private final String name;
    private final String upperCase;

    TaskStates(String name) {
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
