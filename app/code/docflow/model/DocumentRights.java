package code.docflow.model;

import code.docflow.yaml.annotations.NotYamlField;
import code.utils.BitArray;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class DocumentRights {

    public DocType document;

    @NotYamlField
    public BitArray viewMask;

    @NotYamlField
    public BitArray updateMask;

    @NotYamlField
    public BitArray actionsMask;
}
