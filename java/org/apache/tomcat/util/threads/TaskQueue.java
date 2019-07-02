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

import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 专门为线程池设计的任务队列。
 */
public class TaskQueue extends LinkedBlockingQueue<Runnable> {

    private static final long serialVersionUID = 1L;

    /**
     * tomcat定制线程池
     */
    private volatile ThreadPoolExecutor parent = null;

    /**
     * 任务队列剩余容量
     */
    private Integer forcedRemainingCapacity = null;


    public void setForcedRemainingCapacity(Integer forcedRemainingCapacity) {
        this.forcedRemainingCapacity = forcedRemainingCapacity;
    }

    public void setParent(ThreadPoolExecutor tp) {
        parent = tp;
    }




    public TaskQueue() {
        super();
    }

    public TaskQueue(int capacity) {
        super(capacity);
    }

    public TaskQueue(Collection<? extends Runnable> c) {
        super(c);
    }



    /**
     *  检查线程池状态为RUNNING时，将任务添加到任务队列尾部。
     *  如果队列满了会阻塞
     */
    public boolean force(Runnable o) {
        if ( parent==null || parent.isShutdown() ) throw new RejectedExecutionException("Executor not running, can't force a command into the queue");
        return super.offer(o); //forces the item onto the queue, to be used if the task is rejected
    }

    /**
     *   检查线程池状态为RUNNING时，将任务添加到任务队列尾部。
     *   如果队列满了会超时阻塞
     */
    public boolean force(Runnable o, long timeout, TimeUnit unit) throws InterruptedException {
        if ( parent==null || parent.isShutdown() ) throw new RejectedExecutionException("Executor not running, can't force a command into the queue");
        return super.offer(o,timeout,unit); //forces the item onto the queue, to be used if the task is rejected
    }

    /**
     * 将任务添加到任务队列尾部。此方法的重点在于何时执行,以及返回false代表的意义
     *
     * 线程池执行任务策略如下
     *
     * 1 线程池中work< corePoolSize 会创建一个新的work直接执行
     *
     * 2 corePoolSize< work< maximumPoolSize 会调用等待队列offer方法（当前方法）
     *   如果成功则将任务添加到任务队列，执行完毕，如果失败执行操作 3
     *
     * 3 创建一个临时work去执行当前任务
     *
     * 那么定制重写offer意义在于可以控制线程池在2步骤情况下何时进入步骤3
     *
     *
     */
    @Override
    public boolean offer(Runnable o) {

        if (parent==null) return super.offer(o);

        /** 如果线程数已经到了最大值，不能创建新线程了，只能把任务添加到任务队列。 **/
        if (parent.getPoolSize() == parent.getMaximumPoolSize()) return super.offer(o);

        /** 执行到这里，表明当前线程数大于核心线程数，并且小于最大线程数。
            表明是可以创建新线程的，那到底要不要创建呢？分两种情况：
         **/

         /**1. 如果已提交的任务数小于当前线程数，表示还有空闲线程，无需创建新线程 **/
        if (parent.getSubmittedCount()<=(parent.getPoolSize())) return super.offer(o);

         /**2. 如果已提交的任务数大于当前线程数，线程不够用了，返回 false 去创建新线程 **/
        if (parent.getPoolSize()<parent.getMaximumPoolSize()) return false;

         /** 默认情况下总是把任务添加到任务队列 **/
        return super.offer(o);
    }


    /**
     * 从任务队列中获取任务，同时判断是否需要抛出异常停止当前线程（当context停止时会触发此动作）
     *  * 对
     */
    @Override
    public Runnable poll(long timeout, TimeUnit unit)
            throws InterruptedException {
        Runnable runnable = super.poll(timeout, unit);
        if (runnable == null && parent != null) {
            // the poll timed out, it gives an opportunity to stop the current
            // thread if needed to avoid memory leaks.
            parent.stopCurrentThreadIfNeeded();
        }
        return runnable;
    }

    /**
     * 从任务队列中删除队列首部任务，同时判断如果当前线程（work）线程的创建时间< 所在容器的停止时间则需要停止work的工作线程（抛出异常）
     * @return
     * @throws InterruptedException
     */
    @Override
    public Runnable take() throws InterruptedException {
        if (parent != null && parent.currentThreadShouldBeStopped()) {
            return poll(parent.getKeepAliveTime(TimeUnit.MILLISECONDS),
                    TimeUnit.MILLISECONDS);
            // yes, this may return null (in case of timeout) which normally
            // does not occur with take()
            // but the ThreadPoolExecutor implementation allows this
        }
        return super.take();
    }

    /**
     * 返回任务队列剩余容量
     */
    @Override
    public int remainingCapacity() {
        if (forcedRemainingCapacity != null) {
            // ThreadPoolExecutor.setCorePoolSize checks that
            // remainingCapacity==0 to allow to interrupt idle threads
            // I don't see why, but this hack allows to conform to this
            // "requirement"
            return forcedRemainingCapacity.intValue();
        }
        return super.remainingCapacity();
    }



}
