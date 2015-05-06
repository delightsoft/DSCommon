package code.docflow.docs;

import code.docflow.compiler.enums.BuiltInActionSource;
import code.docflow.users.CurrentUser;
import org.hibernate.annotations.Type;
import org.joda.time.DateTime;

import javax.persistence.*;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */

@MappedSuperclass
public abstract class DocumentSimple extends DocumentPersistent {

    @Column(length = 100, nullable = false)
    public String creator;

    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    @Column(nullable = false)
    public DateTime created;

    @PrePersist
    protected final void updateCreated() {
        final Document user = CurrentUser.getInstance().getUser();
        this.creator = user != null ? user._fullId() : BuiltInActionSource.SYSTEM.toString();
        this.created = DateTime.now();
    }
}
