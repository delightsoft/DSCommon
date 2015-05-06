package code.docflow.controlflow;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import play.jobs.Job;
import play.libs.F;

import java.util.LinkedList;

/**
 * Implements sequential processing of Jobs.  One job in a time, once it's over next job starts.
 */
public final class JobSequence<V> {

    private final LinkedList<JobHandler> jobs = new LinkedList<JobHandler>();
    private F.Promise lastJobPromise;

    public class JobHandler<V> implements F.Action<F.Promise<V>> {
        public final Job<V> job;
        public final F.Promise<V> promise;

        public JobHandler(Job<V> job) {
            this.job = job;
            this.promise = new F.Promise<V>();
        }

        @Override
        public void invoke(F.Promise<V> result) {

            synchronized (jobs) {
                lastJobPromise = null;
                final JobHandler nextJob = jobs.poll();
                if (nextJob != null)
                    (lastJobPromise = nextJob.job.now()).onRedeem(nextJob); // nextJob implement onRedeem handler (this method)
            }

            promise.invoke(result.getOrNull());
        }
    }

    /**
     * Adds a job to the sequence.  Returns Promise that will be redeemed once the job is completed.
     */
    public F.Promise<V> add(Job<V> job) {
        final JobHandler h = new JobHandler(job);
        synchronized (jobs) {
            if (lastJobPromise == null)
                (lastJobPromise = job.now()).onRedeem(h);
            else
                jobs.add(h);
        }
        return h.promise;
    }
}
