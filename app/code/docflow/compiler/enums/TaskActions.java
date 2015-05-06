package code.docflow.compiler.enums;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.utils.EnumUtil;

public enum TaskActions implements EnumUtil.ComparableEnum {
    STARTJOB("startJob"),
    BACKTOAWAIT("backToAwait"),
    CANCEL("cancel"),
    SUCCESS("success"),
    ERROR("error");

    private final String name;
    private final String upperCase;

    TaskActions(String name) {
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
