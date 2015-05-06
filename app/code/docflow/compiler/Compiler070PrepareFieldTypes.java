package code.docflow.compiler;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.controlflow.Result;
import code.docflow.DocflowConfig;
import code.docflow.compiler.enums.BuiltInTypes;
import code.docflow.model.*;
import code.docflow.yaml.YamlMessages;
import code.docflow.utils.NamesUtil;

import java.util.LinkedHashMap;
import java.util.Stack;

public class Compiler070PrepareFieldTypes {

    public static void doJob(DocflowConfig docflowConfig, Result result) {
        final Stack<String> path = new Stack<String>();

        recurrentProcessStructure(docflowConfig, docflowConfig.fieldTypes, path, result);

        for (Field fldType : docflowConfig.fieldTypes.values())
            fldType.udtTypeRoot = true;

        docflowConfig.udtTypes = new DocType();
        docflowConfig.udtTypes.udt = true;
        docflowConfig.udtTypes.report = true; // at least, it will not be linked to db table
        docflowConfig.udtTypes.name = DocflowConfig.UDT_DOCUMENT;
        docflowConfig.udtTypes.fields = docflowConfig.fieldTypes;
        docflowConfig.documents.put(docflowConfig.udtTypes.name.toUpperCase(), docflowConfig.udtTypes);
    }

    private static void recurrentProcessStructure(DocflowConfig docflowConfig, LinkedHashMap<String, Field> fields, Stack<String> path, Result result) {
        for (Field fldType : fields.values()) {
            if (fldType.udtType != null)
                recurrentProcessFieldType(docflowConfig, fldType, path, result);
            else if (fldType.type == BuiltInTypes.STRUCTURE || fldType.type == BuiltInTypes.SUBTABLE)
                recurrentProcessStructure(docflowConfig, ((FieldStructure) fldType).fields, path, result);
        }
    }

    private static void recurrentProcessFieldType(DocflowConfig docflowConfig, Field fldType, Stack<String> path, Result result) {

        final Field parentType = docflowConfig.fieldTypes.get(fldType.udtType.toUpperCase());
        if (parentType == null) {
            result.addMsg(YamlMessages.error_UDTypeHasUnknownType, fldType.name, fldType.udtType);
            fldType.type = BuiltInTypes.STRING; // Just to let process other fields
            return;
        }

        if (path.contains(parentType.name)) { // it's loop
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < path.size(); i++) {
                if (sb.length() > 0)
                    sb.append(",");
                sb.append(path.get(i));
            }
            result.addMsg(YamlMessages.error_UDTypeCyclingDependenciesWithTypes, fldType.name, sb.toString());
            fldType.type = BuiltInTypes.STRING; // Just to let process other fields
            return;
        }

        if (parentType.type == null) {
            path.push(fldType.name);
            recurrentProcessFieldType(docflowConfig, parentType, path, result);
            path.pop();
        } else if (parentType.type == BuiltInTypes.STRUCTURE || parentType.type == BuiltInTypes.SUBTABLE)
            recurrentProcessStructure(docflowConfig, ((FieldStructure) parentType).fields, path, result);

        // apply named enum type
        if (fldType.type == BuiltInTypes.ENUM) {
            if (fldType.udtType != null) {
                Field type = docflowConfig.fieldTypes.get(fldType.udtType.toUpperCase());
                if (type == null)
                    result.addMsg(YamlMessages.error_TypeHasUnknownType, type.name, type.udtType);
                else if (!(type instanceof FieldEnum))
                    result.addMsg(YamlMessages.error_TypeNotAnEnumType, type.name, type.udtType);
                else
                    type.mergeTo(fldType);
            } else {
                FieldEnum fieldEnum = (FieldEnum) fldType;
                fieldEnum.enumTypeName = "docflow.enums." + NamesUtil.turnFirstLetterInUpperCase(fldType.name);
            }
        }
        // apply named structure type
        else if (fldType.type == BuiltInTypes.STRUCTURE || fldType.type == BuiltInTypes.SUBTABLE) {
            if (fldType.udtType != null) {
                Field type = docflowConfig.fieldTypes.get(fldType.udtType.toUpperCase());
                if (type == null)
                    result.addMsg(YamlMessages.error_TypeHasUnknownType, type.name, type.udtType);
                else if (!(type instanceof FieldStructure))
                    result.addMsg(YamlMessages.error_TypeNotAStructureType, type.name, type.udtType);
                else
                    type.deepCopy().mergeTo(fldType);
            }
        } else
            // TODO: Check that not an enum, structure or refers are merged into simple field
            parentType.mergeTo(fldType);
    }
}
