package code.users;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.DocflowConfig;
import code.docflow.model.DocType;
import code.models.Document;

public class SystemUser extends Document {

    /**
     * Use CurrentUser.SYSTEM_USER instead.
     */
    SystemUser() {}

    public String name = DocflowConfig.BuiltInRoles.SYSTEM.toString();

    @Override
    public DocType _docType() {
        return null;
    }

    @Override
    public String _fullId() {
        return "BuiltIn:SystemUser";
    }
}
