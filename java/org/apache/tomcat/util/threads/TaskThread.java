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

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 *
 *
 */
public class TaskThread extends Thread {

    private static final Log log = LogFactory.getLog(TaskThread.class);

    /**
     * 任务创建时间
     */
    private final long creationTime;

    /**
     * 创建一个任务线程
     * @param group  线程分组
     * @param target  线程执行业务
     * @param name   线程任务名
     */
    public TaskThread(ThreadGroup group, Runnable target, String name) {
        super(group, new WrappingRunnable(target), name);
        this.creationTime = System.currentTimeMillis();
    }

    /**
     * 创建一个任务线程
     * @param group     线程分组
     * @param target    线程执行业务
     * @param name      线程任务名
     * @param stackSize 新线程所需的堆栈大小
     */
    public TaskThread(ThreadGroup group, Runnable target, String name,
            long stackSize) {
        super(group, new WrappingRunnable(target), name, stackSize);
        this.creationTime = System.currentTimeMillis();
    }

    /**
     * 返回任务创建时间
     */
    public final long getCreationTime() {
        return creationTime;
    }

    /**
     * 任务执行Runnable
     */
    private static class WrappingRunnable implements Runnable {
        private Runnable wrappedRunnable;
        WrappingRunnable(Runnable wrappedRunnable) {
            this.wrappedRunnable = wrappedRunnable;
        }
        @Override
        public void run() {
            try {
                wrappedRunnable.run();
            } catch(StopPooledThreadException exc) {
                //expected : we just swallow the exception to avoid disturbing
                //debuggers like eclipse's
                log.debug("Thread exiting on purpose", exc);
            }
        }

    }

}
