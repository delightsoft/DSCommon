package controllers.ngCodeGen;

import code.docflow.codegen.DocflowCodeGenerator;
import code.docflow.codegen.DocflowLocalizationGenerator;
import code.docflow.controlflow.Result;
import code.docflow.DocflowConfig;
import code.docflow.model.*;
import code.docflow.users.CurrentUser;
import play.mvc.Controller;

import java.io.IOException;
import java.util.concurrent.Callable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class DocflowGenerator extends Controller {

    public static void index(String appPath, String targetDb) {
        Result result = DocflowCodeGenerator.loadDocflowConfiguration(appPath);
        final DocflowConfig docflow = DocflowConfig.instance;
        final boolean codegenExternal = DocflowCodeGenerator.isCodegenExternal();
        render(docflow, appPath, targetDb, codegenExternal, result);
    }

    public static void setAppPath(String appPath, String targetDb) {
        index(appPath, targetDb);
    }

    public static void generateModels(final String appPath, final String targetDb) throws IOException {

        CurrentUser.systemUserScope(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                DocflowCodeGenerator.generate(appPath, targetDb);
                DocflowLocalizationGenerator.generate();
                return null;
            }
        });

        index(appPath, targetDb);
    }

    public static void entity(String entityName, String appPath, String targetDb) {
        DocflowCodeGenerator.loadDocflowConfiguration(appPath);
        Entity entity = findEntity(entityName);
        request.format = "txt";
        DocflowCodeGenerator.generateEntityCode(entity, targetDb, response.out);
    }

    private static Entity findEntity(String entityName) {
        final DocflowConfig docflow = DocflowConfig.instance;
        Entity entity = null;
        for (DocType docType : docflow.documents.values())
            for (int i = 0; i < docType.entities.size(); i++) {
                Entity e = docType.entities.get(i);
                if (e.name.equals(entityName)) {
                    entity = e;
                    break;
                }
            }
        if (entity == null)
            notFound(String.format("Entity '%s'", entityName));
        return entity;
    }
}
