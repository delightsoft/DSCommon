package code.docflow.model;

import code.docflow.yaml.annotations.NotYamlField;

import java.util.TreeMap;
import java.util.TreeSet;

public class FieldReference extends Field {
    public String refDocument;

    @Override
    public void mergeTo(Field field) {
        super.mergeTo(field);

        ((FieldReference) field).refDocument = refDocument;
        field.accessedFields.add("REFDOCUMENT");
    }

    @Override
    public Field deepCopy() {
        FieldReference fld = new FieldReference();
        deepCopy(fld);
        return fld;
    }

    protected void deepCopy(FieldReference fld) {
        super.deepCopy(fld);
        fld.refDocument = refDocument;
    }
}