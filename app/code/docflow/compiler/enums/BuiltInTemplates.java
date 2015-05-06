package code.docflow.compiler.enums;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.utils.EnumUtil;

public enum BuiltInTemplates implements EnumUtil.ComparableEnum {
    ID("id"),
    NONE("none"),
    LIST("list"),
    EXPORT("export"),
    CREATE("create"),
    FORM("form"),
    PARAMS("params"),
    BASIC("basic"),
    FORM_DIALOG("formDialog"),
    LINKED_DOCUMENT("linkedDocument"),
    FORM_TITLE("formTitle"),
    SELECTOR("selector"),
    DICT("dict"),
    HISTORY("history");
    private final String name;
    private final String upperCase;

    BuiltInTemplates(String name) {
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
