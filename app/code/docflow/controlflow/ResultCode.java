package code.docflow.controlflow;

import com.google.common.base.Strings;
import play.exceptions.UnexpectedException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Represents a result of an operation.
 */
public class ResultCode {
    public final String name;
    public final int severity;

    /**
     * @param severity Bigger is worse
     */
    public ResultCode(String name, int severity) {
        checkArgument(!Strings.isNullOrEmpty(name), "name");
        this.name = name;
        this.severity = severity;
    }

    /**
     * True, if this ResultCode worse then given code.
     */
    public final boolean isWorseThen(ResultCode code) {
        return this.severity > code.severity;
    }

    public String toString() {
        return severity + " (" + name + ")";
    }

    // Sample values:
    //   0 (Ok)
    // 100(Failed)
    public static Pattern parseResultPattern = Pattern.compile("^\\s*(?:(?:(\\d*)\\s*\\(\\s*(\\w+)\\s*\\))|(\\w+))\\s*$");

    static Map<String, ResultCode> results;

    /**
     * Lazy 'results' init.  This cannot be a static constructure, by that time static final fields of Result remains empty.
     */
    public static void init() {
        if (results == null) {
            synchronized (ResultCode.class) {
                if (results == null) {
                    results = new TreeMap<String, ResultCode>();
                    for (Field field : Result.class.getFields()) {
                        final int mod = field.getModifiers();
                        if (Modifier.isPublic(mod) && Modifier.isStatic(mod) && field.getType() == ResultCode.class)
                            try {
                                results.put(field.getName().toUpperCase(), (ResultCode) field.get(null));
                            } catch (IllegalAccessException e) {
                                throw new UnexpectedException(e);
                            }
                    }
                }
            }
        }
    }

    public static ResultCode parse(String resultCode) throws IllegalArgumentException {
        init();
        if (!Strings.isNullOrEmpty(resultCode)) {
            final Matcher matcher = parseResultPattern.matcher(resultCode);
            if (matcher.find())
                if (matcher.group(1) != null)
                    try {
                        final ResultCode v = results.get(matcher.group(2).toUpperCase());
                        if (v != null)
                            return v;
                        return new ResultCode(matcher.group(2), Integer.parseInt(matcher.group(1)));
                    } catch (NumberFormatException e) {
                        // fallthru
                    }
                else {
                    final ResultCode v = results.get(matcher.group(3).toUpperCase());
                    if (v != null)
                        return v;
                }
        }
        throw new IllegalArgumentException(resultCode);
    }
}
