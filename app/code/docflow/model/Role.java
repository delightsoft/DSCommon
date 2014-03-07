package code.docflow.model;

import code.docflow.collections.Item;
import code.docflow.yaml.annotations.NotYamlField;
import play.vfs.VirtualFile;

import java.util.LinkedHashMap;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class Role extends RootElement {

    public LinkedHashMap<String, RoleDocument> documents;

    public int index;

    @NotYamlField
    public boolean system;

    /**
     * Play path, where role descriptions was found.  Used to distinguish production and test documents.
     */
    @NotYamlField
    public VirtualFile sourcePath;
}
