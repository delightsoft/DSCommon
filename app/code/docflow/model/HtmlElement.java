package code.docflow.model;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.yaml.annotations.NotYamlField;
import code.docflow.yaml.annotations.TargetField;
import code.docflow.yaml.annotations.WithCompositeKeyHandler;
import code.docflow.yaml.compositeKeyHandlers.HtmlElementCompositeKeyHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

@WithCompositeKeyHandler(HtmlElementCompositeKeyHandler.class)
public class HtmlElement {

    public enum Type {
        FIELD,
        GROUP_FIELD,
        GROUP_EXPR,
        DIV,
        TAG
    };

    @NotYamlField
    public int line;

    @NotYamlField
    public Type type;

    @NotYamlField
    public String value;

    @NotYamlField
    public String classes;

    // Note: Those args are processed just the same as in Play GroovyTemplateCompiler (Line 250+)
    @NotYamlField
    public String args;

    @TargetField
    public LinkedHashMap<Integer, HtmlElement> innerHtml;
}
