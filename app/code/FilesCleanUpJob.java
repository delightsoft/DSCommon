package code;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import models.DocflowFile;
import org.joda.time.DateTime;
import org.joda.time.Period;
import play.jobs.Every;
import play.jobs.Job;

import java.util.List;

@Every("6h")
public class FilesCleanUpJob extends Job {

    public static final Period DELETE_PERIOD = Period.hours(24);

    @Override
    public void doJob() throws Exception {
        List<DocflowFile> toDelete = DocflowFile.find("blocked = false and created <= ?1", DateTime.now().minus(DELETE_PERIOD)).fetch();
        for (DocflowFile docflowFile : toDelete)
            docflowFile.delete();
    }
}
