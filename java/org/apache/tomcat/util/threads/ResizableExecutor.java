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

import java.util.concurrent.Executor;

public interface ResizableExecutor extends Executor {

    /**
     * 返回线程池work数量
     */
    public int getPoolSize();

    /**
     * 返回最大线程数默认200个
     */
    public int getMaxThreads();

    /**
     * 返回正在执行任务work数量
     */
    public int getActiveCount();

    /**
     * 重置线程池corePoolSize，maximumPoolSize
     */
    public boolean resizePool(int corePoolSize, int maximumPoolSize);

    /**
     * 重置等待队列
     */
    public boolean resizeQueue(int capacity);

}
