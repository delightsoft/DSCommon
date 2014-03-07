package code.tests;

import code.docflow.DocflowConfig;
import code.docflow.queries.Query;
import code.docflow.rights.RightsCalculator;
import code.docflow.templateModel.TmplRoot;
import code.docflow.yaml.builders.ItemBuilder;
import code.docflow.yaml.compositeKeyHandlers.ItemCompositeKeyHandler;
import code.docflow.yaml.converters.ArrayConverter;
import code.docflow.yaml.converters.PrimitiveTypeConverter;
import code.jsonBinding.HistoryAccessor;
import code.jsonBinding.JsonTypeBinder;
import code.jsonBinding.RecordAccessor;
import code.jsonBinding.SubrecordAccessor;
import code.jsonBinding.binders.type.TypeBinder;
import code.models.Document;
import play.db.jpa.JPAPlugin;
import play.test.Fixtures;

import java.util.List;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class TestUtil {

    public static void flush() {
        JPAPlugin.closeTx(false);
        JPAPlugin.startTx(false);
    }

    public static void clearDb() {
        Fixtures.deleteAllModels();
        flush();
    }

    public static void resetBeforeReloadDocflowConfig() {

        // yaml reader
        ItemBuilder.factory._resetForTest();
        ItemCompositeKeyHandler.flagsAccessorsFactory._resetForTest();
        ArrayConverter.factory._resetForTest();
        PrimitiveTypeConverter.ENUM_CONVERTERS._resetForTest();

        // docflowConfig
        DocflowConfig._resetForTest();
        Document._resetForTest();

        // type binders
        TypeBinder.factory._resetForTest();
        JsonTypeBinder.factory._resetForTest();

        // template models
        TmplRoot.factory._resetForTest();
        TmplRoot.factoryWithUdtDocument._resetForTest();

        // rights management
        RightsCalculator._resetForTest();

        // accessors
        HistoryAccessor.factory._resetForTest();
        RecordAccessor.factory._resetForTest();
        SubrecordAccessor.factory._resetForTest();

        // queries
        Query.factory._resetForTest();
    }

    public static int countIgnoreNull(List list) {
        int cnt = 0 ;
        for (Object i : list)
            if (i != null)
                cnt++;
        return cnt;
    }

    public static <T> T getIgnoreNull(List<T> list, int n) {
        int cnt = 0 ;
        for (T i : list)
            if (i != null) {
                if (cnt == n)
                    return i;
                cnt++;
            }
        return null;
    }
}
