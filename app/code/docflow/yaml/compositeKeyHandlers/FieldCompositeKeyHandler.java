package code.docflow.yaml.compositeKeyHandlers;

import code.docflow.compiler.Compiler410LinkToCode;
import code.docflow.controlflow.Result;
import code.docflow.collections.Item;
import code.docflow.compiler.enums.BuiltInTypes;
import code.docflow.model.*;
import code.docflow.yaml.CompositeKeyHandler;
import code.docflow.yaml.YamlMessages;
import code.docflow.yaml.YamlParser;
import code.docflow.utils.NamesUtil;
import models.DocflowFile;
import play.exceptions.UnexpectedException;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class FieldCompositeKeyHandler implements CompositeKeyHandler<String, Field> {

    // See 'maximum phone-number' in https://code.google.com/p/libphonenumber/source/browse/trunk/java/release_notes.txt?r=574
    // Plus extention, sign plus and 'x' for extention.  Experementally I found that libphone allows up to 22 digits including extention.
    public static final int PHONE_FIELD_LENGTH = 24;
    // See http://www.rfc-editor.org/errata_search.php?rfc=3696&eid=1690
    public static final int MAX_EMAIL_LENGTH = 254;

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


        BuiltInTypes type = null;
        // Synonyms: struct -> structure; ref -> refers; boolean -> bool
        if ("struct".equalsIgnoreCase(typeStr))
            type = BuiltInTypes.STRUCTURE;
        else if ("ref".equalsIgnoreCase(typeStr))
            type = BuiltInTypes.REFERS;
        else if ("boolean".equalsIgnoreCase(typeStr))
            type = BuiltInTypes.BOOL;
        else
            try {
                type = BuiltInTypes.valueOf(typeStr.toUpperCase());
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
                case PHONE:
                    final FieldSimple fieldPhone = new FieldSimple();
                    fieldPhone.maxLength = 40;
                    fieldPhone.length = PHONE_FIELD_LENGTH;
                    fld = fieldPhone;
                    break;
                case EMAIL:
                    final FieldSimple fieldEmail = new FieldSimple();
                    fieldEmail.length = fieldEmail.maxLength = MAX_EMAIL_LENGTH;
                    fld = fieldEmail;
                    break;
                case UUID:
                    final FieldSimple fieldUuid = new FieldSimple();
                    fld = fieldUuid;
                    break;
                case JSONTEXT:
                    final FieldSimple fieldJson = new FieldSimple();
                    fld = fieldJson;
                    break;
                case FILE:
                    final FieldReference fieldFile = new FieldReference();
                    fld = fieldFile;
                    fieldFile.refDocument = DocflowFile.class.getSimpleName();
                    accessedFields.add("REFDOCUMENT");
                    break;
                case REFERS:
                case TAGS:
                    final boolean isTags = (type == BuiltInTypes.TAGS);
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
                        type = BuiltInTypes.POLYMORPHIC_REFERS;
                        final FieldPolymorphicReference fieldPolymorphicReference = new FieldPolymorphicReference();
                        fld = fieldPolymorphicReference;
                        if (stringToSplit != null && !"_ANY".equalsIgnoreCase(stringToSplit.trim())) {
                            final String[] docTypes = stringToSplit.split(",");
                            if (docTypes[0].trim().length() == 0)
                                fieldPolymorphicReference.refDocuments = new String[0];
                            else {
                                fieldPolymorphicReference.refDocuments = new String[docTypes.length];
                                accessedFields.add("REFDOCUMENTS");
                                for (int i = 0; i < docTypes.length; i++) {
                                    String docType = docTypes[i];
                                    fieldPolymorphicReference.refDocuments[i] = docType.trim();
                                }
                            }
                        }
                    } else { // it's simple reference
                        type = BuiltInTypes.REFERS;
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
                        type = BuiltInTypes.TAGS;
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
                    fieldStructure.single = (type == BuiltInTypes.STRUCTURE);
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
                    fieldEnum.enumTypeName = Compiler410LinkToCode.ENUMS_PACKAGE + NamesUtil.turnFirstLetterInUpperCase(udtTypeName);
                    break;
                case JAVA:
                    FieldJava fieldCalculated = new FieldJava();
                    fld = fieldCalculated;
                    fieldCalculated.javaType = matcher.group(4) != null ? matcher.group(4).trim() : "Object";
                    accessedFields.add("JAVATYPE");
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

    public static void processFlags(Item model, String flagsString, HashSet<String> accessedFields, YamlParser parser, Result result) {
        ItemCompositeKeyHandler.FlagsAccessor flagsAccessor = ItemCompositeKeyHandler.flagsAccessorsFactory.get(model.getClass());
        for (String word : flagsString.split("\\s+")) {
            if (word.isEmpty())
                continue;
            if (word.startsWith("_")) { // it's group
                if (model._groups == null)
                    model._groups = new HashSet<String>();
                model._groups.add(word.substring(1).toUpperCase());
            } else {
                boolean setToTrue = true;
                if (word.startsWith("!")) {
                    word = word.substring(1);
                    setToTrue = false;
                }
                java.lang.reflect.Field f = null;
                // Synonym: null -> nullable
                if ("null".equalsIgnoreCase(word))
                    f = flagsAccessor.flags.get("nullable");
                else if ("index".equalsIgnoreCase(word))
                    f = flagsAccessor.flags.get("indexFlag");
                else
                    f = flagsAccessor.flags.get(word);
                if (f == null) {
                    result.addMsg(YamlMessages.error_UnknownFlag, parser.getSavedFilePosition(), word);
                    continue;
                }
                try {
                    f.setBoolean(model, setToTrue);
                    accessedFields.add(word.toUpperCase());
                } catch (IllegalAccessException e) {
                    throw new UnexpectedException(e);
                }
            }
        }
    }
}
