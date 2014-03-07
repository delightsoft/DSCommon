package code.docflow.yaml.converters;

import code.utils.Builder;
import code.utils.PrimitiveType;
import code.utils.TypeBuildersFactory;
import com.google.common.base.Preconditions;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public abstract class PrimitiveTypeConverter extends Builder {

    public abstract Object convert(String value) throws InvalidValueException;

    public abstract String getTypeTitle();

    protected PrimitiveTypeConverter() {
    }

    @Override
    protected void init() {
        // nothing
    }

    public static final class InvalidValueException extends Exception {
        public static final InvalidValueException instance = new InvalidValueException();

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this; // and do nothing else
        }
    }

    public static PrimitiveTypeConverter getConverter(Class<?> type) {
        switch (PrimitiveType.get(type)) {
            case StringType:
                return STRING_CONVERTER;
            case booleanType:
                return BOOLEAN_CONVERTER;
            case BooleanType:
                return BOOLEAN_OR_NULL_CONVERTER;
            case intType:
                return INTEGER_CONVERTER;
            case IntegerType:
                return INTEGER_OR_NULL_CONVERTER;
            case longType:
                return LONG_CONVERTER;
            case LongType:
                return LONG_OR_NULL_CONVERTER;
            case doubleType:
                return DOUBLE_CONVERTER;
            case DoubleType:
                return DOUBLE_OR_NULL_CONVERTER;
            case EnumType:
                return ENUM_CONVERTERS.get(type);
            case NotPrimitiveOrPrimitiveWrapper:
                return OBJECT_CONVERTER;
            default:
                Preconditions.checkState(false);
                return null;
        }
    }

    public static final PrimitiveTypeConverter STRING_CONVERTER = new PrimitiveTypeConverter() {
        public String getTypeTitle() {
            return "string";
        }

        public Object convert(String value) throws InvalidValueException {
            return value;
        }
    };

    private static boolean yamlBoolean(String value) throws InvalidValueException {
        final String v = value.toLowerCase();
        if ("y".equals(v) || "yes".equals(v) || "true".equals(v) || "on".equals(v))
            return true;
        if ("n".equals(v) || "no".equals(v) || "false".equals(v) || "off".equals(v))
            return false;
        throw new InvalidValueException();
    }

    private static boolean yamlNull(String value) {
        final String v = value.toLowerCase();
        return ("".equals(v) || "~".equals(v) || "null".equals(v));
    }

    public static final PrimitiveTypeConverter OBJECT_CONVERTER = new PrimitiveTypeConverter() {
        public String getTypeTitle() {
            return "object";
        }

        public Object convert(String value) throws InvalidValueException {
            if (yamlNull(value))
                return null;
            try {
                return yamlBoolean(value);
            } catch (InvalidValueException e1) {
                try {
                    return Long.parseLong(value);
                } catch (NumberFormatException e2) {
                    try {
                        return Double.parseDouble(value);
                    } catch (NumberFormatException e3) {
                        return value;
                    }
                }
            }
        }
    };

    public static final PrimitiveTypeConverter BOOLEAN_CONVERTER = new PrimitiveTypeConverter() {
        public String getTypeTitle() {
            return "bool";
        }

        public Object convert(String value) throws InvalidValueException {
            return yamlBoolean(value);
        }
    };

    public static final PrimitiveTypeConverter BOOLEAN_OR_NULL_CONVERTER = new PrimitiveTypeConverter() {
        public String getTypeTitle() {
            return "bool?";
        }

        public Object convert(String value) throws InvalidValueException {
            if (yamlNull(value))
                return null;
            return yamlBoolean(value);
        }
    };

    public static final PrimitiveTypeConverter INTEGER_CONVERTER = new PrimitiveTypeConverter() {
        public String getTypeTitle() {
            return "int";
        }

        public Object convert(String value) throws InvalidValueException {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new InvalidValueException();
            }
        }
    };

    public static final PrimitiveTypeConverter INTEGER_OR_NULL_CONVERTER = new PrimitiveTypeConverter() {
        public String getTypeTitle() {
            return "int?";
        }

        public Object convert(String value) throws InvalidValueException {
            try {
                if (yamlNull(value))
                    return null;
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new InvalidValueException();
            }
        }
    };

    public static final PrimitiveTypeConverter LONG_CONVERTER = new PrimitiveTypeConverter() {
        public String getTypeTitle() {
            return "long";
        }

        public Object convert(String value) throws InvalidValueException {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                throw new InvalidValueException();
            }
        }
    };

    public static final PrimitiveTypeConverter LONG_OR_NULL_CONVERTER = new PrimitiveTypeConverter() {
        public String getTypeTitle() {
            return "long?";
        }

        public Object convert(String value) throws InvalidValueException {
            try {
                if (yamlNull(value))
                    return null;
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                throw new InvalidValueException();
            }
        }
    };

    public static final PrimitiveTypeConverter DOUBLE_CONVERTER = new PrimitiveTypeConverter() {
        public String getTypeTitle() {
            return "double";
        }

        public Object convert(String value) throws InvalidValueException {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                throw new InvalidValueException();
            }
        }
    };

    public static final PrimitiveTypeConverter DOUBLE_OR_NULL_CONVERTER = new PrimitiveTypeConverter() {
        public String getTypeTitle() {
            return "double";
        }

        public Object convert(String value) throws InvalidValueException {
            try {
                if (yamlNull(value))
                    return null;
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                throw new InvalidValueException();
            }
        }
    };

    @SuppressWarnings("unchecked")
    public static final TypeBuildersFactory<PrimitiveTypeConverter> ENUM_CONVERTERS = new TypeBuildersFactory<PrimitiveTypeConverter>() {
        @Override
        public PrimitiveTypeConverter newInstance(final TypeDescription typeDesc) {
            final String title = typeDesc.type.getName();
            return new PrimitiveTypeConverter() {
                public String getTypeTitle() {
                    return title;
                }

                public Object convert(String value) throws InvalidValueException {
                    try {
                        return Enum.valueOf(typeDesc.type, value.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new InvalidValueException();
                    }
                }
            };
        }
    };
}
