package code.docflow.jsonBinding.binders.doc;

import code.docflow.jsonBinding.JsonTypeBinder;

import java.lang.reflect.Field;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */

/**
 * Binder to mark fields that are part of infrastructure, but managed by
 * binders of other fields. I.e. PK and I in substructures, managed by collection field in container structure.
 * <p/>
 * Main purpose of this type of binders is to simplify logic of checking are all fields within class has corresponded
 * fields within docflow model.
 */
public final class StubBinder extends JsonTypeBinder.FieldAccessor {
    public StubBinder(Field fld, String fldName) {
        super(fld, null, null, fldName);
    }
}
