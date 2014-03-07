package code.docflow.model;

/**
 * Document.fields sequence item.
 */

public class FieldSimple extends Field {

    public static String[] typeAttrs = new String[]{"min", "max", "minLength", "maxLength", "patter", "document"};

    /**
     * Length of string in db.  May be longer then maxLength.
     */
    public int length;

    public double min;
    public double max;
    public int minLength;
    public int maxLength;
    public String pattern;

    @Override
    public void mergeTo(Field field) {

        super.mergeTo(field);

        final FieldSimple sf = (FieldSimple) field;
        if (accessedFields.contains("LENGTH") && !sf.accessedFields.contains("LENGTH")) {
            sf.length = length;
            sf.accessedFields.add("LENGTH");
        }
        if (accessedFields.contains("MIN") && !sf.accessedFields.contains("MIN")) {
            sf.min = min;
            sf.accessedFields.add("MIN");
        }
        if (accessedFields.contains("MAX") && !sf.accessedFields.contains("MAX")) {
            sf.max = max;
            sf.accessedFields.add("MAX");
        }
        if (accessedFields.contains("MINLENGTH") && !sf.accessedFields.contains("MINLENGTH")) {
            sf.minLength = minLength;
            sf.accessedFields.add("MINLENGTH");
        }
        if (accessedFields.contains("MAXLENGTH") && !sf.accessedFields.contains("MAXLENGTH")) {
            sf.maxLength = maxLength;
            sf.accessedFields.add("MAXLENGTH");
        }
        if (accessedFields.contains("PATTERN") && !sf.accessedFields.contains("PATTERN")) {
            sf.pattern = pattern;
            sf.accessedFields.add("PATTERN");
        }
    }

    @Override
    public Field deepCopy() {
        FieldSimple fld = new FieldSimple();
        deepCopy(fld);
        return fld;
    }

    protected void deepCopy(FieldSimple fld) {
        super.deepCopy(fld);
        fld.length = length;
        fld.min = min;
        fld.max = max;
        fld.minLength = minLength;
        fld.maxLength = maxLength;
        fld.pattern = pattern;
    }
}
