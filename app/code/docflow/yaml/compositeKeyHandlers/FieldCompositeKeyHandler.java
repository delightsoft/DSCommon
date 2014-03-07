package code.docflow.yaml.compositeKeyHandlers;

import code.controlflow.Result;
import code.docflow.DocflowConfig;
import code.docflow.model.*;
import code.docflow.yaml.CompositeKeyHandler;
import code.docflow.yaml.YamlMessages;
import code.docflow.yaml.YamlParser;
import code.utils.NamesUtil;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class FieldCompositeKeyHandler implements CompositeKeyHandler<String, Field> {

    // Example: f1 type() attr1 attr2
    public static Pattern fieldNameAndType = Pattern.compile("^\\s*(\\S+)\\s+([^\\s\\(\\[]+)(\\s*\\(([^\\)]*)\\))?");
    public static Pattern documentNameAfterRefers = Pattern.compile("\\s*(?:\\[([^\\]]*)\\]|(\\S*))");
    public static Pattern nameAfterStructure = Pattern.compile("\\s+\\((?:(\\S*))\\)");
    public static final String FIELD_TAG = "TAG";

    @Override
    public Field parse(String value, HashSet<String> accessedFields, Class collectionType, YamlParser parser, Result result) {
        final Matcher matcher = fieldNameAndType.matcher(value);
        if (!matcher.find()) {
            result.addMsg(YamlMessages.error_InvalidFieldDescription, parser.getSavedFilePosition(), parser.getSavedValue());
            return null;
        }

        final String typeStr = matcher.group(2).trim();

        Field.Type type = null;
        try {
            type = Field.Type.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            // it's got to be UDT (user defined type)
        }

        // create Field object accordingly to type.  if type is unknown at hits point, FieldSimple will be created
        Field fld = null;
        int restPos = matcher.end();
        if (type == null)
            fld = new FieldSimple();
        else
            switch (type) {
                case REFERS:
                case TAGS:
                    final boolean isTags = (type == Field.Type.TAGS);
                    final Matcher matcher1 = documentNameAfterRefers.matcher(value);
                    if (!matcher1.find(matcher.end())) {
                        result.addMsg(!isTags ? YamlMessages.error_DocumentClassNameIsMissingAfterRefers :
                                YamlMessages.error_DocumentClassNameIsMissingAfterTags, parser.getSavedFilePosition());
                        parser.skipThisValue();
                        return null;
                    }
                    restPos = matcher1.end();
                    if (matcher1.group(1) != null) { // it's polymorphic reference
                        final String stringToSplit = matcher1.group(1);
                        type = Field.Type.POLYMORPHIC_REFERS;
                        final FieldPolymorphicReference fieldPolymorphicReference = new FieldPolymorphicReference();
                        fld = fieldPolymorphicReference;
                        if (stringToSplit != null && !"_ANY".equalsIgnoreCase(stringToSplit.trim())) {
                            final String[] docTypes = stringToSplit.split(",");
                            fieldPolymorphicReference.refDocuments = new String[docTypes.length];
                            accessedFields.add("REFDOCUMENTS");
                            for (int i = 0; i < docTypes.length; i++) {
                                String docType = docTypes[i];
                                fieldPolymorphicReference.refDocuments[i] = docType.trim();
                            }
                        }
                    } else { // it's simple reference
                        type = Field.Type.REFERS;
                        final FieldReference fieldReference = new FieldReference();
                        fld = fieldReference;
                        fieldReference.refDocument = matcher1.group(2);
                        accessedFields.add("REFDOCUMENT");
                    }
                    if (isTags) {

                        // Hack:
                        if (fld instanceof FieldPolymorphicReference) {
                            result.addMsg(YamlMessages.error_TagsCanOnlyRefersSingleDocType, parser.getSavedFilePosition());
                            parser.skipThisValue();
                            return null;
                        }

                        // finish internal field
                        fld.name = "tag";
                        accessedFields.add("NAME");
                        if (type == null) {
                            fld.udtType = typeStr;
                            accessedFields.add("UDTTYPE");
                        } else {
                            fld.type = type;
                            accessedFields.add("TYPE");
                        }
                        fld.accessedFields = accessedFields;

                        // make subtable with 'tag' field within
                        FieldStructure tagsField = new FieldStructure();
                        tagsField.fields = new LinkedHashMap<String, Field>();
                        tagsField.fields.put(fld.name.toUpperCase(), fld);

                        // return subtable as field
                        accessedFields = new HashSet<String>();
                        type = Field.Type.TAGS;
                        fld = tagsField;
                        if (matcher.group(4) != null) {
                            // TODO: I'm sure that is this for.  Invistigate !!!
                            tagsField.udtType = matcher.group(4).trim();
                            accessedFields.add("FIELDS");
                        }
                    }
                    break;
                case STRUCTURE:
                case SUBTABLE:
                    FieldStructure fieldStructure = new FieldStructure();
                    fieldStructure.single = (type == Field.Type.STRUCTURE);
                    fld = fieldStructure;
                    if (matcher.group(4) != null) {
                        fieldStructure.udtType = matcher.group(4).trim();
                        accessedFields.add("FIELDS");
                    }
                    break;
                case ENUM:
                    FieldEnum fieldEnum = new FieldEnum();
                    fld = fieldEnum;
                    if (matcher.group(4) != null) {
                        fieldEnum.udtType = matcher.group(4).trim();
                        accessedFields.add("UDTENUMTYPE");
                        accessedFields.add("VALUES");
                    }
                    String udtTypeName = matcher.group(1).trim();
                    fieldEnum.enumTypeName = DocflowConfig.ENUMS_PACKAGE + NamesUtil.turnFirstLetterInUpperCase(udtTypeName);
                    break;
                case CALCULATED:
                    FieldCalculated fieldCalculated = new FieldCalculated();
                    fld = fieldCalculated;
                    fieldCalculated.javaType = matcher.group(4) != null ? matcher.group(4).trim() : "Object";
                    accessedFields.add("JAVATYPE");
                    fieldCalculated.calculated = true;
                    accessedFields.add("CALCULATED");
                    fieldCalculated.derived = true;
                    accessedFields.add("DERIVED");
                    break;
                default:
                    final FieldSimple fieldSimple = new FieldSimple();
                    fld = fieldSimple;
                    switch (type) {
                        case PASSWORD:
                        case STRING:
                            try {
                                final String val = matcher.group(4);
                                fieldSimple.length = Integer.parseInt(val != null ? val.trim() : null);
                                accessedFields.add("LENGTH");
                            } catch (NumberFormatException e) {
                                // TODO: Report mistake
                            }
                            break;
                    }
                    break;
            }

        fld.name = matcher.group(1).trim();
        accessedFields.add("NAME");

        if (type == null) {
            fld.udtType = typeStr;
            accessedFields.add("UDTTYPE");
        } else {
            fld.type = type;
            accessedFields.add("TYPE");
        }

        // keep yaml builder accessed fields info with Field for later use
        fld.accessedFields = accessedFields;

        processFlags(fld, value.substring(restPos), accessedFields, parser, result);

        return fld;
    }

    @Override
    public String key(Field fld) {
        return fld.name.toUpperCase();
    }

    public static void processFlags(Object model, String flagsString, HashSet<String> accessedFields, YamlParser parser, Result result) {
        ItemCompositeKeyHandler.FlagsAccessor flagsAccessor = ItemCompositeKeyHandler.flagsAccessorsFactory.get(model.getClass());
        final String[] words = flagsString.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            String flag = words[i];
            if (flag.isEmpty())
                continue;
            boolean setToTrue = true;
            if (flag.startsWith("!")) {
                flag = flag.substring(1);
                setToTrue = false;
            }
            final java.lang.reflect.Field f = flagsAccessor.flags.get(flag);
            if (f == null) {
                result.addMsg(YamlMessages.error_UnknownFlag, parser.getSavedFilePosition(), flag);
                continue;
            }
            try {
                f.setBoolean(model, setToTrue);
                accessedFields.add(flag.toUpperCase());
            } catch (IllegalAccessException e) {
                // unexpected
            }
        }
    }
}
