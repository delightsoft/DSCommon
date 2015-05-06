package controllers.ngCodeGen;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.DocflowConfig;
import code.docflow.codegen.DocflowCodeGenerator;
import code.docflow.compiler.enums.BuiltInTypes;
import code.docflow.controlflow.Result;
import code.docflow.model.DocType;
import code.docflow.model.Field;
import code.docflow.model.FieldStructure;
import code.docflow.utils.NamesUtil;
import play.mvc.Controller;

import java.io.BufferedWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.util.Iterator;
import java.util.Map;

/**
 * It's one time solution, that generates MS SQL script that changes boolean fields from tinyint to bit.
 * This solves mapping conflict that appeared during transfer from hibernate 3.6 to 4.2.
 */
public class MSSQLScript extends Controller {

    public static void index(String appPath) {
        Result result = DocflowCodeGenerator.loadDocflowConfiguration(appPath);
        final PrintStream ps = new PrintStream(response.out);
        try {
            for (DocType docType : DocflowConfig.instance.documents.values()) {
                final String tableName = docType.entities.get(0).tableName;
                for (Field fld : docType.allFields) {
                    if (fld.structure != null) continue;
                    if (fld.type == BuiltInTypes.STRUCTURE)
                        for (Field sfld : ((FieldStructure) fld).fields.values())
                            if (sfld.type == BuiltInTypes.BOOL)
                                changeOneFieldType(ps, tableName, sfld, fld.name + "_" + NamesUtil.wordsToUnderscoreSeparated(sfld.name));
                    if (fld.type == BuiltInTypes.BOOL)
                        changeOneFieldType(ps, tableName, fld, fld.name);
                }
            }
        } finally {
            ps.flush();
            ps.close();
        }
    }

    private static void changeOneFieldType(PrintStream ps, String tableName, Field fld, String fieldName) {
        ps.println();
        ps.println(String.format("ALTER TABLE %s ADD _bool bit%s;", tableName, fld.nullable ? "" : " NOT NULL DEFAULT 0"));
        ps.println(String.format("UPDATE %s SET _bool = %s;", tableName, fieldName));
        ps.println(String.format("ALTER TABLE %s DROP COLUMN %s;", tableName, fieldName));
        ps.println(String.format("EXEC sp_rename '%s._bool', '%s', 'COLUMN';", tableName, fieldName));
    }


}
