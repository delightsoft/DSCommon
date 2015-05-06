package code.docflow.compiler;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.controlflow.Result;
import code.docflow.DocflowConfig;
import code.docflow.model.DocType;
import code.docflow.model.DocflowModule;
import code.docflow.model.RootElement;
import code.docflow.yaml.YamlMessages;
import code.docflow.yaml.YamlParser;
import code.docflow.yaml.builders.DocumentBuilder;
import code.docflow.yaml.builders.ItemBuilder;
import code.docflow.yaml.compositeKeyHandlers.RootElementCompositeKeyHandler;
import code.docflow.utils.FileUtil;
import com.google.common.base.Strings;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.parser.ParserException;
import org.yaml.snakeyaml.scanner.ScannerException;
import play.vfs.VirtualFile;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

public class Compiler040LoadDocuments {
    public static void doJob(DocflowConfig docflowConfig, Result result) {
        List<String> loadedFilesInLowerCase = new ArrayList<String>();
        for (DocflowModule module : docflowConfig.modules) {
            docflowConfig.currentModule = module;
            final VirtualFile dir = module.root.child(DocflowConfig.PATH_DOCUMENTS);
            if (dir != null && dir.exists()) {
                final List<VirtualFile> list = dir.list();
                for (int i = 0; i < list.size(); i++) {
                    VirtualFile file = list.get(i);
                    if (file.getRealFile().isHidden() || skipDuplicatesFromDifferentPaths(loadedFilesInLowerCase, file, result))
                        continue;
                    DocType doc = loadOneYamlFile(docflowConfig, file, new DocType[0], result);
                    if (doc == null) // it's error, but keep checking other files
                        continue;
                    doc.sourcePath = module.root;
                    docflowConfig.documents.put(doc.name.toUpperCase(), doc);
                }
            }
        }
    }

    // Note: It's public, cause it used directly in test code
    public static <T extends RootElement> T loadOneYamlFile(DocflowConfig docflowConfig, VirtualFile file, T[] type, Result result) {
        final Yaml yaml = new Yaml();
        final Result localResult = new Result();
        final String source = file.contentAsString();
        final YamlParser yamlParser = new YamlParser(
                yaml.parse(new StringReader(source)),
                file.getRealFile().getAbsolutePath());
        try {
            final String elementName = FileUtil.filename(file);
            final RootElement root;
            try {
                root = (RootElement) new DocumentBuilder(ItemBuilder.factory.get(
                        RootElement.class)).build(yamlParser, localResult);
            } catch (ScannerException e) {
                result.addMsg(YamlMessages.error_InvalidYamlFormat, yamlParser.filename,
                        e.getProblem(), e.getProblemMark().getLine(), e.getProblemMark().getColumn());
                return null;
            } catch (ParserException e) {
                result.addMsg(YamlMessages.error_UnexpectedStructureYaml, yamlParser.filename,
                        e.getProblemMark().get_snippet(), e.getProblemMark().getLine(), e.getProblemMark().getColumn());
                return null;
            }
            if (!localResult.isNotOk()) {
                final Class<?> componentType = type.getClass().getComponentType();
                if (root == null ||
                        !(componentType.isAssignableFrom(root.getClass())) ||
                        !elementName.toLowerCase().equals(Strings.nullToEmpty(root.name).toLowerCase())) {
                    RootElement re = null;
                    try {
                        re = (RootElement) componentType.newInstance();
                        re.name = elementName;
                    } catch (InstantiationException e) {
                        checkState(false);
                    } catch (IllegalAccessException e) {
                        checkState(false);
                    }
                    localResult.addMsg(YamlMessages.error_FileExpectedToBegingWith, RootElementCompositeKeyHandler.expectedBegging(re));
                } else {
                    root.name = elementName;
                    root.module = docflowConfig.currentModule;
                    root.sourceFile = file.relativePath();
                    root.source = source;
                }
            }
            if (localResult.isNotOk()) {
                result.appendFileScope(localResult, elementName);
                return null;
            }

            localResult.addMsg(YamlMessages.debug_FileLoadedSuccessfully, elementName);
            localResult.toLogger();

            return (T) root;
        } catch (ScannerException e) {
            localResult.addMsg(YamlMessages.error_InvalidYamlFormat, yamlParser.filename,
                    e.getProblem(), e.getProblemMark().getLine(), e.getProblemMark().getColumn());
            return null;
        }
    }

    static boolean skipDuplicatesFromDifferentPaths(List<String> loadedDocInLowerCase, VirtualFile file, Result result) {
        String fileName = file.getName();
        final int ext = fileName.lastIndexOf('.');
        if (ext > 0)
            fileName = fileName.substring(0, ext);
        fileName = fileName.toLowerCase();
        if (loadedDocInLowerCase.contains(fileName)) {
            result.addMsg(YamlMessages.error_FileSkipped, file.getRealFile().getAbsolutePath());
            return true;
        }
        loadedDocInLowerCase.add(fileName);
        return false;
    }
}
