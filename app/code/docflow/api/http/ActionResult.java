package code.docflow.api.http;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.controlflow.Result;
import code.docflow.docs.Document;
import code.docflow.jsonBinding.JsonBinding;
import code.docflow.jsonBinding.JsonTypeBinder;
import code.docflow.jsonBinding.binders.type.TypeBinder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.base.Strings;
import models.DocflowFile;
import play.Logger;
import play.exceptions.UnexpectedException;
import play.mvc.Http;

import java.io.IOException;
import java.io.StringWriter;

public class ActionResult extends play.mvc.results.Result {
    String file;
    Result result;
    String template;
    String resultTemplate;

    String docJson;
    String resultJson;

    public ActionResult(Document resultDoc, Object resultData, String template, String resultTemplate, Result result) {
        this.result = result;
        this.template = template;
        this.resultTemplate = resultTemplate;

        if (resultDoc != null)
            try {
                final StringWriter sw = new StringWriter();
                final JsonGenerator generator = JsonBinding.factory.createGenerator(sw);
                generator.writeTree(JsonTypeBinder.factory.get(resultDoc.getClass()).toJson(resultDoc, template, null, null));
                generator.flush();
                generator.close();
                docJson = sw.toString();
            } catch (IOException e) {
                throw new UnexpectedException(e);
            }

        if (resultData != null) {
            if (resultData instanceof ResultFile)
                file = "\"" + ((ResultFile) resultData)._fullId() + "\"";
            else
                try {
                    final StringWriter sw = new StringWriter();
                    final JsonGenerator generator = JsonBinding.factory.createGenerator(sw);
                    final String templateName = Strings.isNullOrEmpty(resultTemplate) ? template : resultTemplate;
                    generator.writeTree(JsonBinding.toJsonNode(resultData, templateName));
                    generator.flush();
                    generator.close();
                    resultJson = sw.toString();
                } catch (IOException e) {
                    throw new UnexpectedException(e);
                }
        }
    }

    @Override
    public void apply(Http.Request request, Http.Response response) {

        response.contentType = "application/json";
        response.status = 200; // business logic errors are not considered as HTTP protocol failures

        try {
            final JsonGenerator generator = JsonBinding.factory.createGenerator(response.out);
            try {
                generator.writeStartObject();

                generator.writeStringField("code", result.getCode().name);

                if (result.anyMessage())
                    // TODO: Fix localization here
                    generator.writeStringField("message", result.toHtml());
                else
                    generator.writeNullField("message");

                if (docJson != null) {
                    generator.writeFieldName("doc");
                    generator.writeRaw(":");
                    generator.writeRaw(docJson.toString());
                }

                if (resultJson != null) {
                    // Note: Jackson do not changes stream state to next field after writeRaw()
                    generator.writeRaw(",\"result\":");
                    generator.writeRaw(resultJson.toString());
                }

                if (file != null) {
                    generator.writeRaw(",\"file\":");
                    generator.writeRaw(file);
                }

                generator.writeEndObject();
                generator.flush();

            } catch (RuntimeException e) {
                Logger.error("Result rendering: Exception: %s", e);
                throw e;
            } finally {
                generator.close();
            }
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }
}
