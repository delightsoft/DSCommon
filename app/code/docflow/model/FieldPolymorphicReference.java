package code.docflow.model;

import code.docflow.yaml.annotations.NotYamlField;
import code.docflow.yaml.annotations.TargetField;

import java.util.TreeSet;

public class FieldPolymorphicReference extends Field {
    @TargetField
    public String[] refDocuments;

    public FieldPolymorphicReference() {
        indexFlag = true;
    }

    @Override
    public void mergeTo(Field field) {
        super.mergeTo(field);

        ((FieldPolymorphicReference) field).refDocuments = refDocuments;
        field.accessedFields.add("REFDOCUMENTS");
    }

    @NotYamlField
    public TreeSet<String> refDocumentsNames;

    @Override
    public Field deepCopy() {
        FieldPolymorphicReference fld = new FieldPolymorphicReference();
        deepCopy(fld);
        return fld;
    }

    protected void deepCopy(FieldPolymorphicReference fld) {
        super.deepCopy(fld);
        fld.refDocuments = refDocuments;
    }
}