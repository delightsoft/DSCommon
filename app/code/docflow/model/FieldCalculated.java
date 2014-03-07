package code.docflow.model;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.collections.Item;
import code.docflow.yaml.annotations.NotYamlField;
import code.docflow.yaml.annotations.TargetField;

import java.util.LinkedHashMap;

public class FieldCalculated extends Field {

    public String javaType;

    @Override
    public void mergeTo(Field field) {

        super.mergeTo(field);

        FieldCalculated fieldEnum = (FieldCalculated) field;

        fieldEnum.javaType = javaType;
        field.accessedFields.add("JAVATYPE");
    }

    @Override
    public Field deepCopy() {
        FieldCalculated fld = new FieldCalculated();
        deepCopy(fld);
        return fld;
    }

    protected void deepCopy(FieldCalculated fld) {
        super.deepCopy(fld);
        fld.javaType = javaType;
    }
}
