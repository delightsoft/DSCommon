package code.docflow.compiler;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.controlflow.Result;
import code.docflow.DocflowConfig;
import code.docflow.model.DocflowModule;
import code.docflow.model.DocflowModuleYamlConfig;
import code.docflow.yaml.YamlMessages;
import code.docflow.yaml.YamlParser;
import code.docflow.yaml.builders.DocumentBuilder;
import code.docflow.yaml.builders.ItemBuilder;
import code.docflow.utils.EnumUtil;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.scanner.ScannerException;
import play.Play;
import play.exceptions.UnexpectedException;
import play.vfs.VirtualFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * Note: Every path can have it's own version of config.
 */

public class Compiler010LoadConfig {

    public static void doJob(DocflowConfig docflowConfig, Result result) {
        final Result localResult = new Result();

        ItemBuilder itemBuilder = null;
        try {
            itemBuilder = ItemBuilder.factory.get(docflowConfig.getClass().getField("sampleConfigField"));
        } catch (NoSuchFieldException e) {
            throw new UnexpectedException(e);
        }

        final ArrayList<DocflowModule> modules = new ArrayList<DocflowModule>();

        for (VirtualFile moduleRoot : DocflowConfig.appPath != null ? DocflowConfig.appPath : Play.javaPath) {
            final VirtualFile configFile = moduleRoot.child(DocflowConfig.PATH_CONFIG);
            final DocflowModule module = new DocflowModule();
            module.root = moduleRoot;
            module.schema = DocflowModule.Schema.V1;
            modules.add(module);

            if (!configFile.exists())
                continue;

            final InputStreamReader inputStreamReader = new InputStreamReader(configFile.inputstream());
            final YamlParser yamlParser = new YamlParser(new Yaml().parse(inputStreamReader), configFile.getRealFile().getAbsolutePath());
            DocflowModuleYamlConfig config = null;
            try {
                final DocumentBuilder documentBuilder = new DocumentBuilder(itemBuilder);
                config = (DocflowModuleYamlConfig) documentBuilder.build(yamlParser, localResult);
            } catch (ScannerException e) {
                localResult.addMsg(YamlMessages.error_InvalidYamlFormat, yamlParser.filename,
                        e.getProblem(), e.getProblemMark().getLine(), e.getProblemMark().getColumn());
                continue;
            } finally {
                try {
                    inputStreamReader.close();
                } catch (IOException e) {
                    throw new UnexpectedException(e);
                }
            }

            if (EnumUtil.isEqual(DocflowModule.Schema.V1, config.schema))
                module.schema = DocflowModule.Schema.V1;
            else if (EnumUtil.isEqual(DocflowModule.Schema.V2, config.schema))
                module.schema = DocflowModule.Schema.V2;
            else {
                localResult.addMsg(YamlMessages.error_ConfigWrongSchema, config.schema);
                continue;
            }

            if (config.dscommon)
                docflowConfig.MODULE_DSCOMMON = module;
        }

        docflowConfig.modules = modules.toArray(new DocflowModule[0]);
    }
}
