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

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.management.ObjectName;

import org.apache.catalina.AccessLog;
import org.apache.catalina.Cluster;
import org.apache.catalina.Container;
import org.apache.catalina.ContainerEvent;
import org.apache.catalina.ContainerListener;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Loader;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Realm;
import org.apache.catalina.Valve;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.ContextName;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.MultiThrowable;
import org.apache.tomcat.util.res.StringManager;



public abstract class ContainerBase extends LifecycleMBeanBase
        implements Container {

    private static final Log log = LogFactory.getLog(ContainerBase.class);

    protected class PrivilegedAddChild implements PrivilegedAction<Void> {

        private final Container child;

        PrivilegedAddChild(Container child) {
            this.child = child;
        }

        @Override
        public Void run() {
            addChildInternal(child);
            return null;
        }
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * 当前容器的名称
     */
    protected String name = null;

    /**
     * 当前容器组件的父容器组件
     */
    protected Container parent = null;

    /**
     * 父类加载器
     */
    protected ClassLoader parentClassLoader = null;


    /**
     * 存储子容器的Map,key表示容器的名称,value表示容器组件对象
     */
    protected final HashMap<String, Container> children = new HashMap<>();

    /**
     * 容器事件监听器
     */
    protected final List<ContainerListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * log
     */
    protected Log logger = null;

    /**
     * 关联的log名称
     */
    protected String logName = null;


    //关联子组件
    /**
     * 容器组件集群对象cluster
     */
    protected Cluster cluster = null;

    /**
     * 集群对象CLuster读写锁
     */
    private final ReadWriteLock clusterLock = new ReentrantReadWriteLock();

    /**
     * 当前容器组件对应的的Realm组件
     */
    private volatile Realm realm = null;

    /**
     * 当前容器组件对应的Realm组件读写锁
     */
    private final ReadWriteLock realmLock = new ReentrantReadWriteLock();

    /**
     * 当前容器组件对应的pipeline组件
     */
    protected final Pipeline pipeline = new StandardPipeline(this);

    /**
     * 当前容器组件对应的AccessLog组件
     */
    protected volatile AccessLog accessLog = null;

    /**
     * 获取AccessLog组件时
     * 如果accessLogScanComplete为false
     * 对AccessLog组件初始化(扫描pipeline组件中类型为AccessLog的Value，添加到AccessLog组件)
     */
    private volatile boolean accessLogScanComplete = false;


    //工具类
    /**
     * 错误日志管理器
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


    /**
     * 属性变更处理器
     */
    protected final PropertyChangeSupport support =
            new PropertyChangeSupport(this);


    //定时任务相关属性
    /**
     * 定时任务处理线程对象
     */
    private Thread thread = null;

    /**
     * 标识定时任务处理线程退出标识
     */
    private volatile boolean threadDone = false;

    /**
     * 当前组件的定执行backgroundProcessor间隔间
     */
    protected int backgroundProcessorDelay = -1;



     //定时任务相关属性
    /**
     * 当前容器添加子容器组件时，是否自动启动子容器
     */
    protected boolean startChildren = true;


    /**
     * 处理子容器启动关闭线程池核心线程数。
     */
    private int startStopThreads = 1;


    /**
     * 处理子容器启动关闭线程池
     */
    protected ThreadPoolExecutor startStopExecutor;


    // ------------------------------------------------------------- Properties

    @Override
    public int getStartStopThreads() {
        return startStopThreads;
    }


    private int getStartStopThreadsInternal() {
        int result = getStartStopThreads();

        if (result > 0) {
            return result;
        }
        result = Runtime.getRuntime().availableProcessors() + result;
        if (result < 1) {
            result = 1;
        }
        return result;
    }


    @Override
    public void setStartStopThreads(int startStopThreads) {
        this.startStopThreads = startStopThreads;

        // Use local copies to ensure thread safety
        ThreadPoolExecutor executor = startStopExecutor;
        if (executor != null) {
            int newThreads = getStartStopThreadsInternal();
            executor.setMaximumPoolSize(newThreads);
            executor.setCorePoolSize(newThreads);
        }
    }


    @Override
    public int getBackgroundProcessorDelay() {
        return backgroundProcessorDelay;
    }


    @Override
    public void setBackgroundProcessorDelay(int delay) {
        backgroundProcessorDelay = delay;
    }


    @Override
    public Log getLogger() {

        if (logger != null)
            return (logger);
        logger = LogFactory.getLog(getLogName());
        return (logger);

    }


    @Override
    public String getLogName() {
        if (logName != null) {
            return logName;
        }
        String loggerName = null;
        Container current = this;
        while (current != null) {
            String name = current.getName();
            if ((name == null) || (name.equals(""))) {
                name = "/";
            } else if (name.startsWith("##")) {
                name = "/" + name;
            }
            loggerName = "[" + name + "]"
                + ((loggerName != null) ? ("." + loggerName) : "");
            current = current.getParent();
        }
        logName = ContainerBase.class.getName() + "." + loggerName;
        return logName;

    }


    @Override
    public Cluster getCluster() {
        Lock readLock = clusterLock.readLock();
        readLock.lock();
        try {
            if (cluster != null)
                return cluster;

            if (parent != null)
                return parent.getCluster();

            return null;
        } finally {
            readLock.unlock();
        }
    }


    protected Cluster getClusterInternal() {
        Lock readLock = clusterLock.readLock();
        readLock.lock();
        try {
            return cluster;
        } finally {
            readLock.unlock();
        }
    }


    @Override
    public void setCluster(Cluster cluster) {

        Cluster oldCluster = null;
        Lock writeLock = clusterLock.writeLock();
        writeLock.lock();
        try {
            /**  获取当前组件原始Cluster组件对象 **/
            oldCluster = this.cluster;
            if (oldCluster == cluster)
                return;
            this.cluster = cluster;

            /**  如果Cluster组件还在运行则停止Cluster组件 **/
            if (getState().isAvailable() && (oldCluster != null) &&
                (oldCluster instanceof Lifecycle)) {
                try {
                    ((Lifecycle) oldCluster).stop();
                } catch (LifecycleException e) {
                    log.error("ContainerBase.setCluster: stop: ", e);
                }
            }

            /** 将设置cluster组件和当前对象反相关联 **/
            if (cluster != null)
                cluster.setContainer(this);

            /** 如果当前组件处于运行状态，启动设置cluster组件 **/
            if (getState().isAvailable() && (cluster != null) &&
                (cluster instanceof Lifecycle)) {
                try {
                    ((Lifecycle) cluster).start();
                } catch (LifecycleException e) {
                    log.error("ContainerBase.setCluster: start: ", e);
                }
            }
        } finally {
            writeLock.unlock();
        }

        /** 触发属性变更 **/
        support.firePropertyChange("cluster", oldCluster, cluster);
    }


    @Override
    public String getName() {
        return (name);
    }


    @Override
    public void setName(String name) {
        if (name == null) {
            throw new IllegalArgumentException(sm.getString("containerBase.nullName"));
        }
        String oldName = this.name;
        this.name = name;
        support.firePropertyChange("name", oldName, this.name);
    }


    public boolean getStartChildren() {
        return (startChildren);
    }


    public void setStartChildren(boolean startChildren) {
        boolean oldStartChildren = this.startChildren;
        this.startChildren = startChildren;
        support.firePropertyChange("startChildren", oldStartChildren, this.startChildren);
    }


    @Override
    public Container getParent() {
        return (parent);
    }


    @Override
    public void setParent(Container container) {
        Container oldParent = this.parent;
        this.parent = container;
        support.firePropertyChange("parent", oldParent, this.parent);
    }


    @Override
    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null)
            return (parentClassLoader);
        if (parent != null) {
            return (parent.getParentClassLoader());
        }
        return (ClassLoader.getSystemClassLoader());

    }


    @Override
    public void setParentClassLoader(ClassLoader parent) {
        ClassLoader oldParentClassLoader = this.parentClassLoader;
        this.parentClassLoader = parent;
        support.firePropertyChange("parentClassLoader", oldParentClassLoader,
                                   this.parentClassLoader);

    }


    @Override
    public Pipeline getPipeline() {
        return (this.pipeline);
    }


    @Override
    public Realm getRealm() {
        Lock l = realmLock.readLock();
        l.lock();
        try {
            if (realm != null)
                return (realm);
            if (parent != null)
                return (parent.getRealm());
            return null;
        } finally {
            l.unlock();
        }
    }


    protected Realm getRealmInternal() {
        Lock l = realmLock.readLock();
        l.lock();
        try {
            return realm;
        } finally {
            l.unlock();
        }
    }


    @Override
    public void setRealm(Realm realm) {

        Lock l = realmLock.writeLock();
        l.lock();
        try {
            /**  获取当前组件原始Realm组件对象 **/
            Realm oldRealm = this.realm;
            if (oldRealm == realm)
                return;
            this.realm = realm;

            /**  如果Realm组件还在运行则停止Cluster组件 **/
            if (getState().isAvailable() && (oldRealm != null) &&
                (oldRealm instanceof Lifecycle)) {
                try {
                    ((Lifecycle) oldRealm).stop();
                } catch (LifecycleException e) {
                    log.error("ContainerBase.setRealm: stop: ", e);
                }
            }

            /** 将设置Realm组件和当前对象反相关联 **/
            if (realm != null)
                realm.setContainer(this);

            /** 如果当前组件处于运行状态，启动设置Realm组件 **/
            if (getState().isAvailable() && (realm != null) &&
                (realm instanceof Lifecycle)) {
                try {
                    ((Lifecycle) realm).start();
                } catch (LifecycleException e) {
                    log.error("ContainerBase.setRealm: start: ", e);
                }
            }

            /** 触发属性变更 **/
            support.firePropertyChange("realm", oldRealm, this.realm);
        } finally {
            l.unlock();
        }

    }


    // ------------------------------------------------------ Container Methods


    /**
     * 为当前容器组件添加子容器组件
     */
    @Override
    public void addChild(Container child) {
        if (Globals.IS_SECURITY_ENABLED) {
            PrivilegedAction<Void> dp =
                new PrivilegedAddChild(child);
            AccessController.doPrivileged(dp);
        } else {
            addChildInternal(child);
        }
    }

    /**
     * 为当前容器组件添加子容器组件内部实现
     */
    private void addChildInternal(Container child) {

        if( log.isDebugEnabled() ){
            log.debug("Add child " + child + " " + this);
        }

        /** 将添加的子容器和当前对象反相关联 **/
        synchronized(children) {
            if (children.get(child.getName()) != null){
                throw new IllegalArgumentException("addChild:  Child name '" +
                        child.getName() +
                        "' is not unique");
            }
            child.setParent(this);  // May throw IAE
            children.put(child.getName(), child);
        }

        /** 如果当前容器状态为运行，则启动添加的子容器 **/
        try {
            if ((getState().isAvailable() ||
                    LifecycleState.STARTING_PREP.equals(getState())) &&
                    startChildren) {
                child.start();
            }
        } catch (LifecycleException e) {
            log.error("ContainerBase.addChild: start: ", e);
            throw new IllegalStateException("ContainerBase.addChild: start: " + e);
        } finally {
            /** 触发ContainerEvent事件ADD_CHILD_EVENT **/
            fireContainerEvent(ADD_CHILD_EVENT, child);
        }
    }


    /**
     * 添加容器事件监听器
     */
    @Override
    public void addContainerListener(ContainerListener listener) {
        listeners.add(listener);
    }


    /**
     * 删除容器事件监听器
     */
    @Override
    public void removeContainerListener(ContainerListener listener) {
        listeners.remove(listener);
    }


    /**
     * 添加属性变更监听器
     */
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }


    /**
     * 删除属性变更监听器
     */
    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }


    /**
     * 查找指定名称的子容器
     */
    @Override
    public Container findChild(String name) {
        if (name == null) {
            return null;
        }
        synchronized (children) {
            return children.get(name);
        }
    }


    /**
     * 获取所有子容器组件
     */
    @Override
    public Container[] findChildren() {
        synchronized (children) {
            Container results[] = new Container[children.size()];
            return children.values().toArray(results);
        }
    }


    /**
     * 获取所有容器事件监听器
     */
    @Override
    public ContainerListener[] findContainerListeners() {
        ContainerListener[] results =
            new ContainerListener[0];
        return listeners.toArray(results);
    }


    /**
     * 删除子容器组件
     */
    @Override
    public void removeChild(Container child) {

        if (child == null) {
            return;
        }

        /** 停止要删除的子容器组件 **/
        try {
            if (child.getState().isAvailable()) {
                child.stop();
            }
        } catch (LifecycleException e) {
            log.error("ContainerBase.removeChild: stop: ", e);
        }

        /** 如果删除的子容器组件停止后状态不等于LifecycleState.DESTROYING，则尝试销毁要删除的子容器组件 **/
        try {
            if (!LifecycleState.DESTROYING.equals(child.getState())) {
                child.destroy();
            }
        } catch (LifecycleException e) {
            log.error("ContainerBase.removeChild: destroy: ", e);
        }

        /** 将删除的子容器组件从当前容器的children Map类型的属性中删除 **/
        synchronized(children) {
            if (children.get(child.getName()) == null)
                return;
            children.remove(child.getName());
        }

        /** 触发ContainerEvent事件REMOVE_CHILD_EVENT **/
        fireContainerEvent(REMOVE_CHILD_EVENT, child);
    }


    /**
     * 组件初始化模板实现
     */
    @Override
    protected void initInternal() throws LifecycleException {
        /** 实例化处理子容器启动关闭线程池  **/
        BlockingQueue<Runnable> startStopQueue = new LinkedBlockingQueue<>();
        startStopExecutor = new ThreadPoolExecutor(
                getStartStopThreadsInternal(),
                getStartStopThreadsInternal(), 10, TimeUnit.SECONDS,
                startStopQueue,
                new StartStopThreadFactory(getName() + "-startStop-"));
        startStopExecutor.allowCoreThreadTimeOut(true);

        super.initInternal();
    }


    /**
     * 组件启动模板实现
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        logger = null;
        /** 初始化日志组件 **/
        getLogger();

        /** 启动Cluster组件**/
        Cluster cluster = getClusterInternal();
        if (cluster instanceof Lifecycle) {
            ((Lifecycle) cluster).start();
        }

        /** 启动Realm组件**/
        Realm realm = getRealmInternal();
        if (realm instanceof Lifecycle) {
            ((Lifecycle) realm).start();
        }

        /** 使用线程池异步处理子容器启动 **/
        Container children[] = findChildren();
        List<Future<Void>> results = new ArrayList<>();
        for (int i = 0; i < children.length; i++) {
            results.add(startStopExecutor.submit(new StartChild(children[i])));
        }

        MultiThrowable multiThrowable = null;

        /** 等待所有子容器启动完毕 **/
        for (Future<Void> result : results) {
            try {
                result.get();
            } catch (Throwable e) {
                log.error(sm.getString("containerBase.threadedStartFailed"), e);
                if (multiThrowable == null) {
                    multiThrowable = new MultiThrowable();
                }
                multiThrowable.add(e);
            }

        }
        if (multiThrowable != null) {
            throw new LifecycleException(sm.getString("containerBase.threadedStartFailed"),
                    multiThrowable.getThrowable());
        }

        /** 启动pipeline组件**/
        if (pipeline instanceof Lifecycle) {
            ((Lifecycle) pipeline).start();
        }

        /** 设置当前容器的状态LifecycleState.STARTING **/
        setState(LifecycleState.STARTING);

        /** 启动线程，定时处理当前容器的所有子容器内backgroundProcess方法 **/
        threadStart();
    }


    /**
     * 组件停止模板实现
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        /** 关闭处理定时任务线程 **/
        threadStop();

        /** 设置当前容器的状态LifecycleState.STOPPING **/
        setState(LifecycleState.STOPPING);


        /** 停止pipeline组件**/
        if (pipeline instanceof Lifecycle &&
                ((Lifecycle) pipeline).getState().isAvailable()) {
            ((Lifecycle) pipeline).stop();
        }

        /** 使用线程池异步处理子容器关闭 **/
        Container children[] = findChildren();
        List<Future<Void>> results = new ArrayList<>();
        for (int i = 0; i < children.length; i++) {
            results.add(startStopExecutor.submit(new StopChild(children[i])));
        }

        /** 等待所有子容器关闭完毕 **/
        boolean fail = false;
        for (Future<Void> result : results) {
            try {
                result.get();
            } catch (Exception e) {
                log.error(sm.getString("containerBase.threadedStopFailed"), e);
                fail = true;
            }
        }
        if (fail) {
            throw new LifecycleException(
                    sm.getString("containerBase.threadedStopFailed"));
        }

        /** 关闭Realm组件**/
        Realm realm = getRealmInternal();
        if (realm instanceof Lifecycle) {
            ((Lifecycle) realm).stop();
        }

        /** 关闭Cluster组件**/
        Cluster cluster = getClusterInternal();
        if (cluster instanceof Lifecycle) {
            ((Lifecycle) cluster).stop();
        }
    }


    /**
     * 组件销毁模板实现
     */
    @Override
    protected void destroyInternal() throws LifecycleException {

        /** 销毁Realm组件**/
        Realm realm = getRealmInternal();
        if (realm instanceof Lifecycle) {
            ((Lifecycle) realm).destroy();
        }

        /** 销毁cluster组件**/
        Cluster cluster = getClusterInternal();
        if (cluster instanceof Lifecycle) {
            ((Lifecycle) cluster).destroy();
        }

        /** 销毁cluster组件**/
        if (pipeline instanceof Lifecycle) {
            ((Lifecycle) pipeline).destroy();
        }

        /** 清空所有子容器 **/
        for (Container child : findChildren()) {
            removeChild(child);
        }

        /** 父容器和当前容器组件取消关联 **/
        if (parent != null) {
            parent.removeChild(this);
        }

        /** 关闭处理子容器启动关闭线程池 **/
        if (startStopExecutor != null) {
            startStopExecutor.shutdownNow();
        }

        super.destroyInternal();
    }


    /**
     * 使用AccessLog组件打印日志，
     * 如果当前组件不存在AccessLog组件则使用父类AccessLog组件
     */
    @Override
    public void logAccess(Request request, Response response, long time,
            boolean useDefault) {

        boolean logged = false;

        /** 使用AccessLog组件打印日志 **/
        if (getAccessLog() != null) {
            getAccessLog().log(request, response, time);
            logged = true;
        }

        /** 使用父容器AccessLog组件打印日志 **/
        if (getParent() != null) {
            getParent().logAccess(request, response, time, (useDefault && !logged));
        }
    }

    /**
     * 获取AccessLog组件时
     * 如果accessLogScanComplete为false
     * 对AccessLog组件初始化(扫描pipeline组件中类型为AccessLog的Value，添加到AccessLog组件)
     */
    @Override
    public AccessLog getAccessLog() {

        if (accessLogScanComplete) {
            return accessLog;
        }

        AccessLogAdapter adapter = null;
        Valve valves[] = getPipeline().getValves();
        for (Valve valve : valves) {
            if (valve instanceof AccessLog) {
                if (adapter == null) {
                    adapter = new AccessLogAdapter((AccessLog) valve);
                } else {
                    adapter.add((AccessLog) valve);
                }
            }
        }
        if (adapter != null) {
            accessLog = adapter;
        }
        accessLogScanComplete = true;
        return accessLog;
    }

    // ------------------------------------------------------- Pipeline Methods


    /**
     * 给pipeline组件添加一个Valve
     */
    public synchronized void addValve(Valve valve) {
        pipeline.addValve(valve);
    }


    /**
     * 容器默认定时任务处理逻辑
     */
    @Override
    public void backgroundProcess() {

        if (!getState().isAvailable())
            return;

        /** 调用Cluster组件backgroundProcess 方法 **/
        Cluster cluster = getClusterInternal();
        if (cluster != null) {
            try {
                cluster.backgroundProcess();
            } catch (Exception e) {
                log.warn(sm.getString("containerBase.backgroundProcess.cluster",
                        cluster), e);
            }
        }

        /** 调用Realm组件backgroundProcess 方法 **/
        Realm realm = getRealmInternal();
        if (realm != null) {
            try {
                realm.backgroundProcess();
            } catch (Exception e) {
                log.warn(sm.getString("containerBase.backgroundProcess.realm", realm), e);
            }
        }

        /** 调用pipeline组件中所有Value的backgroundProcess 方法 **/
        Valve current = pipeline.getFirst();
        while (current != null) {
            try {
                current.backgroundProcess();
            } catch (Exception e) {
                log.warn(sm.getString("containerBase.backgroundProcess.valve", current), e);
            }
            current = current.getNext();
        }
        fireLifecycleEvent(Lifecycle.PERIODIC_EVENT, null);
    }


    @Override
    public File getCatalinaBase() {
        if (parent == null) {
            return null;
        }
        return parent.getCatalinaBase();
    }


    @Override
    public File getCatalinaHome() {
        if (parent == null) {
            return null;
        }
        return parent.getCatalinaHome();
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * 处理容器事件
     */
    @Override
    public void fireContainerEvent(String type, Object data) {
        if (listeners.size() < 1){
            return;
        }
        ContainerEvent event = new ContainerEvent(this, type, data);
        // Note for each uses an iterator internally so this is safe
        for (ContainerListener listener : listeners) {
            listener.containerEvent(event);
        }
    }


    // jmx相关方法

    @Override
    protected String getDomainInternal() {
        Container p = this.getParent();
        if (p == null) {
            return null;
        } else {
            return p.getDomain();
        }
    }

    @Override
    public String getMBeanKeyProperties() {
        Container c = this;
        StringBuilder keyProperties = new StringBuilder();
        int containerCount = 0;

        // Work up container hierarchy, add a component to the name for
        // each container
        while (!(c instanceof Engine)) {
            if (c instanceof Wrapper) {
                keyProperties.insert(0, ",servlet=");
                keyProperties.insert(9, c.getName());
            } else if (c instanceof Context) {
                keyProperties.insert(0, ",context=");
                ContextName cn = new ContextName(c.getName(), false);
                keyProperties.insert(9,cn.getDisplayName());
            } else if (c instanceof Host) {
                keyProperties.insert(0, ",host=");
                keyProperties.insert(6, c.getName());
            } else if (c == null) {
                // May happen in unit testing and/or some embedding scenarios
                keyProperties.append(",container");
                keyProperties.append(containerCount++);
                keyProperties.append("=null");
                break;
            } else {
                // Should never happen...
                keyProperties.append(",container");
                keyProperties.append(containerCount++);
                keyProperties.append('=');
                keyProperties.append(c.getName());
            }
            c = c.getParent();
        }
        return keyProperties.toString();
    }


    public ObjectName[] getChildren() {
        List<ObjectName> names = new ArrayList<>(children.size());
        for (Container next : children.values()) {
            if (next instanceof ContainerBase) {
                names.add(((ContainerBase)next).getObjectName());
            }
        }
        return names.toArray(new ObjectName[names.size()]);
    }


    // -------------------- Background Thread --------------------

    /**
     * 启动线程，定时处理当前容器的所有子容器内backgroundProcess方法
     */
    protected void threadStart() {
        if (thread != null){
            return;
        }
        if (backgroundProcessorDelay <= 0){
            return;
        }
        threadDone = false;
        String threadName = "ContainerBackgroundProcessor[" + toString() + "]";
        thread = new Thread(new ContainerBackgroundProcessor(), threadName);
        thread.setDaemon(true);
        thread.start();
    }


    /**
     * 关闭处理定时任务线程
     */
    protected void threadStop() {

        if (thread == null){
            return;
        }

        threadDone = true;
        thread.interrupt();
        try {
            thread.join();
        } catch (InterruptedException e) {
        }
        thread = null;
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        Container parent = getParent();
        if (parent != null) {
            sb.append(parent.toString());
            sb.append('.');
        }
        sb.append(this.getClass().getSimpleName());
        sb.append('[');
        sb.append(getName());
        sb.append(']');
        return sb.toString();
    }


    // -------------------------------------- ContainerExecuteDelay Inner Class

    /**
     * 定时任务处理线程对象
     */
    protected class ContainerBackgroundProcessor implements Runnable {

        @Override
        public void run() {
            Throwable t = null;
            String unexpectedDeathMessage = sm.getString(
                    "containerBase.backgroundProcess.unexpectedThreadDeath",
                    Thread.currentThread().getName());
            try {
                /** 间隔backgroundProcessorDelay时间，调用processChildren函数 **/
                while (!threadDone) {
                    try {
                        Thread.sleep(backgroundProcessorDelay * 1000L);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                    if (!threadDone) {
                        processChildren(ContainerBase.this);
                    }
                }
            } catch (RuntimeException|Error e) {
                t = e;
                throw e;
            } finally {
                if (!threadDone) {
                    log.error(unexpectedDeathMessage, t);
                }
            }
        }

        /**
         * 调用当前容器以及子孙容器组件backgroundProcess方法
         */
        protected void processChildren(Container container) {
            ClassLoader originalClassLoader = null;

            try {
                if (container instanceof Context) {
                    Loader loader = ((Context) container).getLoader();
                    // Loader will be null for FailedContext instances
                    if (loader == null) {
                        return;
                    }

                    originalClassLoader = ((Context) container).bind(false, null);
                }
                container.backgroundProcess();
                Container[] children = container.findChildren();
                for (int i = 0; i < children.length; i++) {
                    if (children[i].getBackgroundProcessorDelay() <= 0) {
                        processChildren(children[i]);
                    }
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.error("Exception invoking periodic operation: ", t);
            } finally {
                if (container instanceof Context) {
                    ((Context) container).unbind(false, originalClassLoader);
               }
            }
        }
    }


    // ----------------------------- Inner classes used with start/stop Executor

    private static class StartChild implements Callable<Void> {

        private Container child;

        public StartChild(Container child) {
            this.child = child;
        }

        @Override
        public Void call() throws LifecycleException {
            child.start();
            return null;
        }
    }

    private static class StopChild implements Callable<Void> {

        private Container child;

        public StopChild(Container child) {
            this.child = child;
        }

        @Override
        public Void call() throws LifecycleException {
            if (child.getState().isAvailable()) {
                child.stop();
            }
            return null;
        }
    }

    private static class StartStopThreadFactory implements ThreadFactory {
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        public StartStopThreadFactory(String namePrefix) {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(group, r, namePrefix + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
