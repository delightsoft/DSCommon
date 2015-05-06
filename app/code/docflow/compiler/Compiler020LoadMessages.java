package code.docflow.compiler;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.controlflow.Result;
import code.docflow.DocflowConfig;
import code.docflow.model.Message;
import code.docflow.yaml.YamlMessages;
import code.docflow.yaml.YamlParser;
import code.docflow.yaml.builders.DocumentBuilder;
import code.docflow.yaml.builders.ItemBuilder;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.scanner.ScannerException;
import play.Logger;
import play.exceptions.UnexpectedException;
import play.vfs.VirtualFile;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class Compiler020LoadMessages {
    public static void doJob(DocflowConfig docflowConfig, Result result) {
        final Result localResult = new Result();

        ItemBuilder itemBuilder = null;
        try {
            itemBuilder = ItemBuilder.factory.get(docflowConfig.getClass().getField("messages"));
        } catch (NoSuchFieldException e) { // from getClass()
            throw new UnexpectedException(e);
        }

        // reverse path iteration
        for (int i = docflowConfig.modules.length - 1; i >= 0; i--) {
            docflowConfig.currentModule = docflowConfig.modules[i];
            final VirtualFile file = docflowConfig.currentModule.root.child(DocflowConfig.PATH_DOCFLOW);
            if (!file.exists())
                continue;

            final File[] msgFileList = file.getRealFile().listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.toLowerCase().endsWith(DocflowConfig.MESSAGES_FILE_SUFFIX);
                }
            });

            for (int j = 0; j < msgFileList.length; j++) {
                File msgFile = msgFileList[j];

                String fullFilename = msgFile.getAbsolutePath();
                final String name = msgFile.getName();
                final String fileKey = name.substring(0, name.length() - DocflowConfig.MESSAGES_FILE_SUFFIX.length()).toUpperCase() + ".";

                InputStreamReader inputStreamReader = null;
                try {
                    inputStreamReader = new InputStreamReader(new FileInputStream(msgFile));
                } catch (FileNotFoundException e) {
                    throw new UnexpectedException(e);
                }
                final YamlParser yamlParser = new YamlParser(new Yaml().parse(inputStreamReader), msgFile.getAbsolutePath());
                try {
                    final DocumentBuilder documentBuilder = new DocumentBuilder(itemBuilder);
                    final LinkedHashMap<String, Message> map = (LinkedHashMap<String, Message>) documentBuilder.build(yamlParser, localResult);
                    if (map == null)
                        continue;
                    if (docflowConfig.messages == null) {
                        docflowConfig.messages = new LinkedHashMap<String, Message>();
                        docflowConfig.messagesByFiles = new LinkedHashMap<String, LinkedHashMap<String, Message>>();
                    }

                    for (Map.Entry<String, Message> entry : map.entrySet())
                        docflowConfig.messages.put(fileKey + entry.getKey(), entry.getValue());

                    docflowConfig.messagesByFiles.put(fullFilename, map);

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

                if (localResult.isNotOk())
                    result.appendFileScope(localResult, fullFilename);
                else
                    result.addMsg(YamlMessages.debug_FileLoadedSuccessfully, fullFilename);
            }
        }
    }
}
