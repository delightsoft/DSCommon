package code.utils;

import com.google.common.base.Strings;
import play.templates.JavaExtensions;

import java.util.ArrayList;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class NamesUtil extends JavaExtensions {

    // Sample: ABCBigTest or just to be complete testSomethingABC


    public static ArrayList<String> splitByUpperLetters(String name) {

        checkArgument(!Strings.isNullOrEmpty(name));

        int s = 0;
        int p = 0;
        ArrayList<String> res = new ArrayList<String>();
        for (int i = 0; i < name.length(); i++) {
            boolean upperCase = Character.isUpperCase(name.charAt(i));
            switch (s) {
                case 0:
                    s = upperCase ? 2 : 1;
                    break;
                case 1:
                    if (upperCase) {
                        res.add(name.substring(p, i).toLowerCase());
                        p = i;
                        s = 2;
                    }
                    break;
                case 2:
                    if (!upperCase) {
                        if ((i - p) > 2) {
                            res.add(name.substring(p, i - 1));
                            p = i - 1;
                        }
                        s = 1;
                    }
                    break;
            }
        }
        switch (s) {
            case 1:
                res.add(name.substring(p).toLowerCase());
                break;
            case 2:
                res.add(name.substring(p));
                break;
        }

        return res;
    }

    public static String wordsToUnderscoreSeparated(String key) {
        final ArrayList<String> split = splitByUpperLetters(key);
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < split.size(); i++) {
            if (i > 0)
                sb.append('_');
            sb.append(split.get(i).toLowerCase());
        }
        return sb.toString();
    }

    public static String wordsToUpperUnderscoreSeparated(String key) {
        final ArrayList<String> split = splitByUpperLetters(key);
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < split.size(); i++) {
            if (i > 0)
                sb.append('_');
            sb.append(split.get(i).toUpperCase());
        }
        return sb.toString();
    }

    public static String turnFirstLetterInUpperCase(String key) {
        checkArgument(!Strings.isNullOrEmpty(key));
//        if (key.length() == 1)
//            return key.toUpperCase();
//        else
//            return key.substring(0, 1).toUpperCase() + key.substring(1);

        ArrayList<String> strings = splitByUpperLetters(key);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < strings.size(); i++) {
            String word = strings.get(i);
            if (Character.isUpperCase(word.charAt(0)))
                sb.append(word);
            else {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1)
                    sb.append(word.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    public static String turnFirstLetterInLowerCase(String key) {
        checkArgument(!Strings.isNullOrEmpty(key));
//        if (key.length() == 1)
//            return key.toLowerCase();
//        else
//            return key.substring(0, 1).toLowerCase() + key.substring(1);

        ArrayList<String> strings = splitByUpperLetters(key);
        StringBuilder sb = new StringBuilder();
        String word = strings.get(0);
        if (Character.isUpperCase(word.charAt(0)))
            sb.append(word.toLowerCase());
        else
            sb.append(word);
        for (int i = 1; i < strings.size(); i++) {
            word = strings.get(i);
            if (Character.isUpperCase(word.charAt(0)))
                sb.append(word);
            else {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1)
                    sb.append(word.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    public static String wordsToCamelCase(String wordsSeparatedBySpace) {
        final String[] strings = wordsSeparatedBySpace.split("\\s");
        if (strings.length == 1)
            return strings[0];
        String f = strings[0];
        final StringBuilder sb = new StringBuilder(f.toLowerCase());
        for (int i = 1; i < strings.length; i++) {
            final String s = strings[i];
            if (s.length() == 1 || Character.isUpperCase(s.charAt(1)))
                sb.append(s);
            else {
                sb.append(s.substring(0, 1).toUpperCase());
                sb.append(s.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    public static String wordsToCamelCase(ArrayList<String> strings) {

        checkNotNull(strings);
        checkArgument(strings.size() > 0);

        if (strings.size() == 1)
            return strings.get(0);
        String f = strings.get(0);
        final StringBuilder sb = new StringBuilder(f.toLowerCase());
        for (int i = 1; i < strings.size(); i++) {
            String s = strings.get(i);
            if (s.length() == 1 || Character.isUpperCase(s.charAt(1)))
                sb.append(s);
            else {
                sb.append(s.substring(0, 1).toUpperCase());
                sb.append(s.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    public static String listToString(ArrayList<String> strings) {

        checkNotNull(strings);
        checkArgument(strings.size() > 0);

        if (strings.size() == 1)
            return strings.get(0);
        final StringBuilder sb = new StringBuilder(strings.get(0));
        for (int i = 1; i < strings.size(); i++) {
            sb.append(" ").append(strings.get(i));
        }
        return sb.toString();
    }

}