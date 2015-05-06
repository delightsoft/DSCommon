package code.docflow.compiler;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.DocflowConfig;
import code.docflow.api.http.ActionResult;
import code.docflow.collections.Item;
import code.docflow.compiler.enums.BuiltInActions;
import code.docflow.compiler.enums.BuiltInTypes;
import code.docflow.compiler.enums.CrudActions;
import code.docflow.compiler.enums.TaskPreconditions;
import code.docflow.compiler.preconditions.PreconditionFailedResult;
import code.docflow.controlflow.Result;
import code.docflow.docs.Document;
import code.docflow.jsonBinding.JsonTypeBinder;
import code.docflow.model.*;
import code.docflow.queries.DocListBuilder;
import code.docflow.queries.FiltersEnum;
import code.docflow.queries.QueryParams;
import code.docflow.queries.SortOrdersEnum;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.utils.BitArray;
import code.docflow.utils.EnumUtil;
import code.docflow.utils.NamesUtil;
import code.docflow.yaml.YamlMessages;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.Play;
import play.mvc.Http;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.TreeMap;

/**
 * 1. Populates Document.jsonBinding for all documents.
 * 2. Links Action to java implementations.
 */
public class Compiler410LinkToCode {

    public static final String ENUMS_PACKAGE = "docflow.enums.";

    public static void doJob(DocflowConfig docflowConfig, Result result) {

        for (Field fld : docflowConfig.fieldTypes.values()) {
            if (fld.type != BuiltInTypes.ENUM)
                continue;
            reflectEnum((FieldEnum) fld, result);
        }

        for (int i = 0; i < docflowConfig.documentsArray.length; i++) {
            DocType docType = docflowConfig.documentsArray[i];
            if (docType.udt)
                continue;
            final String className = docType.getClassName();
            final Class type = Play.classloader.getClassIgnoreCase(className);
            if (type == null) {
                result.addMsg(YamlMessages.error_DocumentNoCorrespondedModelClass, docType.name, className);
                continue;
            }
            docType.jsonBinder = JsonTypeBinder.factory.get(type);
            if (!docType.report && docType.jsonBinder.recordAccessor == null) {
                result.addMsg(YamlMessages.error_DocumentCorrespondedClassMustBeChildOfEntityBase, docType.name, className, Document.class.getName());
                continue;
            }

            docType.jsonBinder.linkDocumentFieldsToFieldsAccessors(result);

            linkTasks(docType, result);

            linkQueriesAndCalculateMethods(docType, result);

            linkActionsAndPreconditions(docType, result);

            linkFilters(docType);

            linkSortOrders(docType);

            linkEnums(docflowConfig, docType, result);

            linkRelations(docType, result);
        }
    }

    private static void linkActionsAndPreconditions(DocType docType, Result result) {
        final String className = "docflow.actions.Actions" + docType.name;

        for (Action action : docType.actionsArray)
            if (action.params != null) {
                action.paramsClass = Play.classloader.getClassIgnoreCase(action.getFullParamsClassName());
                if (action.paramsClass == null) {
                    result.addMsg(YamlMessages.error_DocumentActionFailedToFindClass, docType.name, action.name, action.getFullParamsClassName());
                    continue;
                }
            }

        final Class actionsType = Play.classloader.getClassIgnoreCase(className);
        if (actionsType == null) {
            result.addMsg(YamlMessages.error_DocumentNoCorrespondedActionsClass, docType.name, className);
            return;
        }

        final Method[] methods = actionsType.getMethods();
        for (Method method : methods) {
            if (method.getDeclaringClass() != actionsType)
                continue;
            final String methodName = method.getName().toUpperCase();
            final Class<?>[] params = method.getParameterTypes();
            final Class<?> returnType = method.getReturnType();

            if (EnumUtil.isEqual(BuiltInActions.PRECREATE, methodName)) {
                boolean ok = params.length == 2;
                if (ok) {
                    final String param0Class = params[0].getCanonicalName();
                    final String returnClass = returnType.getCanonicalName();
                    ok &= param0Class != null && param0Class.equals(docType.getClassName()); // first parameter EntityType
                    ok &= params[1] == Result.class; // 2nd parameter Result
                    ok &= returnClass != null && returnClass.equals(docType.getClassName()); // return void or EntityType
                }
                if (!ok) {
                    result.addMsg(YamlMessages.error_DocumentActionExpectedToHaveSignature, docType.name, actionsType.getName() + "." + method.getName(),
                            "public static " + docType.name + " " + BuiltInActions.PRECREATE.toString() + "(" + docType.name + ", Result result)");
                    continue;
                }
                docType.preCreateMethod = method;
                continue;
            }

            if (EnumUtil.isEqual(BuiltInActions.PREUPDATE, methodName)) {
                final Action updateAction = docType.actions.get(CrudActions.UPDATE.name());
                if (updateAction.params == null) {
                    result.addMsg(YamlMessages.error_DocumentPreUpdateRequiresParametersOnUpdate, docType.name, actionsType.getName() + "." + method.getName());
                    continue;
                }

                boolean ok = params.length == 1;
                if (ok) {
                    final String param0Class = params[0].getCanonicalName();
                    ok &= param0Class != null && param0Class.equals(docType.getClassName()); // first parameter EntityType
                    ok &= returnType == updateAction.paramsClass; // return void or EntityType
                }
                if (!ok) {
                    result.addMsg(YamlMessages.error_DocumentActionExpectedToHaveSignature, docType.name, actionsType.getName() + "." + method.getName(),
                            "public static " + docType.name + "." + updateAction.paramsClassName + " " + BuiltInActions.PREUPDATE.toString() + "(" + docType.name + ")");
                    continue;
                }
                docType.preUpdateMethod = method;
                continue;
            }

            Precondition precondition = docType.preconditions == null ? null : docType.preconditions.get(methodName);
            final Action action = docType.actions.get(methodName);

            if (precondition != null) {
                boolean ok = params.length == 1;
                if (ok) {
                    final int mod = method.getModifiers();
                    final String param0Class = params[0].getCanonicalName();
                    ok &= Modifier.isPublic(mod) && Modifier.isStatic(mod);
                    ok &= param0Class.equals(docType.getClassName());
                    ok &= boolean.class == returnType; // return void or EntityType
                }
                if (!ok) {
                    result.addMsg(YamlMessages.error_DocumentPreconditionExpectedToHaveSignature, docType.name,
                            actionsType.getName() + "." + method.getName(),
                            preconditionMethodSignature(docType, precondition.name));
                    continue;
                }
                for (Transition transition : precondition.transitions)
                    transition.preconditionEvaluator = method;

            } else if (action != null) {
                // parameters are: [doc,] [params,] result
                final int paramsCount = (action.service ? 0 : 1) + (action.params != null ? 1 : 0) + 1;
                boolean ok = params.length == paramsCount;
                if (ok) {
                    final int mod = method.getModifiers();
                    final String param0Class = params[0].getCanonicalName();
                    ok &= Modifier.isPublic(mod) && Modifier.isStatic(mod);
                    ok &= action.service || param0Class != null && param0Class.equals(docType.getClassName()); // first parameter EntityType, if this is not a service
                    ok &= !(action.params != null) || params[action.service ? 0 : 1].getName().equals(action.getFullParamsClassName()); // parameters comes after EntityType,  if there are some
                    ok &= params[paramsCount - 1] == Result.class; // last parameter Result
                    ok &= returnType == void.class || Object.class.isAssignableFrom(returnType); // return void or EntityType
                }
                if (!ok) {
                    result.addMsg(YamlMessages.error_DocumentActionExpectedToHaveSignature, docType.name, actionsType.getName() + "." + method.getName(), actionMethodSignature(docType, action));
                    continue;
                }
                action.actionMethod = method;
            } else {
                result.addMsg(YamlMessages.error_DocumentMethodNoSuchActionOrPreconditionInModel, docType.name, actionsType.getName() + "." + method.getName());
                continue;
            }
        }

        for (Action action : docType.actionsArray)
            if (action.service && action.actionMethod == null)
                result.addMsg(YamlMessages.error_DocumentActionServiceActionMustBeImplemented, docType.name, action.name, actionsType.getName(), actionMethodSignature(docType, action));

        if (docType.preconditions != null)
            for (Precondition precondition : docType.preconditions.values()) {
                if (precondition.transitions.get(0).preconditionEvaluator == null)
                    if (precondition.name.toUpperCase().equals(TaskPreconditions._FAILEDRESULT.name())) {
                        for (Transition transition : precondition.transitions)
                            transition.preconditionEvaluator = PreconditionFailedResult.METHOD;
                    } else
                        result.addMsg(YamlMessages.error_DocumentPreconditionMustBeImplemented,
                                docType.name, precondition.name, actionsType.getName(),
                                preconditionMethodSignature(docType, precondition.name));
            }
    }

    private static String actionMethodSignature(DocType docType, Action action) {
        return "public static [void | Object] " + action.name + "(final " +
                (action.service ? "" : docType.name + " doc, final ") +
                (action.params == null ? "" : docType.name + "." + action.paramsClassName + " params, ") +
                "final Result result)";
    }

    private static String preconditionMethodSignature(DocType docType, String methodName) {
        return "public static boolean " + methodName + "(final " + docType.name + " docType)";
    }

    private static void linkTasks(DocType docType, Result result) {
        if (!docType.task)
            return;
        final String className = "docflow.tasks.Task" + NamesUtil.turnFirstLetterInUpperCase(docType.name);
        final Class taskType = Play.classloader.getClassIgnoreCase(className);

        if (taskType == null) {
            // TODO: Change message
            result.addMsg(YamlMessages.error_DocumentNoCorrespondedRelationsClass, docType.name, className);
            return;
        }
        String methodName = "doJob";
        try {
            Method method = taskType.getMethod(methodName, docType.jsonBinder.type, ObjectNode.class, Result.class);
            final Class<?>[] params = method.getParameterTypes();
            final Class<?> returnType = method.getReturnType();

            boolean ok = params.length == 3;
            if (ok) {
                final int mod = method.getModifiers();
                ok &= Modifier.isPublic(mod) && Modifier.isStatic(mod);
                final String param0Class = params[0].getCanonicalName();
                ok &= param0Class != null && param0Class.equals(docType.getClassName());
                ok &= ObjectNode.class == params[1];
                ok &= Result.class == params[2];
                ok &= ActionResult.class == returnType;
            }
            if (!ok) {
                result.addMsg(YamlMessages.error_DocumentTaskExpectedToHaveSignature, docType.name,
                        taskType.getName() + "." + method.getName(),
                        taskMethodSignature(docType));
                return;
            }
            docType.taskEvoluator = method;
        } catch (NoSuchMethodException e) {
            result.addMsg(YamlMessages.error_DocumentTaskMustBeImplemented, docType.name,
                    className, taskMethodSignature(docType));
        }
    }

    private static String taskMethodSignature(DocType docType) {
        return "public static ActionResult doJob(final " + docType.name + " task, final ObjectNode update, final Result result)";
    }

    private static void linkRelations(DocType docType, Result result) {
        if (docType.relations == null)
            return;

        for (DocumentRelation relation : docType.relations.values()) {
            final String className = "docflow.relations." + NamesUtil.turnFirstLetterInUpperCase(relation.name);
            final Class relationsType = Play.classloader.getClassIgnoreCase(className);
            if (relationsType == null) {
                result.addMsg(YamlMessages.error_DocumentNoCorrespondedRelationsClass, docType.name, className);
                continue;
            }
            String methodName = "isTrue";
            try {
                Method method = relationsType.getMethod(methodName, docType.jsonBinder.type, Document.class);
                final Class<?>[] params = method.getParameterTypes();
                final Class<?> returnType = method.getReturnType();
                boolean ok = params.length == 2;
                if (ok) {
                    final int mod = method.getModifiers();
                    final String param0Class = params[0].getCanonicalName();
                    ok &= Modifier.isPublic(mod) && Modifier.isStatic(mod);
                    ok &= param0Class.equals(docType.getClassName());
                    ok &= Document.class.isAssignableFrom(params[1]);
                    ok &= boolean.class == returnType; // return void or EntityType
                }
                if (!ok) {
                    result.addMsg(YamlMessages.error_DocumentRelationExpectedToHaveSignature, docType.name,
                            relationsType.getName() + "." + method.getName(),
                            relationMethodSignature(docType, methodName));
                    continue;
                }
                relation.evaluator = method;
            } catch (NoSuchMethodException e) {
                result.addMsg(YamlMessages.error_DocumentRelationServiceActionMustBeImplemented, docType.name,
                        relation.name, className, relationMethodSignature(docType, methodName));
                continue;
            }
        }
    }

    private static String relationMethodSignature(DocType docType, String methodName) {
        return "public static boolean " + methodName + "(final " + docType.name + " docType, final Document user)";
    }

    private static void linkEnums(DocflowConfig docflowConfig, DocType docType, Result result) {
        for (Field fld : docType.allFields) {
            if (fld.type != BuiltInTypes.ENUM)
                continue;
            FieldEnum fieldEnum = (FieldEnum) fld;
            if (fieldEnum.udtType != null) {
                FieldEnum udtType = (FieldEnum) docflowConfig.fieldTypes.get(fieldEnum.udtType.toUpperCase());
                fieldEnum.values = udtType.values;
                fieldEnum.enumType = udtType.enumType;
                continue;
            }
            reflectEnum(fieldEnum, result);
        }
    }

    private static void reflectEnum(FieldEnum fieldEnum, Result result) {
        Class enumClass = Play.classloader.getClassIgnoreCase(fieldEnum.enumTypeName);
        if (enumClass == null) {
            result.addMsg(YamlMessages.error_EnumImplementationNotFound, fieldEnum.enumTypeName);
            return;
        }
        if (!enumClass.isEnum()) {
            result.addMsg(YamlMessages.error_EnumNotEnumType, fieldEnum.enumTypeName);
            return;
        }
        fieldEnum.values = new LinkedHashMap<String, Enum>();
        Object[] values = enumClass.getEnumConstants();
        for (int j = 0; j < values.length; j++) {
            Object value = values[j];
            if (fieldEnum.strValues.get(value.toString().toUpperCase()) == null) {
                result.addMsg(YamlMessages.error_EnumImplementationContainsUnderfinedValue, fieldEnum.enumTypeName, value.toString());
                continue;
            }
            fieldEnum.values.put(value.toString().toUpperCase(), (Enum) value);
        }
        for (Item item : fieldEnum.strValues.values()) {
            if (fieldEnum.values.get(item.name.toUpperCase()) == null) {
                result.addMsg(YamlMessages.error_EnumImplementationMissingValue, fieldEnum.enumTypeName, item.name);
                continue;
            }
        }
    }

    private static void linkQueriesAndCalculateMethods(DocType docType, Result result) {
        final String listQueryClassName = "docflow.queries.Query" + docType.name;
        final Class listQueryClass = Play.classloader.getClassIgnoreCase(listQueryClassName);
        if (listQueryClass == null) {
            result.addMsg(YamlMessages.error_DocumentNoCorrespondedListQueryClass, docType.name, listQueryClassName);
            return;
        }
        final Method[] methods = listQueryClass.getMethods();
        docType.queryMethods = new TreeMap<String, Method>();
        for (int j = 0; j < methods.length; j++) {
            Method method = methods[j];
            if (method.getDeclaringClass() != listQueryClass)
                continue;
            final Class<?>[] params = method.getParameterTypes();
            if (method.getName().equals("text")) {
                boolean ok = params.length == 1;
                if (ok) {
                    final int mod = method.getModifiers();
                    final String param0Class = params[0].getCanonicalName();
                    ok &= Modifier.isPublic(mod) && Modifier.isStatic(mod);
                    ok &= param0Class != null && param0Class.equals(docType.getClassName());
                    ok &= method.getReturnType() == String.class;
                }
                if (!ok) {
                    result.addMsg(YamlMessages.error_DocumentActionExpectedToHaveSignature, docType.name,
                            listQueryClass.getName() + "." + method.getName(), textMethodSignature(docType));
                    continue;
                }
                docType.textMethod = method;
            } else if (method.getName().equals("calculate")) {
                boolean ok = params.length == 3;
                if (ok) {
                    final int mod = method.getModifiers();
                    final String param0Class = params[0].getCanonicalName();
                    ok &= Modifier.isPublic(mod) && Modifier.isStatic(mod);
                    ok &= param0Class != null && param0Class.equals(docType.getClassName());
                    ok &= params[1] == BitArray.class;
                    ok &= params[2] == DocumentAccessActionsRights.class;
                    ok &= method.getReturnType() == void.class;
                }
                if (!ok) {
                    result.addMsg(YamlMessages.error_DocumentActionExpectedToHaveSignature, docType.name,
                            listQueryClass.getName() + "." + method.getName(), calculateMethodSignature(docType));
                    continue;
                }
                docType.calculateMethod = method;
            } else {
                final int mod = method.getModifiers();
                if (Modifier.isPublic(mod) && Modifier.isStatic(mod) &&
                        params.length == 2 && params[0] == Http.Request.class &&
                        params[1] == DocumentAccessActionsRights.class) { // Note: Old query.  Expected be obsolete by 2015
                    // TODO: Move list to API: 1. Instead of request give parameters map; 2. Add 'result' as 3rd parameter - it's done in query2 right below
                    docType.queryMethods.put(method.getName().toUpperCase(), method);
                    continue;
                }
                // Query2
                boolean ok = params.length == 1;
                if (ok) {
                    final String param0Class = params[0].getCanonicalName();
                    ok &= Modifier.isPublic(mod) && Modifier.isStatic(mod);
                    ok &= params[0] == QueryParams.class;
                    ok &= method.getReturnType() == DocListBuilder.class;
                }
                if (!ok) {
                    if (Modifier.isPublic(mod)) // otherwise it's an utility non-public method
                        result.addMsg(YamlMessages.error_DocumentQueryInvalidParameters, docType.name, method.getName(),
                                listQueryClass.getName(), queryMethodSignature(method.getName(), docType));
                    continue;
                }
                final String methodKey = method.getName().toUpperCase();
                docType.queryMethods.put(methodKey, method);
            }
        }
        if (docType.textField == null) {
            if (docType.textMethod != null)
                result.addMsg(YamlMessages.error_DocumentDocTypeTextMethodNotApplicable, docType.name, textMethodSignature(docType));
        } else if (docType.textMethod != null && !docType.blendText)
            result.addMsg(YamlMessages.error_DocumentBlendFlagTextMethodNotApplicable, docType.name, textMethodSignature(docType));
        else if (docType.textMethod == null && docType.blendText)
            result.addMsg(YamlMessages.error_DocumentTextMethodMissing, docType.name, textMethodSignature(docType));
    }

    private static String textMethodSignature(final DocType docType) {
        return "public static String text(final " + docType.name + " doc)";
    }

    private static String calculateMethodSignature(final DocType docType) {
        return "public static void calculate(final " + docType.name + " doc, final BitArray mask, final DocumentAccessActionsRights rights)";
    }

    private static String queryMethodSignature(final String methodName, final DocType docType) {
        return "public static DocListBuilder " + methodName + "(final QueryParams params)";
    }

    private static void linkFilters(DocType docType) {
        final String filterClassName = docType.getClassName() + "$Filters";
        final Class filterType = Play.classloader.getClassIgnoreCase(filterClassName);
        if (filterType != null && FiltersEnum.class.isAssignableFrom(filterType)) {
            docType.filterEnums = new TreeMap<String, FiltersEnum>();
            final Object[] vals = filterType.getEnumConstants();
            if (vals.length > 0) {
                for (int j = 0; j < vals.length; j++) {
                    Object val = vals[j];
                    docType.filterEnums.put(val.toString().toUpperCase(), (FiltersEnum) val);
                }
                docType.defaultFilterEnum = (FiltersEnum) vals[0];
            }
        }
    }

    private static void linkSortOrders(DocType docType) {
        final String sortOrderClassName = docType.getClassName() + "$SortOrders";
        final Class sortOrderType = Play.classloader.getClassIgnoreCase(sortOrderClassName);
        if (sortOrderType != null && SortOrdersEnum.class.isAssignableFrom(sortOrderType)) {
            docType.sortOrderEnums = new TreeMap<String, SortOrdersEnum>();
            final Object[] vals = sortOrderType.getEnumConstants();
            if (vals.length > 0) {
                for (int j = 0; j < vals.length; j++) {
                    Object val = vals[j];
                    docType.sortOrderEnums.put(val.toString().toUpperCase(), (SortOrdersEnum) val);
                }
                docType.defaultSortOrderEnum = (SortOrdersEnum) vals[0];
            }
        }
    }
}
