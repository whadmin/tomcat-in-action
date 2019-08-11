/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.threads;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.tomcat.util.res.StringManager;

/**
 * 扩展功能1
 * Context停止动作会触发ThreadLocalLeakPreventionListener监听器响应，调用contextStopping()在线程池中记录Context停止时间。
 * 由于每个work线程中ThreadLocal中对象在线程没有销毁时是无法回收。而work线程可能一直阻塞导致退出，导致ThreadLocal中对象无法回收，内存泄漏
 * 因而需要在任务处理中扩展实现afterExecute，当context关闭时，抛出异常让线程对象退出。
 *
 * 扩展功能2
 * 在work中记录尚未完成的任务数量（由于使用链表的任务队列需要线程池自己记录）
 */
public class ThreadPoolExecutor extends java.util.concurrent.ThreadPoolExecutor {

    /**
     * 管理打印日志模板组件
     */
    protected static final StringManager sm = StringManager
            .getManager("org.apache.tomcat.util.threads.res");

    /**
     * 提交但尚未完成的任务数量。
     */
    private final AtomicInteger submittedCount = new AtomicInteger(0);


    /**
     * Context 停止时间
     */
    private final AtomicLong lastContextStoppedTime = new AtomicLong(0L);

    /**
     * Context 停止，所有work线程需要停止，用来记录最后一个work线程停止时间
     */
    private final AtomicLong lastTimeThreadKilledItself = new AtomicLong(0L);

    /**
     * Context 停止会触发其work线程的停止，为避免多个work同时停止，设置时间间隔，每一段时间停止一个，用来累加
     * lastTimeThreadKilledItself
     */
    private long threadRenewalDelay = Constants.DEFAULT_THREAD_RENEWAL_DELAY;



    public long getThreadRenewalDelay() {
        return threadRenewalDelay;
    }

    public void setThreadRenewalDelay(long threadRenewalDelay) {
        this.threadRenewalDelay = threadRenewalDelay;
    }

    public int getSubmittedCount() {
        return submittedCount.get();
    }


    /**
     * 创建线程池，使用默认threadFactory
     * 初始化线程池时直接创建corePoolSize个work
     */
    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
        prestartAllCoreThreads();
    }

    /**
     * 创建线程池
     * 初始化线程池时直接创建corePoolSize个work
     */
    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory,
            RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
        prestartAllCoreThreads();
    }

    /**
     * 创建线程池，使用RejectHandler拒绝策略，直接抛异常
     * 初始化线程池时直接创建corePoolSize个work
     */
    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, new RejectHandler());
        prestartAllCoreThreads();
    }

    /**
     * 创建线程池，使用默认threadFactory，使用RejectHandler拒绝策略，直接抛异常
     * 初始化线程池时直接创建corePoolSize个work
     */
    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, new RejectHandler());
        prestartAllCoreThreads();
    }



    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        /** 待完成任务 -1 **/
        submittedCount.decrementAndGet();
        /** 任务没有执行调用stopCurrentThreadIfNeeded() **/
        if (t == null) {
            stopCurrentThreadIfNeeded();
        }
    }

    /**
     * 判断是否需要抛出异常停止当前线程（当context停止时会触发此动作）
     */
    protected void stopCurrentThreadIfNeeded() {
        if (currentThreadShouldBeStopped()) {
            long lastTime = lastTimeThreadKilledItself.longValue();
            if (lastTime + threadRenewalDelay < System.currentTimeMillis()) {
                if (lastTimeThreadKilledItself.compareAndSet(lastTime,
                        System.currentTimeMillis() + 1)) {

                    final String msg = sm.getString(
                                    "threadPoolExecutor.threadStoppedToAvoidPotentialLeak",
                                    Thread.currentThread().getName());

                    throw new StopPooledThreadException(msg);
                }
            }
        }
    }

    /**
     * 如果当前线程（work）线程的创建时间< 所在容器的停止时间则需要停止work的工作线程（抛出异常）
     */
    protected boolean currentThreadShouldBeStopped() {
        if (threadRenewalDelay >= 0
            && Thread.currentThread() instanceof TaskThread) {
            TaskThread currentTaskThread = (TaskThread) Thread.currentThread();
            if (currentTaskThread.getCreationTime() <
                    this.lastContextStoppedTime.longValue()) {
                return true;
            }
        }
        return false;
    }


    /**
     * 执行一个任务
     */
    @Override
    public void execute(Runnable command) {
        execute(command,0,TimeUnit.MILLISECONDS);
    }

    /**
     * 执行任务
     * @param command 任务
     * @param timeout 超时时间
     * @param unit 超时单位
     */
    public void execute(Runnable command, long timeout, TimeUnit unit) {
        /** 待处理任务数量+1 **/
        submittedCount.incrementAndGet();
        try {
            /** JDK线程池执行任务 **/
            super.execute(command);
        }
        /** 当线程池触发拒绝策略时捕获异常 **/
        catch (RejectedExecutionException rx) {
            /** 判断线程等待队列类型是否TaskQueue **/
            if (super.getQueue() instanceof TaskQueue) {
                final TaskQueue queue = (TaskQueue)super.getQueue();
                try {
                    if (!queue.force(command, timeout, unit)) {
                        submittedCount.decrementAndGet();
                        throw new RejectedExecutionException("Queue capacity is full.");
                    }
                } catch (InterruptedException x) {
                    submittedCount.decrementAndGet();
                    throw new RejectedExecutionException(x);
                }
            } else {
                submittedCount.decrementAndGet();
                throw rx;
            }
        }
    }

    /**
     * ThreadLocalLeakPreventionListener 作用时停止当前线程（work线程处理业务）避免当前线程ThreadLocal中数据
     * 因为线程一直运行而无法回收
     */
    public void contextStopping() {
        /** 设置context 停止时间 **/
        this.lastContextStoppedTime.set(System.currentTimeMillis());

        /** 获取线程池中核心work数量 **/
        int savedCorePoolSize = this.getCorePoolSize();
        TaskQueue taskQueue =
                getQueue() instanceof TaskQueue ? (TaskQueue) getQueue() : null;
        if (taskQueue != null) {
            // note by slaurent : quite oddly threadPoolExecutor.setCorePoolSize
            // checks that queue.remainingCapacity()==0. I did not understand
            // why, but to get the intended effect of waking up idle threads, I
            // temporarily fake this condition.
            taskQueue.setForcedRemainingCapacity(Integer.valueOf(0));
        }

        /** 设置线程池中核心work为0 **/
        this.setCorePoolSize(0);

        /** 设置线程池中任务队列剩余容量为无限大 **/
        if (taskQueue != null) {
            // ok, restore the state of the queue and pool
            taskQueue.setForcedRemainingCapacity(null);
        }
        /** 重置线程池中核心work数 **/
        this.setCorePoolSize(savedCorePoolSize);
    }

    private static class RejectHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r,
                java.util.concurrent.ThreadPoolExecutor executor) {
            throw new RejectedExecutionException();
        }

    }


}
