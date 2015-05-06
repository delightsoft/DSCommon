package code.docflow.compiler;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.controlflow.Result;
import code.docflow.DocflowConfig;
import code.docflow.model.Field;
import code.docflow.yaml.YamlMessages;
import code.docflow.yaml.YamlParser;
import code.docflow.yaml.builders.DocumentBuilder;
import code.docflow.yaml.builders.ItemBuilder;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.scanner.ScannerException;
import play.Logger;
import play.vfs.VirtualFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;

public class Compiler030LoadFieldTypes {
    public static void doJob(DocflowConfig docflowConfig, Result result) {
        final Result localResult = new Result();

        // reverse path iteration
        for (int i = docflowConfig.modules.length - 1; i >= 0; i--) {
            docflowConfig.currentModule = docflowConfig.modules[i];
            final VirtualFile file = docflowConfig.currentModule.root.child(DocflowConfig.PATH_FIELD_TYPES);
            if (!file.exists())
                continue;

            String filename = null;
            try {
                filename = file.getRealFile().getCanonicalPath();
            } catch (IOException e) {
                Logger.error(e, "Unexpected");
            }

            try {
                final ItemBuilder itemBuilder = ItemBuilder.factory.get(docflowConfig.getClass().getField("fieldTypes"));
                final InputStreamReader inputStreamReader = new InputStreamReader(file.inputstream());
                final YamlParser yamlParser = new YamlParser(new Yaml().parse(inputStreamReader), file.getRealFile().getAbsolutePath());
                try {
                    final DocumentBuilder documentBuilder = new DocumentBuilder(itemBuilder);
                    final LinkedHashMap<String, Field> map = (LinkedHashMap<String, Field>) documentBuilder.build(yamlParser, localResult);
                    if (map != null) // merge.  map is null, when file is empty
                        for (Map.Entry<String, Field> entry : map.entrySet()) {
                            Field fld = entry.getValue();
                            fld.sourcePath = docflowConfig.currentModule.root;
                            docflowConfig.fieldTypes.put(entry.getKey(), fld);
                        }
                } catch (ScannerException e) {
                    localResult.addMsg(YamlMessages.error_InvalidYamlFormat, yamlParser.filename,
                            e.getProblem(), e.getProblemMark().getLine(), e.getProblemMark().getColumn());
                } finally {
                    try {
                        inputStreamReader.close();
                    } catch (IOException e) {
                        Logger.error(e, "Failed to close file.");
                    }
                }
            } catch (NoSuchFieldException e) { // from getClass()
                Logger.error(e, "Unexpected");
            }

            if (localResult.isNotOk())
                result.appendFileScope(localResult, filename);
            else
                result.addMsg(YamlMessages.debug_FileLoadedSuccessfully, filename);
        }

        if (docflowConfig.fieldTypes == null) {
            result.addMsg(YamlMessages.error_FileNotFound, DocflowConfig.PATH_FIELD_TYPES);
            return;
        }
    }
}
