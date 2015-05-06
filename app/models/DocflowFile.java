package models;

// Generate by DocflowGenerator

import code.docflow.DocflowConfig;
import code.docflow.action.ActionParams;
import code.docflow.model.DocType;
import code.docflow.model.State;
import code.docflow.queries.FiltersEnum;
import code.docflow.queries.SortOrdersEnum;
import code.docflow.jsonBinding.annotations.field.*;
import code.docflow.jsonBinding.annotations.doc.*;
import code.docflow.docs.Document;
import code.docflow.docs.DocumentSimple;
import code.docflow.docs.DocumentPersistent;
import code.docflow.docs.DocumentVersioned;
import code.docflow.types.DocumentRef;
import code.docflow.utils.EnumCaseInsensitiveIndex;
import org.hibernate.annotations.*;
import org.joda.time.*;
import play.db.jpa.GenericModel;
import play.exceptions.UnexpectedException;

import javax.persistence.*;
import javax.persistence.Entity;
import javax.persistence.CascadeType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static com.google.common.base.Preconditions.*;

@Entity(name = "doc_docflow_file")
public class DocflowFile extends DocumentSimple {

    public static final String TABLE = "doc_docflow_file";

    @Id
    @SequenceGenerator(name = "doc_docflow_file_seq", sequenceName = "doc_docflow_file_seq", initialValue = 1, allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "doc_docflow_file_seq")
    public long id;

    @Transient
    public String text = "";

    public String getText() {
        return text = title;
    }

    @Column(length = 100, nullable = false, columnDefinition = "character varying(100) default ''")
    public String filename = "";

    @Column(length = 2000, nullable = false, columnDefinition = "character varying(2000) default ''")
    public String title = "";

    public boolean blocked;

    @AttributeOverrides({
        @AttributeOverride(name = "type", column = @Column(name = "document_type", nullable=false)),
        @AttributeOverride(name = "id", column = @Column(name = "document_id", nullable=false))
    })
    @Embedded
    public DocumentRef document = DocumentRef.NULL;

    @Column(length = 200, nullable = true)
    public String field;

    public enum States {
        NEW("new", 0),
        PERSISTED("persisted", 1);
        public final int index;
        private final String name;
        private States(String name, int index) { this.name = name; this.index = index; }
        public String toString() { return name; }
    }

    public static EnumCaseInsensitiveIndex<States> _states = new EnumCaseInsensitiveIndex<States>(States.class);

    public enum Filters implements FiltersEnum {
        ALL("all");
        private final String name;
        public final String where;
        private Filters(String name) { this.name = name; this.where = null; }
        private Filters(String name, String where) { this.name = name; this.where = where; }
        public String toString() { return name; }
        @Override public String getWhere() { return where; }
    }

    public static EnumCaseInsensitiveIndex<Filters> _filters = new EnumCaseInsensitiveIndex<Filters>(Filters.class);

    public enum SortOrders implements SortOrdersEnum {
        BY_FILENAME("byFilename", "doc.filename"),
        BY_TITLE("byTitle", "doc.title"),
        BY_BLOCKED("byBlocked", "doc.blocked"),
        BY_FIELD("byField", "doc.field");
        private final String name;
        public final String orderBy;
        private SortOrders(String name) { this.name = name; this.orderBy = null; }
        private SortOrders(String name, String orderBy) { this.name = name; this.orderBy = orderBy; }
        public String toString() { return name; }
        @Override public String getOrderBy() { return orderBy; }
    }

    public static EnumCaseInsensitiveIndex<SortOrders> _sortOrders = new EnumCaseInsensitiveIndex<SortOrders>(SortOrders.class);

    public enum Fields {
        ID("id", 0),
        TEXT("text", 1),
        FILENAME("filename", 2),
        TITLE("title", 3),
        BLOCKED("blocked", 4),
        DOCUMENT("document", 5),
        FIELD("field", 6),
        CREATOR("creator", 7),
        CREATED("created", 8);
        public final int index;
        private final String name;
        private Fields(String name, int index) { this.name = name; this.index = index; }
        public String toString() { return name; }
    }

    public static EnumCaseInsensitiveIndex<Fields> _fields = new EnumCaseInsensitiveIndex<Fields>(Fields.class);

    public enum Actions {
        CREATE("create", 0),
        RETRIEVE("retrieve", 1),
        UPDATE("update", 2),
        DELETE("delete", 3),
        RECOVER("recover", 4);
        public final int index;
        private final String name;
        private Actions(String name, int index) { this.name = name; this.index = index; }
        public String toString() { return name; }
    }

    public static EnumCaseInsensitiveIndex<Actions> _actions = new EnumCaseInsensitiveIndex<Actions>(Actions.class);

    private static DocType _type;

    public static DocType _type() {
        if (_type == null) {
            _type = DocflowConfig.instance.documents.get("DOCFLOWFILE");
            _resetQueue.add(new ResetForTest() { @Override public void reset() { _type = null; }});
        }
        return _type;
    }

    @Override
    public DocType _docType() {
        return _type();
    }

    @Override
    public State _state() {
        if (_state == null || _isPersisted() ^ _state.name.equals(States.PERSISTED.toString()))
            _state = _docType().statesArray[_isPersisted() ? States.PERSISTED.index : States.NEW.index];
        return _state;
    }

    @Override
    public void _updateState(State newState) {
        States state = _states.get(newState.name);
        checkNotNull(state, "Unknown state: %s", newState.name);
    }


    @Override
    public boolean _isPersisted() {
        return id > 0;
    }

    @Override
    public String _fullId() {
        return _isPersisted() ? "DocflowFile@" + id : "DocflowFile";
    }

    @Override
    public DocumentRef _ref() {
        return new DocumentRef(this, "DocflowFile", id);
    }
}
