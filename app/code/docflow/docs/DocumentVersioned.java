package code.docflow.docs;

import org.hibernate.annotations.Type;
import org.joda.time.DateTime;

import javax.persistence.*;

import static com.google.gdata.util.common.base.Preconditions.checkNotNull;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */

@MappedSuperclass
public abstract class DocumentVersioned extends DocumentPersistent {

    @Version
    public int rev;

    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    @Column(nullable = false)
    public DateTime created;

    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    @Column(nullable = false)
    public DateTime modified;

    @PrePersist
    protected final void updateCreated() {
        created = DateTime.now();
        modified = DateTime.now();
    }

    @PreUpdate
    protected final void updateModified() {
        modified = DateTime.now();
    }

    public boolean deleted;
}
