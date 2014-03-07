package code.docflow.model;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.collections.Item;
import code.docflow.yaml.annotations.NotYamlField;
import code.docflow.yaml.annotations.TargetField;

import java.util.LinkedHashMap;

public class FieldEnum extends Field {

    public boolean multiple;

    @TargetField
    public LinkedHashMap<String, FieldEnumItem> strValues;

    @NotYamlField
    public String enumTypeName;

    @NotYamlField
    public Class enumType;

    @NotYamlField
    public LinkedHashMap<String, Enum> values;

    @Override
    public void mergeTo(Field field) {

        super.mergeTo(field);

        FieldEnum fieldEnum = (FieldEnum) field;

        fieldEnum.multiple = multiple;
        field.accessedFields.add("MULTIPLE");

        fieldEnum.strValues = strValues;
        field.accessedFields.add("STRVALUES");

        fieldEnum.enumTypeName = enumTypeName;
    }

    @Override
    public Field deepCopy() {
        FieldEnum fld = new FieldEnum();
        deepCopy(fld);
        return fld;
    }

    protected void deepCopy(FieldEnum fld) {
        super.deepCopy(fld);
        fld.multiple = multiple;
        fld.strValues = strValues;
        fld.enumTypeName = enumTypeName;
    }
}
