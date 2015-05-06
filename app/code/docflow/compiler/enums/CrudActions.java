package code.docflow.compiler.enums;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.utils.EnumUtil;

/**
 * List of implicit actions, hardcoded in this solution.
 */
public enum CrudActions implements EnumUtil.ComparableEnum {
    CREATE(0, "create", true, true, false),
    RETRIEVE(1, "retrieve", false, false, false),
    UPDATE(2, "update", true, true, false),
    DELETE(3, "delete", true, false, true),
    RECOVER(4, "recover", true, false, false);

    private final String name;
    private final String upperCase;

    public final int index;
    public final boolean display;
    public final boolean update;
    public final boolean other;

    CrudActions(int index, String name, boolean display, boolean update, boolean other) {
        this.index = index;
        this.name = name;
        this.upperCase = name.toUpperCase();
        this.update = update;
        this.display = display;
        this.other = other;
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
