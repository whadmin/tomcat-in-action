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

package org.apache.catalina.core;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.catalina.Executor;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.tomcat.util.threads.ResizableExecutor;
import org.apache.tomcat.util.threads.TaskQueue;
import org.apache.tomcat.util.threads.TaskThreadFactory;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;

public class StandardThreadExecutor extends LifecycleMBeanBase
        implements Executor, ResizableExecutor {

    // ---------------------------------------------- Properties
    /**
     * 默认线程的优先级
     */
    protected int threadPriority = Thread.NORM_PRIORITY;

    /**
     * 守护线程
     */
    protected boolean daemon = true;

    /**
     * 线程名称的前缀
     */
    protected String namePrefix = "tomcat-exec-";

    /**
     * 最大线程数默认200个
     */
    protected int maxThreads = 200;

    /**
     * 最小空闲线程25个
     */
    protected int minSpareThreads = 25;

    /**
     * 超时时间为6000
     */
    protected int maxIdleTime = 60000;

    /**
     * 线程池容器
     */
    protected ThreadPoolExecutor executor = null;

    /**
     * 线程池的名称
     */
    protected String name;

    /**
     * 是否提前启动线程
     */
    protected boolean prestartminSpareThreads = false;

    /**
     * 队列最大大小
     */
    protected int maxQueueSize = Integer.MAX_VALUE;

    /**
     *为了避免在上下文停止之后，所有的线程在同一时间段被更新，所以进行线程的延迟操作
     */
    protected long threadRenewalDelay =
        org.apache.tomcat.util.threads.Constants.DEFAULT_THREAD_RENEWAL_DELAY;

    /**
     * 任务队列
     */
    private TaskQueue taskqueue = null;


    public StandardThreadExecutor() {
    }



    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();
    }

    @Override
    protected void destroyInternal() throws LifecycleException {
        super.destroyInternal();
    }


    /**
     * 组件启动模板方法实现
     */
    @Override
    protected void startInternal() throws LifecycleException {
        /** 实例化任务队列，对JDK，LinkedBlockingQueue进行了封装
         *  由于是LinkedBlockingQueue且maxQueueSize=Integer.MAX_VALUE，表明
         * **/
        taskqueue = new TaskQueue(maxQueueSize);
        /** 实例化work线程工厂类,实现了JDK的ThreadFactory接口 **/
        TaskThreadFactory tf = new TaskThreadFactory(namePrefix,daemon,getThreadPriority());
        /** 实例化tomcat线程池，对JDK线程池进行了封装 **/
        executor = new ThreadPoolExecutor(getMinSpareThreads(), getMaxThreads(), maxIdleTime, TimeUnit.MILLISECONDS,taskqueue, tf);
        executor.setThreadRenewalDelay(threadRenewalDelay);
        /** 初始化线程池中work线程达到minSpareThreads **/
        if (prestartminSpareThreads) {
            executor.prestartAllCoreThreads();
        }
        /** 将线程池设置给taskqueue **/
        taskqueue.setParent(executor);
        /** 设置当前组件状态  STARTING **/
        setState(LifecycleState.STARTING);
    }


    /**
     * 组件停止模板方法实现
     */
    @Override
    protected void stopInternal() throws LifecycleException {
        /** 设置当前组件状态  STOPPING **/
        setState(LifecycleState.STOPPING);
        /** 关闭线程池 **/
        if ( executor != null ) executor.shutdownNow();
        /** 重置属性 **/
        executor = null;
        taskqueue = null;
    }


    /**
     * 执行任务，有超时设置
     */
    @Override
    public void execute(Runnable command, long timeout, TimeUnit unit) {
        if ( executor != null ) {
            executor.execute(command,timeout,unit);
        } else {
            throw new IllegalStateException("StandardThreadExecutor not started.");
        }
    }


    /**
     * 执行任务
     * @param command
     */
    @Override
    public void execute(Runnable command) {
        if ( executor != null ) {
            try {
                executor.execute(command);
            } catch (RejectedExecutionException rx) {
                //there could have been contention around the queue
                if ( !( (TaskQueue) executor.getQueue()).force(command) ) throw new RejectedExecutionException("Work queue full.");
            }
        } else throw new IllegalStateException("StandardThreadPool not started.");
    }

    public void contextStopping() {
        if (executor != null) {
            executor.contextStopping();
        }
    }

    public int getThreadPriority() {
        return threadPriority;
    }

    public boolean isDaemon() {

        return daemon;
    }

    public String getNamePrefix() {
        return namePrefix;
    }

    public int getMaxIdleTime() {
        return maxIdleTime;
    }

    @Override
    public int getMaxThreads() {
        return maxThreads;
    }

    public int getMinSpareThreads() {
        return minSpareThreads;
    }

    @Override
    public String getName() {
        return name;
    }

    public boolean isPrestartminSpareThreads() {

        return prestartminSpareThreads;
    }
    public void setThreadPriority(int threadPriority) {
        this.threadPriority = threadPriority;
    }

    public void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    public void setNamePrefix(String namePrefix) {
        this.namePrefix = namePrefix;
    }

    public void setMaxIdleTime(int maxIdleTime) {
        this.maxIdleTime = maxIdleTime;
        if (executor != null) {
            executor.setKeepAliveTime(maxIdleTime, TimeUnit.MILLISECONDS);
        }
    }

    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
        if (executor != null) {
            executor.setMaximumPoolSize(maxThreads);
        }
    }

    public void setMinSpareThreads(int minSpareThreads) {
        this.minSpareThreads = minSpareThreads;
        if (executor != null) {
            executor.setCorePoolSize(minSpareThreads);
        }
    }

    public void setPrestartminSpareThreads(boolean prestartminSpareThreads) {
        this.prestartminSpareThreads = prestartminSpareThreads;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setMaxQueueSize(int size) {
        this.maxQueueSize = size;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public long getThreadRenewalDelay() {
        return threadRenewalDelay;
    }

    public void setThreadRenewalDelay(long threadRenewalDelay) {
        this.threadRenewalDelay = threadRenewalDelay;
        if (executor != null) {
            executor.setThreadRenewalDelay(threadRenewalDelay);
        }
    }

    // Statistics from the thread pool
    @Override
    public int getActiveCount() {
        return (executor != null) ? executor.getActiveCount() : 0;
    }

    public long getCompletedTaskCount() {
        return (executor != null) ? executor.getCompletedTaskCount() : 0;
    }

    public int getCorePoolSize() {
        return (executor != null) ? executor.getCorePoolSize() : 0;
    }

    public int getLargestPoolSize() {
        return (executor != null) ? executor.getLargestPoolSize() : 0;
    }

    @Override
    public int getPoolSize() {
        return (executor != null) ? executor.getPoolSize() : 0;
    }

    public int getQueueSize() {
        return (executor != null) ? executor.getQueue().size() : -1;
    }


    @Override
    public boolean resizePool(int corePoolSize, int maximumPoolSize) {
        if (executor == null)
            return false;

        executor.setCorePoolSize(corePoolSize);
        executor.setMaximumPoolSize(maximumPoolSize);
        return true;
    }


    @Override
    public boolean resizeQueue(int capacity) {
        return false;
    }


    @Override
    protected String getDomainInternal() {
        // No way to navigate to Engine. Needs to have domain set.
        return null;
    }

    @Override
    protected String getObjectNameKeyProperties() {
        StringBuilder name = new StringBuilder("type=Executor,name=");
        name.append(getName());
        return name.toString();
    }
}
