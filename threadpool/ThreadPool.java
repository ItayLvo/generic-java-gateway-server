package threadpool;

import waitingqueue.WaitablePQueue;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadPool implements Executor {
    private final WaitablePQueue<Task<?>> taskQueue = new WaitablePQueue<>();
    private final AtomicInteger currentNumberOfThreads;
    private final Object poolPauseLock = new Object();
    private volatile boolean isShutDown = false;
    private volatile boolean isPaused = false;
    private static final int HIGHEST_PRIORITY = Priority.HIGH.getValue() + 1;
    private static final int LOWEST_PRIORITY = Priority.LOW.getValue() - 1 ;


    public ThreadPool() {
        //set number of threads in the pool according to number of CPU cores times 1.5
        this((int) (Runtime.getRuntime().availableProcessors() * 1.5));
    }

    public ThreadPool(int nThreads) {
        currentNumberOfThreads = new AtomicInteger(0);
        //create and start n running Threads
        for (int i = 0; i < nThreads; ++i) {
            Worker worker = new Worker();
            worker.start();
        }
    }


    private final class Worker extends Thread {
        @Override
        public void run() {
            //each created threads increases the current thread counter
            currentNumberOfThreads.incrementAndGet();
            boolean isTaskPoison = false;
            try {
                while (!isTaskPoison) {
                    //dequeues a task from the queue. waits if no tasks are available to dequeue (blocking)
                    Task<?> task = taskQueue.dequeue();
                    //set the poison flag to true if the task is supposed to kill the thread
                    isTaskPoison = task.isPoison;
                    //run the current task (blocking)
                    task.executeTask();
                }
            } finally {
                //thread was killed: decrease number of current threads
                synchronized (poolPauseLock) {
                    currentNumberOfThreads.decrementAndGet();
                    //notify any threads waiting for awaitTermination
                    if (currentNumberOfThreads.get() == 0) {
                        poolPauseLock.notifyAll();
                    }
                }
            }
        }
    }   //end of Worker class


    @Override
    public void execute(Runnable runnable) {
        submit(runnable);
    }


    public Future<?> submit(Runnable command) {
        Callable<Object> callableWrapper = Executors.callable(command);
        return submit(callableWrapper, Priority.MEDIUM);

    }

    public Future<?> submit(Runnable command, Priority p) {
        Callable<Object> callableWrapper = Executors.callable(command);
        return submit(callableWrapper, p);
    }

    public <T> Future<T> submit(Runnable command, Priority p, T value) {
        Callable<T> callableWrapper = Executors.callable(command, value);
        return submit(callableWrapper, p);
    }

    public <T> Future<T> submit(Callable<T> command) {
        return submit(command, Priority.MEDIUM);
    }

    public <T> Future<T> submit(Callable<T> command, Priority p) {
        if(command == null) {
            throw new NullPointerException();
        }

        if (isShutDown) {
            //if shutdown() was called before submit
            throw new RejectedExecutionException("ThreadPool is shut down");
        }

        Task<T> task = new Task<>(command, p.getValue());
        taskQueue.enqueue(task);

        return task.getFuture();
    }


    public void setNumOfThreads(int nThreads) {
        if (nThreads < 0) {
            throw new IllegalArgumentException("Number of threads cannot be negative");
        }
        if (isShutDown){
            throw new RejectedExecutionException("ThreadPool is shut down");
        }

        //save thread counter in tmp var because the global counter will change for every worker created/killed
        int tmpCurrentNumberOfThreads = currentNumberOfThreads.get();

        if (nThreads > tmpCurrentNumberOfThreads) {
            //increase number of threads in the pool
            for (int i = 0; i < (nThreads - tmpCurrentNumberOfThreads); ++i) {
                if (isPaused) {
                    //if pool is currently paused - enqueue a sleeping pill for each new thread
                    Task<Void> pauseThreadTask = createSleepingPillTask(HIGHEST_PRIORITY);
                    taskQueue.enqueue(pauseThreadTask);
                }
                Worker worker = new Worker();
                worker.start();
            }
        } else {
            //decrease number of threads in the pool
            for (int i = 0; i < (tmpCurrentNumberOfThreads - nThreads); ++i) {
                //create a "poison pill" callable and wrap it in a new Task with max priority, then enqueue it
                Task<Void> killThreadTask = createPoisonPillTask(HIGHEST_PRIORITY);
                taskQueue.enqueue(killThreadTask);
            }
        }
    }


    private Task<Void> createPoisonPillTask(Integer priority) {
        Callable<Void> killThreadCallable = new Callable<Void>() {
            @Override
            public Void call() {
                return null;    //do nothing
            }
        };
        Task<Void> killThreadTask = new Task<>(killThreadCallable, priority);
        killThreadTask.isPoison = true;

        return killThreadTask;
    }


    public void pause() {
        if (isPaused) {
            return;
        }
        //set flag to true to stop client from inserting new tasks
        isShutDown = true;
        //set isPaused flag that threads check in their sleeping pill task
        isPaused = true;

        for (int i = 0; i < currentNumberOfThreads.get(); ++i) {
            //the sleeping pill task will have the highest priority
            Task<Void> pauseThreadTask = createSleepingPillTask(HIGHEST_PRIORITY);
            taskQueue.enqueue(pauseThreadTask);
        }
    }


    private Task<Void> createSleepingPillTask(int priority) {
        Callable<Void> pauseThreadCallable = new Callable<Void>() {
            @Override
            public Void call() {
                synchronized (poolPauseLock) {
                    while (isPaused) {
                        try {
                            poolPauseLock.wait();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                return null;
            }
        };

        return (new Task<>(pauseThreadCallable, priority));
    }


    public void resume() {
        isShutDown = false; //re-allow users to insert new tasks

        synchronized (poolPauseLock) {
            isPaused = false;
            poolPauseLock.notifyAll();
        }
    }


    public void shutdown() {
        //set shutdown flag to true so client can't add any tasks to the task queue
        isShutDown = true;

        //create and enqueue a poison pill task for each thread
        for (int i = 0; i < (currentNumberOfThreads.get()); ++i) {
            //create a "poison pill" callable and wrap it in a new Task with the lowest priority, then enqueue it
            Task<Void> killThreadTask = createPoisonPillTask(LOWEST_PRIORITY);
            taskQueue.enqueue(killThreadTask);
        }
    }

    public void awaitTermination() throws InterruptedException {
        awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    }


    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        //calculate timeout deadline and remaining time to wait
        long remainingTimeToWait = unit.toMillis(timeout);
        long deadline = System.currentTimeMillis() + remainingTimeToWait;

        synchronized (poolPauseLock) {
            while (currentNumberOfThreads.get() > 0) {
                remainingTimeToWait = deadline - System.currentTimeMillis();
                if (remainingTimeToWait <= 0) {
                    return false;   //timed out
                }
                poolPauseLock.wait(remainingTimeToWait);
            }
        }

        return true;
    }

    //temporary helper method for JUnit tests - should be removed!
    public int getCurrentNumberOfThreads() {
        return currentNumberOfThreads.get();
    }


    private class Task<T> implements Comparable<Task<T>> {
        private final Callable<T> callable;
        private final Future<T> future;
        private final int priority;
        private T result = null;
        private final Semaphore isDoneSemaphore = new Semaphore(0);
        private Exception callableExceptionStatus = null;
        private boolean isDone = false;
        private boolean isCancelled = false;
        public volatile boolean isPoison = false;


        public Task(Callable<T> callable, int priority) {
            this.callable = callable;
            this.priority = priority;
            this.future = new TaskFuture();
        }


        private void executeTask() {
            try {
                result = callable.call();   //execute the command
                isDone = true;  //set isDone flag, which is checked in Future.get() method
            } catch (Exception e) {
                this.callableExceptionStatus = e; //if call() threw an exception, catch it and save it for Future.get(), then continue to next task
            } finally {
                isDoneSemaphore.release();  //release semaphore for Future.get() method
            }
        }


        @Override
        public int compareTo(Task<T> other) {
            return other.priority - this.priority;
        }


        private Future<T> getFuture() {
            return future;
        }


        private class TaskFuture implements Future<T> {

            @Override
            public boolean cancel(boolean ignoredArgument) {
                //check if task is already complete or cancelled before
                if (isDone || isCancelled) {
                    return false;
                }
                //try to remove task from queue
                boolean removed = ThreadPool.this.taskQueue.remove(Task.this);
                //set isCancelled to "true" only if it was actually removed
                if (removed) {
                    isCancelled = true;
                }
                //after cancel() - isDone() will always be true, no matter at what stage of execution
                isDone = true;

                return removed;
            }


            @Override
            public boolean isCancelled() {
                return isCancelled;
            }

            @Override
            public boolean isDone() {
                return isDone;
            }


            @Override
            public T get() throws InterruptedException, ExecutionException {
                //re-use the get() method which receives time limit arguments, allow endless time limit
                try {
                    return get(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
                } catch (TimeoutException e) {
                    //should not happen!
                    throw new RuntimeException("Timed out on get()");
                }
            }


            @Override
            public T get(long l, TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
                //if Task.executeTask() resulted in an exception
                if (Task.this.callableExceptionStatus != null) {
                    throw new ExecutionException(callableExceptionStatus);
                }

                //if the task was cancelled using Future.cancel()
                if (isCancelled) {
                    throw new CancellationException();
                }

                //if the task is done, return the result
                if (Task.this.isDone) {
                    return Task.this.result;
                } else {
                    //if the task is not done yet, wait for the semaphore token (executeTask() releases it when done)
                    if (isDoneSemaphore.tryAcquire(l, timeUnit) == true) {
                        //check if cancellation or exception status changed while waiting
                        if (Task.this.callableExceptionStatus != null) {
                            throw new ExecutionException(callableExceptionStatus);
                        }
                        if (isCancelled) {
                            throw new CancellationException();
                        }

                        return Task.this.result;
                    } else {
                        //if semaphore timed out
                        throw new TimeoutException();
                    }
                }
            }
        }   //end of TaskFuture class
    }
}
