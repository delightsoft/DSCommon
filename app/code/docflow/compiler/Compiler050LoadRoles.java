package code.docflow.compiler;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.controlflow.Result;
import code.docflow.DocflowConfig;
import code.docflow.model.DocflowModule;
import code.docflow.model.Role;
import code.docflow.yaml.YamlMessages;
import play.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

public class Compiler050LoadRoles {
    public static void doJob(DocflowConfig docflowConfig, Result result) {
        List<String> loadedFilesInLowerCase = new ArrayList<String>();
        for (DocflowModule module : docflowConfig.modules) {
            docflowConfig.currentModule = module;
            final VirtualFile dir = module.root.child(DocflowConfig.PATH_ROLES);
            if (dir != null && dir.exists()) {
                final List<VirtualFile> list = dir.list();
                for (int i = 0; i < list.size(); i++) {
                    VirtualFile file = list.get(i);
                    if (file.getRealFile().isHidden() || Compiler040LoadDocuments.skipDuplicatesFromDifferentPaths(loadedFilesInLowerCase, file, result))
                        continue;
                    Role role = Compiler040LoadDocuments.loadOneYamlFile(docflowConfig, file, new Role[0], result);
                    if (result.isError())
                        return;
                    checkState(role != null);
                    role.sourcePath = module.root;
                    docflowConfig.roles.put(role.name.toUpperCase(), role);
                }
            }
        }
        if (loadedFilesInLowerCase.size() == 0)
            result.addMsg(YamlMessages.error_NoRolesDefinitions);
    }
}
