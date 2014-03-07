package code.docflow.javascript;

import code.docflow.DocflowConfig;
import code.docflow.action.ActionParams;
import code.docflow.model.Action;
import code.docflow.model.DocType;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.docflow.templateModel.*;
import code.jsonBinding.JsonBinding;
import code.jsonBinding.JsonTypeBinder;
import code.models.Document;
import code.users.CurrentUser;
import code.utils.BitArray;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import play.exceptions.JavaExecutionException;
import play.i18n.Messages;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class DocflowConfig2JavaScript {

    public static String build() {
        try {
            final TmplRoot model = TmplRoot.factory.get(CurrentUser.getInstance().getUserRoles());
            StringWriter sw = new StringWriter();
            JsonGenerator gen = (JsonBinding.factory != null ? JsonBinding.factory : new JsonFactory()).createGenerator(sw);
            gen.disable(JsonGenerator.Feature.QUOTE_FIELD_NAMES);
            gen.writeStartObject();

            // user
            gen.writeFieldName("user");
            final Document user = CurrentUser.getInstance().getUser();
            if (user != null)
                JsonTypeBinder.factory.get(user.getClass()).toJson(user, "profile", gen, null, null);
            else
                gen.writeNull();

            // documents
            gen.writeFieldName("docs");
            gen.writeStartObject();
            for (TmplDocument doc : model.getDocuments()) {

                gen.writeFieldName(doc.getName());
                gen.writeStartObject();

                final DocType docType = DocflowConfig.instance.documents.get(doc.getName().toUpperCase());
                final DocumentAccessActionsRights fullRights = RightsCalculator.instance.calculate(docType, CurrentUser.getInstance().getUserRoles());

                // way to create new document
                if (doc.hasAction(DocflowConfig.BuiltInActions.NEW_INSTANCE.getUpperCase()))
                    gen.writeBooleanField(DocflowConfig.BuiltInActions.NEW_INSTANCE.toString(), true);
                else if (fullRights.actionsMask.get(DocflowConfig.ImplicitActions.CREATE.index) || doc.getLinkedDocument()) {
                    gen.writeFieldName("$n");
                    final Template itemTemplate = docType.templates.get(DocflowConfig.BuiltInTemplates.FORM.getUpperCase());
                    docType.jsonBinder.toJson(docType.jsonBinder.sample, itemTemplate, gen, null,
                            JsonTypeBinder.EDIT_MODE | JsonTypeBinder.SAMPLE_OBJECT, null, null);
                }

                // actions localization
                gen.writeFieldName("actions");
                gen.writeStartObject();
                final BitArray.EnumTrueValues actionIterator = fullRights.actionsMask.getEnumTrueValues();
                int index;
                while ((index = actionIterator.next()) != -1) {
                    final Action action = docType.actionsArray[index];
                    gen.writeFieldName(action.name);
                    gen.writeStartObject();
                    gen.writeStringField("title", Messages.get(Messages.get(doc.getActionTitle(action.name))));
                    if (action.service)
                        gen.writeBooleanField("service", true);
                    if (action.update)
                        gen.writeBooleanField("update", true);
                    if (!Strings.isNullOrEmpty(action.confirm))
                        gen.writeStringField("confirm", Messages.get(action.confirm));
                    if (action.params != null && action.implicitAction != DocflowConfig.ImplicitActions.UPDATE) {
                        gen.writeFieldName("params");
                        try {
                            ActionParams actionParams = (ActionParams) action.paramsClass.newInstance();
                            JsonTypeBinder.factory.get(actionParams.getClass()).toJson(actionParams, gen, null);
                        } catch (InstantiationException e) {
                            throw new JavaExecutionException(e);
                        } catch (IllegalAccessException e) {
                            throw new JavaExecutionException(e);
                        }
                    }
                    gen.writeEndObject(); // action
                }
                gen.writeEndObject(); // actions

                // actions localization
                gen.writeFieldName("states");
                gen.writeStartObject();
                for (TmplState tmplState : doc.getStates()) {
                    gen.writeFieldName(tmplState.getName());
                    gen.writeStartObject();
                    gen.writeStringField("title", Messages.get(Messages.get(tmplState.getTitle())));
                    if (tmplState.getColor() != null)
                        gen.writeStringField("color", tmplState.getColor());
                    gen.writeEndObject();
                }
                gen.writeEndObject(); // states

                // templates definitions
                gen.writeFieldName("templates");
                gen.writeStartObject();
                for (TmplTemplate template : doc.getTemplates()) {
                    if (!template.getScreen())
                        continue;
                    gen.writeFieldName(template.getName());
                    gen.writeStartObject();
                    if (template.getTabs() != null) {
                        gen.writeFieldName("tabs");
                        gen.writeStartObject();
                        for (TmplTab tab : template.getTabs()) {
                            gen.writeFieldName(tab.getName());
                            gen.writeStartObject();
                            final TmplTemplate tabTemplate = tab.getTemplate();
                            final TmplDocument document = tabTemplate.getDocument();
                            gen.writeStringField("docType", document.getName());
                            gen.writeStringField("template", tab.getTemplate().getName());
                            final ImmutableMap<String,Object> options = tab.getOptions();
                            if (options != null) {
                                gen.writeFieldName("options");
                                gen.writeStartObject();
                                for (Map.Entry<String, Object> entry : options.entrySet()) {
                                    final Object value = entry.getValue();
                                    if (value == null)
                                        gen.writeNullField(entry.getKey());
                                    else if (value instanceof Boolean)
                                        gen.writeBooleanField(entry.getKey(), (Boolean) value);
                                    else if (value instanceof Long)
                                        gen.writeNumberField(entry.getKey(), (Long) value);
                                    else if (value instanceof Double)
                                        gen.writeNumberField(entry.getKey(), (Double) value);
                                    else
                                        gen.writeStringField(entry.getKey(), value.toString());
                                }
                                gen.writeEndObject();
                            }
                            gen.writeEndObject(); // tab name
                        }
                        gen.writeEndObject(); // tabs
                    }
                    gen.writeEndObject(); // template
                }

                gen.writeEndObject(); // templates

                gen.writeEndObject(); // docType
            }
            gen.writeEndObject(); // documents

            gen.writeFieldName("messages");
            gen.writeStartObject();
            gen.writeStringField("actionProgress", Messages.get("message.actionProgress"));
            gen.writeStringField("actionDone", Messages.get("message.actionDone"));
            gen.writeStringField("actionFailed", Messages.get("message.actionFailed"));
            gen.writeEndObject(); // messages

            gen.writeEndObject();
            gen.flush();
            return "window.docflowConfig=" + sw.toString();
        } catch (IOException e) {
            throw new JavaExecutionException(e);
        }
    }
}
