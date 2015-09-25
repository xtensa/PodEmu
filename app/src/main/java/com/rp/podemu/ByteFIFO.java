package com.rp.podemu;

/**
 * Source: http://www.java2s.com/Code/Java/Threads/ByteFIFO.htm
 * Provided as is.
 */
class ByteFIFO extends Object {
    private byte[] queue;
    private int capacity;
    private int size;
    private int head;
    private int tail;

    public ByteFIFO(int cap) {
        capacity = ( cap > 0 ) ? cap : 1; // at least 1
        queue = new byte[capacity];
        head = 0;
        tail = 0;
        size = 0;
    }

    public int getCapacity() {
        return capacity;
    }

    public synchronized int getSize() {
        return size;
    }

    public synchronized boolean isEmpty() {
        return ( size == 0 );
    }

    public synchronized boolean isFull() {
        return ( size == capacity );
    }

    public synchronized void add(byte b)
            throws InterruptedException {

        // TODO decide if really wait here
        waitWhileFull();

        queue[head] = b;
        head = ( head + 1 ) % capacity;
        size++;

        notifyAll(); // let any waiting threads know about change
    }


    // TODO for now I need just 1 byte, but maybe worth extending to n bytes
    public synchronized int remove(byte bytes[])
            throws InterruptedException {

        byte buf[]=new byte[1];
        int len=1;

        if(isEmpty()) return 0;

        buf[0] = queue[tail];
        tail = ( tail + 1 ) % capacity;
        size--;

        System.arraycopy(buf, 0, bytes, 0, len);
        notifyAll(); // let any waiting threads know about change

        return len;
    }

    public synchronized byte[] removeAll() {
        // For efficiency, the bytes are copied in blocks
        // instead of one at a time.

        if ( isEmpty() ) {
            // Nothing to remove, return a zero-length
            // array and do not bother with notification
            // since nothing was removed.
            return new byte[0];
        }

        // based on the current size
        byte[] list = new byte[size];

        // copy in the block from tail to the end
        int distToEnd = capacity - tail;
        int copyLen = Math.min(size, distToEnd);
        System.arraycopy(queue, tail, list, 0, copyLen);

        // If data wraps around, copy the remaining data
        // from the front of the array.
        if ( size > copyLen ) {
            System.arraycopy(
                    queue, 0, list, copyLen, size - copyLen);
        }

        tail = ( tail + size ) % capacity;
        size = 0; // everything has been removed

        // Signal any and all waiting threads that
        // something has changed.
        notifyAll();

        return list;
    }

    public synchronized byte[] removeAtLeastOne()
            throws InterruptedException {

        waitWhileEmpty(); // wait for a least one to be in FIFO
        return removeAll();
    }

    public synchronized boolean waitUntilEmpty(long msTimeout)
            throws InterruptedException {

        if ( msTimeout == 0L ) {
            waitUntilEmpty();  // use other method
            return true;
        }

        // wait only for the specified amount of time
        long endTime = System.currentTimeMillis() + msTimeout;
        long msRemaining = msTimeout;

        while ( !isEmpty() && ( msRemaining > 0L ) ) {
            wait(msRemaining);
            msRemaining = endTime - System.currentTimeMillis();
        }

        // May have timed out, or may have met condition,
        // calc return value.
        return isEmpty();
    }

    public synchronized void waitUntilEmpty()
            throws InterruptedException {

        while ( !isEmpty() ) {
            wait();
        }
    }

    public synchronized void waitWhileEmpty()
            throws InterruptedException {

        while ( isEmpty() ) {
            wait();
        }
    }

    public synchronized void waitUntilFull()
            throws InterruptedException {

        while ( !isFull() ) {
            wait();
        }
    }

    public synchronized void waitWhileFull()
            throws InterruptedException {

        while ( isFull() ) {
            wait();
        }
    }
}