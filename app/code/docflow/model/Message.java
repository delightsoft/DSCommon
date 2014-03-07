package code.docflow.model;

import code.docflow.yaml.annotations.NotYamlField;
import code.docflow.yaml.annotations.WithCompositeKeyHandler;
import code.docflow.yaml.compositeKeyHandlers.MessageCompositeKeyHandler;

import java.util.TreeMap;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
@WithCompositeKeyHandler(MessageCompositeKeyHandler.class)
public class Message {

    public enum Type {
        ERROR,
        /**
         * Use as notification.
         */
        WARN,
        INFO,
    }

    public String key;

    public Type type;

    @NotYamlField
    public String fieldName;

    @NotYamlField
    public String resultCode;

    @NotYamlField
    public TreeMap<String, String> params;

    public String en;
    public String ru;
    public String ruHtml;
}
