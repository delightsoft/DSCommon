package code;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.controlflow.Result;
import code.docflow.yaml.YamlMessages;
import org.yaml.snakeyaml.Yaml;
import play.exceptions.PlayException;

public class DocflowConfigException extends PlayException {

    private Result result;

    public DocflowConfigException(Result result) {
        this.result = result;
    }

    @Override
    public String getErrorTitle() {
        return YamlMessages.error_FailedToLoadDocflowConfig.format.toString();
    }

    @Override
    public String getErrorDescription() {
        return result.toHtml();
    }

    @Override
    public String getMessage() {
        return getErrorTitle();
    }
}
