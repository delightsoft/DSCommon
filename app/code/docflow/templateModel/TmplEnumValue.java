package code.docflow.templateModel;

import code.docflow.model.FieldEnum;
import code.docflow.model.FieldEnumItem;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class TmplEnumValue {

    /**
     * Title in form for localization.
     */
    String title;

    /**
     * Name of enum value.
     */
    String name;

    /**
     * Color to be used as item header background.
     */
    String color;

    public static TmplEnumValue buildFor(TmplDocument tmplDoc, String rootTitle, FieldEnum fieldEnum, FieldEnumItem value) {

        final TmplEnumValue res = new TmplEnumValue();
        res.title = fieldEnum.udtType != null ?
                TmplField.ENUM_ROOT + fieldEnum.udtType + TmplField.ENUM_LINK + value.name :
                rootTitle + TmplField.ENUM_LINK + value.name;
        res.name = value.name;
        res.color = value.color;

        tmplDoc.enumTitle.put((fieldEnum.enumTypeName + "." + res.name).toUpperCase(), res.title);

        return res;
    }

    public String getTitle() {
        return title;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }
}
