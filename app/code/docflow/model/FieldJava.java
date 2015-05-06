package code.docflow.model;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

public class FieldJava extends Field {

    public String javaType;

    @Override
    public void mergeTo(Field field) {

        super.mergeTo(field);

        FieldJava fieldEnum = (FieldJava) field;

        fieldEnum.javaType = javaType;
        field.accessedFields.add("JAVATYPE");
    }

    @Override
    public Field deepCopy() {
        FieldJava fld = new FieldJava();
        deepCopy(fld);
        return fld;
    }

    protected void deepCopy(FieldJava fld) {
        super.deepCopy(fld);
        fld.javaType = javaType;
    }
}
