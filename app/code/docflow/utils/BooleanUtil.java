package code.docflow.utils;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import com.google.common.base.Strings;

public class BooleanUtil {

    public static boolean parse(String scriptRedirectParam) {
        return stringToBoolean(scriptRedirectParam, false);
    }

    public static Boolean stringToBoolean(String value, final Boolean defaultValue) {
        if (value == null)
            return defaultValue;
        if ((value = value.trim()).length() == 0)
            return defaultValue;
        value = value.toLowerCase();
        if (defaultValue == null || !defaultValue) {
            if (value.equalsIgnoreCase("1") || value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes"))
                return true;
        } else if (defaultValue == null || defaultValue) {
            if (value.equalsIgnoreCase("0") || value.equalsIgnoreCase("false") || value.equalsIgnoreCase("no"))
                return false;
        }
        return defaultValue;
    }
}
