package code.docflow.model;

import code.docflow.yaml.annotations.NotYamlField;
import code.docflow.yaml.annotations.TargetField;
import code.docflow.utils.BitArray;

import java.util.LinkedHashMap;
import java.util.Map;

public class FieldStructure extends Field {

    @TargetField
    public LinkedHashMap<String, Field> fields;

    @NotYamlField
    public boolean single;

    @NotYamlField
    public boolean tags;

    @Override
    public void mergeTo(Field field) {

        super.mergeTo(field);

        final FieldStructure dst = (FieldStructure) field;
        dst.fields = fields;
        dst.single = single;
        dst.type = type;
        field.accessedFields.add("FIELDS");
    }

    @NotYamlField
    public Entity entity;

    /**
     * Only field that belongs to structure.
     */
    @NotYamlField
    public BitArray levelMask;

    /**
     * Field that belongs to structure and substructures.
     */
    @NotYamlField
    public BitArray mask;

    @Override
    public Field deepCopy() {
        FieldStructure fld = new FieldStructure();
        deepCopy(fld);
        return fld;
    }

    protected void deepCopy(FieldStructure fld) {
        super.deepCopy(fld);
        fld.type = type;
        fld.single = single;

        if (fields != null) {
            fld.fields = new LinkedHashMap<String, Field>(fields.size());
            for (Map.Entry<String, Field> entry : fields.entrySet())
                fld.fields.put(entry.getKey(), entry.getValue().deepCopy());
        }
    }
}