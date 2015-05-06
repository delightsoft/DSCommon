package code.docflow.utils;

import code.docflow.DocflowConfig;
import code.docflow.docs.Document;
import code.docflow.jsonBinding.HistoryAccessor;
import code.docflow.jsonBinding.JsonTypeBinder;
import code.docflow.jsonBinding.RecordAccessor;
import code.docflow.jsonBinding.SubrecordAccessor;
import code.docflow.jsonBinding.binders.type.TypeBinder;
import code.docflow.queries.Query;
import code.docflow.rights.RightsCalculator;
import code.docflow.templateModel.TmplModel;
import code.docflow.yaml.builders.ItemBuilder;
import code.docflow.yaml.compositeKeyHandlers.ItemCompositeKeyHandler;
import code.docflow.yaml.converters.ArrayConverter;
import code.docflow.yaml.converters.PrimitiveTypeConverter;
import com.google.common.base.Strings;
import org.junit.Assert;
import play.Logger;
import play.Play;
import play.db.jpa.JPAPlugin;
import play.exceptions.UnexpectedException;
import play.test.Fixtures;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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

        // template docs
        TmplModel.factory._resetForTest();
        TmplModel.factoryWithUdtDocument._resetForTest();

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
        int cnt = 0;
        for (Object i : list)
            if (i != null)
                cnt++;
        return cnt;
    }

    public static <T> T getIgnoreNull(List<T> list, int n) {
        int cnt = 0;
        for (T i : list)
            if (i != null) {
                if (cnt == n)
                    return i;
                cnt++;
            }
        return null;
    }

    public static java.sql.Connection testDbConnection() throws SQLException {
        final Properties p = Play.configuration;
        if (p.getProperty("db.user") == null)
            return DriverManager.getConnection(p.getProperty("db.url"));
        else
            return DriverManager.getConnection(p.getProperty("db.url"), p.getProperty("db.user"), p.getProperty("db.pass"));
    }

    public static class ParallelSteps {

        /**
         * Reasonable timeout, tests are waiting for parallel things to appear in the schema.
         */
        public static final int TIMEOUT = 3 * 1000;

        public static final String MAIN_JOB = "main";

        public static final boolean LOG = true;

        boolean over;

        public interface TestBody {
            void body(String jobName, ParallelSteps parallelSteps) throws Throwable;
        }

        public interface StepBody {
            void body() throws Throwable;
        }

        // Get instance by ID suuport
        static ConcurrentHashMap<UUID, ParallelSteps> instances = new ConcurrentHashMap<UUID, ParallelSteps>();

        public final UUID id = UUID.randomUUID();

        final Thread mainTestThread = Thread.currentThread();

        public ParallelSteps(String[] jobs) {
            instances.put(id, this);
            this.jobs.add(new Job(MAIN_JOB));
            for (final String jobName : jobs) {
                if (Strings.isNullOrEmpty(jobName))
                    throw new AssertionError(String.format("Job name '%s' is invalid", jobName));
                for (Job job : this.jobs)
                    if (job.name.equals(jobName))
                        throw new AssertionError(String.format("Job name '%s' is duplicated", jobName));
                this.jobs.add(new Job(jobName));
            }
        }

        public static ParallelSteps getInstance(String id) {
            try {
                return getInstance(UUID.fromString(id));
            } catch (IllegalArgumentException e) {
                throw new UnexpectedException(String.format("ParallelActivities: Wrong ID format '%s'", id));
            }
        }

        public static ParallelSteps getInstance(UUID id) {
            final ParallelSteps parallelSteps = instances.get(id);
            if (parallelSteps == null)
                throw new UnexpectedException(String.format("ParallelActivities (id: '%s'): Not found", id.toString()));
            final Thread thread = Thread.currentThread();
            if (parallelSteps.over)
                thread.interrupt(); // since it's later for test
            else if (thread != parallelSteps.mainTestThread)
                parallelSteps.threads.put(thread, thread);
            return parallelSteps;
        }

        // Result
        AssertionError error;

        // Jobs
        static final class Job {
            final String name;
            boolean taken;
            boolean finished;

            Job(String name) {
                this.name = name;
            }

            @Override
            public String toString() {
                return name;
            }
        }

        ArrayList<Job> jobs = new ArrayList<Job>();
        ConcurrentMap<Thread, Thread> threads = new ConcurrentHashMap();

        Job jobInstantiated(final String jobName) {
            synchronized (this) {
                Job job = null;
                for (Job v : jobs)
                    if (v.name.equals(jobName))
                        job = v;
                if (job == null) {
                    error = new AssertionError(String.format("%s: Job '%s' is not expected", this, jobName));
                    deliverError();
                }
                job.taken = true;
                this.notify();
                return job;
            }
        }

        @Override
        public String toString() {
            return "ParallelActivities{id:" + id.toString() + "}";
        }

        void jobFinished(Job job) {
            synchronized (this) {
                job.finished = true;
                this.notify();
            }
        }

        public class Step {

            final char name;

            boolean taken;
            boolean finished;

            Step prevStep;
            Step nextStep;

            Step(char name, Step prevStep) {
                this.name = name;
                if (prevStep != null) {
                    this.prevStep = prevStep;
                    if (prevStep.nextStep != null) {
                        error = new AssertionError(String.format("Step '%s': Already has next step", prevStep.name));
                        deliverError();
                    }
                    prevStep.nextStep = this;
                }
            }
        }

        ArrayList<Step> steps = new ArrayList<Step>();

        /**
         * Returns step with given name (index).  If step requested 2nd time, throws an exception.
         */
        Step getStep(char name) {
            synchronized (this) {
                if (!Character.isLetter(name)) {
                    error = new AssertionError(String.format("Invalid step name '%s'. Expected one letter name"));
                    deliverError();
                }
                final int n = Character.toUpperCase(name) - 'A';
                if (steps.size() > n) {
                    final Step step = steps.get(n);
                    if (step.taken) {
                        error = new AssertionError(String.format("Step '%s' already used in other part", name));
                        deliverError();
                    }
                    step.taken = true;
                    return step;
                }
                Step step = steps.size() > 0 ? steps.get(steps.size() - 1) : null;
                for (int j = steps.size(); j <= n; j++)
                    steps.add(step = new Step((char) ('A' + j), step));
                step.taken = true;
                return step;
            }
        }

        private void deliverError() {
            if (over)
                throw error;
            else {
                mainTestThread.interrupt();
                Thread.currentThread().interrupt();
            }
        }

        public void test(String jobName, TestBody test) {
            final Job job = jobInstantiated(jobName);
            try {
                if (LOG)
                    Logger.info("Job '%s': Started", jobName);
                test.body(job.name, this);
            } catch (InterruptedException e) {
                // nothing
            } catch (AssertionError e) {
                error = e;
            } catch (Throwable e) {
                error = new AssertionError(e);
            } finally {
                if (LOG)
                    Logger.info("Job '%s': Finished", jobName);
                jobFinished(job);
                // main thread awaits for all job to instantiated and be completed
                if (Thread.currentThread() == mainTestThread) {
                    synchronized (this) {
                        int notInstantiatedJobs = countNotInstantiatedJobs();
                        int notFinishedJobs = -1;
                        while (true) {
                            // throw error, if any
                            if (error != null)
                                throw error;
                            // wait while all jobs will start, and if non new jobs are starting within TIMEOUT, report this by exception
                            if (notInstantiatedJobs > 0) {
                                try {
                                    this.wait(TIMEOUT);
                                } catch (InterruptedException e) {
                                    if (error != null)
                                        throw error;
                                }
                                final int newNotInstantiatedJobs = countNotInstantiatedJobs();
                                if (notInstantiatedJobs != newNotInstantiatedJobs) {
                                    notInstantiatedJobs = newNotInstantiatedJobs;
                                    continue;
                                }
                                throw new AssertionError(
                                        String.format("%s: Expected jobs are not started: %s", this, notInstantiatedJobs()));
                            }
                            // wait while all jobs will finish
                            if ((notFinishedJobs = countNotFinishedJobs()) == 0)
                                break;
                            try {
                                this.wait(TIMEOUT);
                            } catch (InterruptedException e) {
                                if (error != null)
                                    throw error;
                            }
                            if (countNotFinishedJobs() > 0) {
                                // clean up known threads
                                for (Thread thread : threads.values())
                                    thread.interrupt();
                                throw new AssertionError(
                                        String.format("%s: Jobs are finishing too long: %s", this, notFinishedJobs()));
                            }
                        }
                    }
                    if (LOG)
                        Logger.info("All jobs are finished");
                }
            }
        }

        private String notInstantiatedJobs() {
            final StringBuilder res = new StringBuilder();
            for (Job job : jobs)
                if (!job.taken) {
                    if (res.length() > 0)
                        res.append(", ");
                    res.append(job.name);
                }
            return res.toString();
        }

        private String notFinishedJobs() {
            final StringBuilder res = new StringBuilder();
            for (Job job : jobs)
                if (!job.finished) {
                    if (res.length() > 0)
                        res.append(", ");
                    res.append(job.name);
                }
            return res.toString();
        }

        private int countNotInstantiatedJobs() {
            int res = 0;
            for (Job job : jobs)
                if (!job.taken)
                    res++;
            return res;
        }

        private int countNotFinishedJobs() {
            int res = 0;
            for (Job job : jobs)
                if (!job.finished)
                    res++;
            return res;
        }

        public void step(char stepName, StepBody body) {
            final Step step = getStep(stepName);
            if (LOG)
                Logger.info("Step '%s': Instantiated", stepName);
            if (step.prevStep != null)
                synchronized (step.prevStep) {
                    if (!step.prevStep.finished) {
                        if (LOG)
                            Logger.info("Step '%s': Waiting for step '%s'.", step.name, step.prevStep.name);
                        try {
                            step.prevStep.wait();
                            Assert.assertTrue(step.prevStep.finished);
                        } catch (InterruptedException e) {
                            deliverError();
                        }
                    }
                }
            try {
                if (LOG)
                    Logger.info("Step '%s': started", stepName);
                body.body();
            } catch (AssertionError e) {
                error = e;
                deliverError();
            } catch (Throwable e) {
                error = new AssertionError(e);
                deliverError();
            } finally {
                synchronized (step) {
                    step.finished = true;
                    step.notify();
                }
                if (LOG)
                    Logger.info("Step '%s': finished", stepName);
            }
        }
    }
}
