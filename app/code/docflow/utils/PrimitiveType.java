package code.docflow.utils;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */

import org.joda.time.*;

import java.util.TreeMap;
import java.util.UUID;

/**
 * Enum that allows to write "switch(PrimitiveType.get(obj.class.getName())) { ... }" statements.
 */
public enum PrimitiveType {
    StringType(String.class.getName()),
    booleanType(boolean.class.getName()),
    BooleanType(Boolean.class.getName()),
    byteType(byte.class.getName()),
    ByteType(Byte.class.getName()),
    shortType(short.class.getName()),
    ShortType(Short.class.getName()),
    intType(int.class.getName()),
    IntegerType(Integer.class.getName()),
    longType(long.class.getName()),
    LongType(Long.class.getName()),
    floatType(float.class.getName()),
    FloatType(Float.class.getName()),
    doubleType(double.class.getName()),
    DoubleType(Double.class.getName()),
    charType(char.class.getName()),
    CharacterType(Character.class.getName()),
    DateType(LocalDate.class.getName()),
    TimeType(LocalTime.class.getName()),
    DateTimeType(DateTime.class.getName()),
    LocalDateTimeType(LocalDateTime.class.getName()),
    PeriodType(Period.class.getName()),
    IntervalType(Interval.class.getName()),
    DurationType(Duration.class.getName()),
    UUIDType(UUID.class.getName()),
    EnumType(""),
    NotPrimitiveOrPrimitiveWrapper("");

    String className;

    PrimitiveType(String className) {
        this.className = className;
    }

    static TreeMap<String, PrimitiveType> nameToType;

    public static PrimitiveType get(Class<?> type) {
        if (type.isEnum())
            return EnumType;
        final PrimitiveType res = nameToType.get(type.getName());
        return res != null ? res : PrimitiveType.NotPrimitiveOrPrimitiveWrapper;
    }

    static {
        // fullfill name to types
        nameToType = new TreeMap<String, PrimitiveType>();
        final PrimitiveType[] values = PrimitiveType.values();
        final int length = values.length - 1; // skip last element
        for (int i = 0; i < length; i++) {
            PrimitiveType value = values[i];
            nameToType.put(value.className, value);
        }
    }
}
