package code.docflow.docs;

import code.docflow.model.DocType;
import org.hibernate.annotations.Type;
import org.joda.time.DateTime;
import play.db.jpa.GenericModel;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.persistence.PrePersist;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
@MappedSuperclass
public abstract class DocumentHistory extends GenericModel {

    public int rev;

    @Column(nullable = false)
    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    public DateTime time;

    @Column(length = 100, nullable = false)
    public String action;

    @Column(columnDefinition = "text", nullable = true)
    public String params;

    @Column(columnDefinition = "text", nullable = true)
    public String changes;

    // TODO: Add undo field
    // TODO: Add rename srcType to simply 'src' (coulbe user or any other document)
    // TODO: Should I split src to DocumentRef?

    @Column(length = 100, nullable = false)
    public String srcType;

    public long srcHistId;

    // TODO: Consider adding interfaces to history later
//    @Column(length =  20, nullable = true)
//    public String interfacetype;
//
//    @Column(nullable = true)
//    public Long interfaceid;

    // Note: In production, field modified should be updated in trigger to provide consistency of time stamps in servers cluster

    @PrePersist
    protected final void updateCreated() {
        // Note: columnDefinition = "timestamp DEFAULT CURRENT_TIMESTAMP" for some reasons do not work on H2, but works find on Postgres
        time = DateTime.now();
    }

    public abstract DocType _docType();

    public abstract long getId();
}