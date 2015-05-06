package code.docflow.utils;

public class EnumUtil {

    public static interface ComparableEnum {
        public String getUpperCase();
    }

    public static boolean isEqual(ComparableEnum anEnum, String value) {
        return anEnum.getUpperCase().equals(value.toUpperCase());
    }
}