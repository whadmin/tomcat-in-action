/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.util.threads;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 *
 * Tomcat作为web服务器，对于每个客户端的请求将给予处理响应，但对于一台机器而言，访问请求的总流量有高峰期且服务器有物理极限，
 * 为了保证web服务器不被冲垮我们需要采取一些措施进行保护预防，需要稍微说明的此处的流量更多的是指套接字的连接数，通过控制套接字连接个数来控制流量。其中一种有效的方法就是采取流量控制，
 * 它就像在流量的入口增加了一道闸门，闸门的大小决定了流量的大小，一旦达到最大流量将关闭闸门停止接收直到有空闲通道。limit使用AQS实现这样一个流量控制器
 */
public class LimitLatch {

    private static final Log log = LogFactory.getLog(LimitLatch.class);

    private class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 1L;

        public Sync() {
        }

        /**
         * 重写AQS 获取共享式同步状态的方法
         * 当连接数限制器开打，且新连接没有草果限制返回 1 表示连接可以创建，否则返回-1
         */
        @Override
        protected int tryAcquireShared(int ignored) {
            long newCount = count.incrementAndGet();
            if (!released && newCount > limit) {
                // Limit exceeded
                count.decrementAndGet();
                return -1;
            } else {
                return 1;
            }
        }

        /**
         * 重写AQS 释放共享式同步状态的方法
         * 当前连接数量-1，返回true,返回true会释放在同步队列的节点中的线程去竞争同步状态，失败则阻塞，成功则创建新连接，
         * 创建连接完成后，再次调用当前方法迭代。
         */
        @Override
        protected boolean tryReleaseShared(int arg) {
            count.decrementAndGet();
            return true;
        }
    }

    /**
     * 同步器
     */
    private final Sync sync;
    /**
     * 当前连接数量
     */
    private final AtomicLong count;
    /**
     * 限制的连接数量
     */
    private volatile long limit;
    /**
     * 是否打开连接数限制器 false表示默认打开
     */
    private volatile boolean released = false;

    /**
     * 创建一个连接控制器
     */
    public LimitLatch(long limit) {
        this.limit = limit;
        this.count = new AtomicLong(0);
        this.sync = new Sync();
    }


    public long getCount() {
        return count.get();
    }


    public long getLimit() {
        return limit;
    }


    public void setLimit(long limit) {
        this.limit = limit;
    }


    /**
     * 尝试获取共享式同步状态，失败则加入同步队列，同时阻塞
     */
    public void countUpOrAwait() throws InterruptedException {
        if (log.isDebugEnabled()) {
            log.debug("Counting up["+Thread.currentThread().getName()+"] latch="+getCount());
        }
        sync.acquireSharedInterruptibly(1);
    }

    /**
     * 释放共享式同步状态，
     */
    public long countDown() {
        sync.releaseShared(0);
        long result = getCount();
        if (log.isDebugEnabled()) {
            log.debug("Counting down[" + Thread.currentThread().getName() + "] latch=" + result);
        }
        return result;
    }

    /**
     * 关闭控制连接器，释放同步状态，
     */
    public boolean releaseAll() {
        released = true;
        return sync.releaseShared(0);
    }

    /**
     * 重置控制连接器，设置连接数为0，同时开打
     */
    public void reset() {
        this.count.set(0);
        released = false;
    }


    /**
     * 返回同步队列中是否存在等待线程
     */
    public boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }


    /**
     * 返回同步队列中等待线程
     * @return
     */
    public Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }
}
