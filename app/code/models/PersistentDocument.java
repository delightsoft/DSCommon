package code.models;

import org.hibernate.annotations.Type;
import org.joda.time.DateTime;

import javax.persistence.*;

import static com.google.gdata.util.common.base.Preconditions.checkNotNull;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
@MappedSuperclass
public abstract class PersistentDocument extends Document {

    @Version
    public int rev;

    @Type(type = "org.joda.time.contrib.hibernate.PersistentDateTime")
    @Column(nullable = false)
//    @Column(columnDefinition = "timestamp DEFAULT CURRENT_TIMESTAMP", insertable = false, nullable = false)
    public DateTime created;

    @Type(type = "org.joda.time.contrib.hibernate.PersistentDateTime")
    @Column(nullable = false)
//    @Column(columnDefinition = "timestamp DEFAULT CURRENT_TIMESTAMP", insertable = false, nullable = false)
    public DateTime modified; // Update by now() on every record modification, if DB created by evolution script

    // Note: In production, field modified should be updated in trigger to provide consistency of time stamps in servers cluster

    @PrePersist
    protected final void updateCreated() {
        // Note: columnDefinition = "timestamp DEFAULT CURRENT_TIMESTAMP" for some reasons do not work on H2, but works find on Postgres
        created = DateTime.now();
        modified = DateTime.now();
    }

    @PreUpdate
    protected final void updateModified() {
        modified = DateTime.now();
    }

    public boolean deleted;
}
