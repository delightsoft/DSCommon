package code.docflow.templateModel;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.model.DocType;
import code.docflow.model.HtmlElement;
import code.docflow.model.Template;
import code.docflow.templateModel.TmplTemplate;
import groovy.lang.Closure;
import play.Logger;
import play.Play;
import play.exceptions.TagInternalException;
import play.exceptions.TemplateExecutionException;
import play.exceptions.UnexpectedException;
import play.templates.FastTags;
import play.templates.GroovyTemplate;
import play.templates.JavaExtensions;
import play.templates.TagContext;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DocflowTags extends FastTags {

    /**
     * Renders HTML structure if template.html is defined, otherwise execute body.
     */
    public static void _docflowHtml(Map<?, ?> args, Closure body, PrintWriter out, GroovyTemplate.ExecutableTemplate template, int fromLine) {
        Object arg = args.get("arg");
        if (arg == null || !(arg instanceof TmplTemplate))
            throw new TemplateExecutionException(template.template, fromLine, "Specify a docflow template (TmplTemplate object)", new TagInternalException("Specify a docflow template (TmplTemplate object)"));
        TmplTemplate tmpl = (TmplTemplate) arg;

        Object argNamespace = args.get("namespace");
        String namespace = (argNamespace != null) ? argNamespace.toString() : "ngApp.form";

        LinkedHashMap<Integer, HtmlElement> html = tmpl.srcTemplate.html;
        if (html == null) {
            out.print(JavaExtensions.toString(body));
            return;
        }

        HtmlElement first = html.values().iterator().next();
        String path = tmpl.srcTemplate.document.sourceFile + "(" + first.line + ").html";

        // TODO: Cache template by it's path
        // TODO: When tag is missing does not refer yaml - fix it

        String groovySource = generateTemplate(tmpl, first, html, namespace);

//        out.print(groovySource);

        final DocType srcDocType = tmpl.srcTemplate.document;
        final YamlTemplate yamlTemplate = new YamlTemplate(path, srcDocType.source, groovySource);
        Map<String, Object> newArgs = new HashMap<String, Object>();
        newArgs.putAll(template.getBinding().getVariables());
        newArgs.put("_isInclude", true);
        newArgs.put("_template", tmpl);
        for (Map.Entry<?, ?> pair : args.entrySet())
            newArgs.put("_" + pair.getKey(), pair.getValue());
        TagContext.enterTag(path);
        yamlTemplate.render(newArgs);
        TagContext.exitTag();

    }

    public static class YamlTemplate extends GroovyTemplate {
        public YamlTemplate(String name, String yamlSource, String groovyCode) {
            super(name, yamlSource);
            super.compiledSource = groovyCode;
        }
    }

    private static String generateTemplate(TmplTemplate tmpl, HtmlElement first, LinkedHashMap<Integer, HtmlElement> html, String namespace) {
        StringBuffer sb = new StringBuffer();

        sb.append("class DocflowYaml_" + tmpl.document.name + "_" + first.line + " extends play.templates.GroovyTemplate.ExecutableTemplate {\n");
        sb.append("public Object run() { use(play.templates.JavaExtensions) {\n");
        final List<String> extentions = Play.pluginCollection.addTemplateExtensions();
        for (String n : extentions) {
            sb.append("use(_('" + n + "')) {");
        }

        generateClosure(sb, tmpl, html, namespace, "_p");

        for (String n : extentions) {
            sb.append("}");
        }
        sb.append("}}}");

        return sb.toString();
    }

    private static void generateClosure(StringBuffer sb, TmplTemplate tmpl, LinkedHashMap<Integer, HtmlElement> html, String namespace, String shift) {
        // TODO: Support html alignment
        for (HtmlElement htmlElement : html.values()) {
            sb.append("if (_d) out.print(\"${" + shift + "}<!-- html: " + tmpl.srcTemplate.document.sourceFile + " (" + htmlElement.line + ") -->${_n}\");");
            switch (htmlElement.type) {
                case FIELD:
                    if (tmpl.getFieldByName(htmlElement.value) != null) // fields might be missing due to rights restrictions
                        sb.append("_template.getFieldByName('" + htmlElement.value + "').smartTag('" + namespace + "', this, !!_debug, [classes: '" + htmlElement.classes + "', lbl: true, val: true, p: " + shift + ", s: _s, n: _n, d: _d]);\n");
                    break;
                case DIV:
                    sb.append("if (_d) out.print(" + shift  +");");
                    sb.append("out.print(\"<div class='" + htmlElement.classes + "'>\");\n");
                    sb.append("if (_d) out.print(_n);");
                    if (htmlElement.innerHtml != null)
                        generateClosure(sb, tmpl, htmlElement.innerHtml, namespace, shift + " + _s");
                    sb.append("if (_d) out.print(" + shift  +");");
                    sb.append("out.print(\"</div>\");\n");
                    sb.append("if (_d) out.print(_n);");
                    break;
                case TAG:
                    sb.append("invokeTag(" + htmlElement.line + ", '" + htmlElement.value + "', [");
                    if (htmlElement.args != null)
                        sb.append(htmlElement.args + ", ");
                    sb.append("template: _template, debug: !!_debug, p: " + shift + ", s: _s, n: _n, d: _d],{\n");
                    if (htmlElement.innerHtml != null)
                        generateClosure(sb, tmpl, htmlElement.innerHtml, namespace, shift + " + _s");
                    sb.append("});\n");
                    break;
                default:
                    throw new UnexpectedException(String.format("%s from yaml.html support is not implemented yet", htmlElement.type.name()));
            }
        }
    }
}
