package controllers;

import code.docflow.Docflow;
import code.docflow.DocflowConfig;
import code.docflow.action.Transaction;
import code.docflow.controlflow.Result;
import code.docflow.docs.DocumentPersistent;
import code.docflow.model.DocType;
import code.docflow.users.CurrentUser;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import play.Logger;
import play.Play;
import play.db.jpa.JPABase;
import play.db.jpa.Transactional;
import play.exceptions.UnexpectedException;
import play.mvc.With;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

@With(CurrentUser.CheckAccess.class)
public class DocflowHttpFixTextController extends DocflowControllerBase {
    @Transactional(readOnly = false)
    public static void fixText() {
        Logger.info("DocflowHttpFixTextController.fix running ...");
        final Result result = new Result();
        final ObjectNode empty = JsonNodeFactory.instance.objectNode();
        Transaction.dataImport(result, new Transaction.Delegate<Object>() {
            @Override
            public Object body(int attempt, Result result) {
                int i = 0;
                for (DocType docType : DocflowConfig.instance.documentsArray) {
                    if (docType.blendText) {
                        try {
                            final Class docClazz = Play.classloader.getClassIgnoreCase(docType.getClassName());
                            final Method findAll;
                            findAll = docClazz.getMethod("findAll");
                            Preconditions.checkNotNull(findAll);
                            List<JPABase> all = (List<JPABase>) findAll.invoke(null);
                            Logger.info("DocType '%s': Found %d records to fix", docType.name, all.size());
                            for (JPABase jpaBase : all) {
                                final DocumentPersistent doc = (DocumentPersistent) jpaBase;
                                Docflow.update(doc, empty, result);
                                if (result.isError()) {
                                    result.toLogger("Failed on document: " + docType.jsonBinder.recordAccessor.getFullId.invoke(doc));
                                    return null;
                                }
                                if ((i++ % 50) == 0)
                                    Transaction.commit();
                            }
                        } catch (NoSuchMethodException e) {
                            throw new UnexpectedException(docType.name, e);
                        } catch (IllegalAccessException e) {
                            throw new UnexpectedException(docType.name, e);
                        } catch (InvocationTargetException e) {
                            throw new UnexpectedException(docType.name, e);
                        }
                    }
                }
                return null;
            }
        });
        if (result.isError())
            result.toLogger("Failed");
        Logger.info("DocflowHttpFixTextController.fix finished");
    }
}
