package code.controlflow;

import com.google.common.base.Objects;
import com.google.common.base.Strings;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Объекты класса, используются как коды результата выполнения операций.
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
        return Objects.toStringHelper(this)
                .add("name", name)
                .add("severity", severity)
                .toString();
    }
}
