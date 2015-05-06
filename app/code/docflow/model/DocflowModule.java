package code.docflow.model;

import code.docflow.utils.EnumUtil;
import play.vfs.VirtualFile;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */

public class DocflowModule {

    public enum Schema implements EnumUtil.ComparableEnum {
        V1("2013"),
        V2("2014-06");

        public String name;
        private final String upperCase;

        private Schema(String name) {
            this.name = name;
            this.upperCase = name.toUpperCase();
        }

        @Override
        public String getUpperCase() {
            return upperCase;
        }

        public String toString() {
            return name;
        }
    }

    public VirtualFile root;

    public Schema schema;

    @Override
    public String toString() {
        return "DocflowModule{root=" + root.getName() + ", schema=" + schema + '}';
    }
}
