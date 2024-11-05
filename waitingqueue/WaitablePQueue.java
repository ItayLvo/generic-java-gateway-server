package waitingqueue;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class WaitablePQueue<E> {
    private final Queue<E> queue;
    private final Semaphore semaphore = new Semaphore(0);
    private final Lock lock = new ReentrantLock();

    //constructor with Comparator
    public WaitablePQueue(Comparator<E> comparator) {
        queue = new PriorityQueue<>(comparator);
    }

    //Constructor without Comparator, E should be Comparable
    public WaitablePQueue() {
        this(null);
    }

    public void enqueue(E e) {
        lock.lock();
        try {
            queue.add(e);
            semaphore.release();
        } finally {
            lock.unlock();
        }
    }

    public E dequeue() {
        try {
            semaphore.acquire();
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }

        lock.lock();
        try {
            return queue.remove();
        } finally {
            lock.unlock();
        }
    }


    public E dequeue(long timeout, TimeUnit unit) {
        //calculate timeout deadline and remaining time to wait
        long remainingTimeToWait = unit.toNanos(timeout);
        long deadline = System.nanoTime() + remainingTimeToWait;

        //try to acquire semaphore before timeout
        try {
            if (!semaphore.tryAcquire(remainingTimeToWait, TimeUnit.NANOSECONDS)) {
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        //update remaining time to wait after waiting for semaphore
        remainingTimeToWait = deadline - System.nanoTime();

        boolean acquiredLock = false;
        try {
            if (!lock.tryLock(remainingTimeToWait, TimeUnit.NANOSECONDS)) {
                return null;    //if timed out while waiting for lock - return null
            }
            acquiredLock = true;
            return queue.remove();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (acquiredLock) {
                lock.unlock();
            } else {
                //if timed out while waiting for lock - release semaphore
                semaphore.release();
            }
        }
    }


    public boolean remove(Object o) {
        lock.lock();
        try {
            boolean isRemoved = queue.remove(o);
            if (isRemoved) {
                semaphore.release();
            }
            return isRemoved;
        } finally {
            lock.unlock();
        }
    }

    public E peek() {
        lock.lock();
        try {
            return queue.peek();
        } finally {
            lock.unlock();
        }
    }


    public int size() {
        lock.lock();
        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }


    public boolean isEmpty() {
        lock.lock();
        try {
            return queue.isEmpty();
        } finally {
            lock.unlock();
        }
    }
}
