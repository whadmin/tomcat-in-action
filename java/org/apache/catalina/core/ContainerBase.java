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
     * ��ǰ����������
     */
    protected String name = null;

    /**
     * ��ǰ��������ĸ��������
     */
    protected Container parent = null;

    /**
     * ���������
     */
    protected ClassLoader parentClassLoader = null;


    /**
     * �洢��������Map,key��ʾ����������,value��ʾ�����������
     */
    protected final HashMap<String, Container> children = new HashMap<>();

    /**
     * �����¼�������
     */
    protected final List<ContainerListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * log
     */
    protected Log logger = null;

    /**
     * ������log����
     */
    protected String logName = null;


    //���������
    /**
     * ���������Ⱥ����cluster
     */
    protected Cluster cluster = null;

    /**
     * ��Ⱥ����CLuster��д��
     */
    private final ReadWriteLock clusterLock = new ReentrantReadWriteLock();

    /**
     * ��ǰ���������Ӧ�ĵ�Realm���
     */
    private volatile Realm realm = null;

    /**
     * ��ǰ���������Ӧ��Realm�����д��
     */
    private final ReadWriteLock realmLock = new ReentrantReadWriteLock();

    /**
     * ��ǰ���������Ӧ��pipeline���
     */
    protected final Pipeline pipeline = new StandardPipeline(this);

    /**
     * ��ǰ���������Ӧ��AccessLog���
     */
    protected volatile AccessLog accessLog = null;

    /**
     * ��ȡAccessLog���ʱ
     * ���accessLogScanCompleteΪfalse
     * ��AccessLog�����ʼ��(ɨ��pipeline���������ΪAccessLog��Value����ӵ�AccessLog���)
     */
    private volatile boolean accessLogScanComplete = false;


    //������
    /**
     * ������־������
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


    /**
     * ���Ա��������
     */
    protected final PropertyChangeSupport support =
            new PropertyChangeSupport(this);


    //��ʱ�����������
    /**
     * ��ʱ�������̶߳���
     */
    private Thread thread = null;

    /**
     * ��ʶ��ʱ�������߳��˳���ʶ
     */
    private volatile boolean threadDone = false;

    /**
     * ��ǰ����Ķ�ִ��backgroundProcessor�����
     */
    protected int backgroundProcessorDelay = -1;



     //��ʱ�����������
    /**
     * ��ǰ����������������ʱ���Ƿ��Զ�����������
     */
    protected boolean startChildren = true;


    /**
     * ���������������ر��̳߳غ����߳�����
     */
    private int startStopThreads = 1;


    /**
     * ���������������ر��̳߳�
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
            /**  ��ȡ��ǰ���ԭʼCluster������� **/
            oldCluster = this.cluster;
            if (oldCluster == cluster)
                return;
            this.cluster = cluster;

            /**  ���Cluster�������������ֹͣCluster��� **/
            if (getState().isAvailable() && (oldCluster != null) &&
                (oldCluster instanceof Lifecycle)) {
                try {
                    ((Lifecycle) oldCluster).stop();
                } catch (LifecycleException e) {
                    log.error("ContainerBase.setCluster: stop: ", e);
                }
            }

            /** ������cluster����͵�ǰ��������� **/
            if (cluster != null)
                cluster.setContainer(this);

            /** �����ǰ�����������״̬����������cluster��� **/
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

        /** �������Ա�� **/
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
            /**  ��ȡ��ǰ���ԭʼRealm������� **/
            Realm oldRealm = this.realm;
            if (oldRealm == realm)
                return;
            this.realm = realm;

            /**  ���Realm�������������ֹͣCluster��� **/
            if (getState().isAvailable() && (oldRealm != null) &&
                (oldRealm instanceof Lifecycle)) {
                try {
                    ((Lifecycle) oldRealm).stop();
                } catch (LifecycleException e) {
                    log.error("ContainerBase.setRealm: stop: ", e);
                }
            }

            /** ������Realm����͵�ǰ��������� **/
            if (realm != null)
                realm.setContainer(this);

            /** �����ǰ�����������״̬����������Realm��� **/
            if (getState().isAvailable() && (realm != null) &&
                (realm instanceof Lifecycle)) {
                try {
                    ((Lifecycle) realm).start();
                } catch (LifecycleException e) {
                    log.error("ContainerBase.setRealm: start: ", e);
                }
            }

            /** �������Ա�� **/
            support.firePropertyChange("realm", oldRealm, this.realm);
        } finally {
            l.unlock();
        }

    }


    // ------------------------------------------------------ Container Methods


    /**
     * Ϊ��ǰ�������������������
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
     * Ϊ��ǰ��������������������ڲ�ʵ��
     */
    private void addChildInternal(Container child) {

        if( log.isDebugEnabled() ){
            log.debug("Add child " + child + " " + this);
        }

        /** ����ӵ��������͵�ǰ��������� **/
        synchronized(children) {
            if (children.get(child.getName()) != null){
                throw new IllegalArgumentException("addChild:  Child name '" +
                        child.getName() +
                        "' is not unique");
            }
            child.setParent(this);  // May throw IAE
            children.put(child.getName(), child);
        }

        /** �����ǰ����״̬Ϊ���У���������ӵ������� **/
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
            /** ����ContainerEvent�¼�ADD_CHILD_EVENT **/
            fireContainerEvent(ADD_CHILD_EVENT, child);
        }
    }


    /**
     * ��������¼�������
     */
    @Override
    public void addContainerListener(ContainerListener listener) {
        listeners.add(listener);
    }


    /**
     * ɾ�������¼�������
     */
    @Override
    public void removeContainerListener(ContainerListener listener) {
        listeners.remove(listener);
    }


    /**
     * ������Ա��������
     */
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }


    /**
     * ɾ�����Ա��������
     */
    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }


    /**
     * ����ָ�����Ƶ�������
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
     * ��ȡ�������������
     */
    @Override
    public Container[] findChildren() {
        synchronized (children) {
            Container results[] = new Container[children.size()];
            return children.values().toArray(results);
        }
    }


    /**
     * ��ȡ���������¼�������
     */
    @Override
    public ContainerListener[] findContainerListeners() {
        ContainerListener[] results =
            new ContainerListener[0];
        return listeners.toArray(results);
    }


    /**
     * ɾ�����������
     */
    @Override
    public void removeChild(Container child) {

        if (child == null) {
            return;
        }

        /** ֹͣҪɾ������������� **/
        try {
            if (child.getState().isAvailable()) {
                child.stop();
            }
        } catch (LifecycleException e) {
            log.error("ContainerBase.removeChild: stop: ", e);
        }

        /** ���ɾ�������������ֹͣ��״̬������LifecycleState.DESTROYING����������Ҫɾ������������� **/
        try {
            if (!LifecycleState.DESTROYING.equals(child.getState())) {
                child.destroy();
            }
        } catch (LifecycleException e) {
            log.error("ContainerBase.removeChild: destroy: ", e);
        }

        /** ��ɾ��������������ӵ�ǰ������children Map���͵�������ɾ�� **/
        synchronized(children) {
            if (children.get(child.getName()) == null)
                return;
            children.remove(child.getName());
        }

        /** ����ContainerEvent�¼�REMOVE_CHILD_EVENT **/
        fireContainerEvent(REMOVE_CHILD_EVENT, child);
    }


    /**
     * �����ʼ��ģ��ʵ��
     */
    @Override
    protected void initInternal() throws LifecycleException {
        /** ʵ�������������������ر��̳߳�  **/
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
     * �������ģ��ʵ��
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        logger = null;
        /** ��ʼ����־��� **/
        getLogger();

        /** ����Cluster���**/
        Cluster cluster = getClusterInternal();
        if (cluster instanceof Lifecycle) {
            ((Lifecycle) cluster).start();
        }

        /** ����Realm���**/
        Realm realm = getRealmInternal();
        if (realm instanceof Lifecycle) {
            ((Lifecycle) realm).start();
        }

        /** ʹ���̳߳��첽�������������� **/
        Container children[] = findChildren();
        List<Future<Void>> results = new ArrayList<>();
        for (int i = 0; i < children.length; i++) {
            results.add(startStopExecutor.submit(new StartChild(children[i])));
        }

        MultiThrowable multiThrowable = null;

        /** �ȴ������������������ **/
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

        /** ����pipeline���**/
        if (pipeline instanceof Lifecycle) {
            ((Lifecycle) pipeline).start();
        }

        /** ���õ�ǰ������״̬LifecycleState.STARTING **/
        setState(LifecycleState.STARTING);

        /** �����̣߳���ʱ����ǰ������������������backgroundProcess���� **/
        threadStart();
    }


    /**
     * ���ֹͣģ��ʵ��
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        /** �رմ���ʱ�����߳� **/
        threadStop();

        /** ���õ�ǰ������״̬LifecycleState.STOPPING **/
        setState(LifecycleState.STOPPING);


        /** ֹͣpipeline���**/
        if (pipeline instanceof Lifecycle &&
                ((Lifecycle) pipeline).getState().isAvailable()) {
            ((Lifecycle) pipeline).stop();
        }

        /** ʹ���̳߳��첽�����������ر� **/
        Container children[] = findChildren();
        List<Future<Void>> results = new ArrayList<>();
        for (int i = 0; i < children.length; i++) {
            results.add(startStopExecutor.submit(new StopChild(children[i])));
        }

        /** �ȴ������������ر���� **/
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

        /** �ر�Realm���**/
        Realm realm = getRealmInternal();
        if (realm instanceof Lifecycle) {
            ((Lifecycle) realm).stop();
        }

        /** �ر�Cluster���**/
        Cluster cluster = getClusterInternal();
        if (cluster instanceof Lifecycle) {
            ((Lifecycle) cluster).stop();
        }
    }


    /**
     * �������ģ��ʵ��
     */
    @Override
    protected void destroyInternal() throws LifecycleException {

        /** ����Realm���**/
        Realm realm = getRealmInternal();
        if (realm instanceof Lifecycle) {
            ((Lifecycle) realm).destroy();
        }

        /** ����cluster���**/
        Cluster cluster = getClusterInternal();
        if (cluster instanceof Lifecycle) {
            ((Lifecycle) cluster).destroy();
        }

        /** ����cluster���**/
        if (pipeline instanceof Lifecycle) {
            ((Lifecycle) pipeline).destroy();
        }

        /** ������������� **/
        for (Container child : findChildren()) {
            removeChild(child);
        }

        /** �������͵�ǰ�������ȡ������ **/
        if (parent != null) {
            parent.removeChild(this);
        }

        /** �رմ��������������ر��̳߳� **/
        if (startStopExecutor != null) {
            startStopExecutor.shutdownNow();
        }

        super.destroyInternal();
    }


    /**
     * ʹ��AccessLog�����ӡ��־��
     * �����ǰ���������AccessLog�����ʹ�ø���AccessLog���
     */
    @Override
    public void logAccess(Request request, Response response, long time,
            boolean useDefault) {

        boolean logged = false;

        /** ʹ��AccessLog�����ӡ��־ **/
        if (getAccessLog() != null) {
            getAccessLog().log(request, response, time);
            logged = true;
        }

        /** ʹ�ø�����AccessLog�����ӡ��־ **/
        if (getParent() != null) {
            getParent().logAccess(request, response, time, (useDefault && !logged));
        }
    }

    /**
     * ��ȡAccessLog���ʱ
     * ���accessLogScanCompleteΪfalse
     * ��AccessLog�����ʼ��(ɨ��pipeline���������ΪAccessLog��Value����ӵ�AccessLog���)
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
     * ��pipeline������һ��Valve
     */
    public synchronized void addValve(Valve valve) {
        pipeline.addValve(valve);
    }


    /**
     * ����Ĭ�϶�ʱ�������߼�
     */
    @Override
    public void backgroundProcess() {

        if (!getState().isAvailable())
            return;

        /** ����Cluster���backgroundProcess ���� **/
        Cluster cluster = getClusterInternal();
        if (cluster != null) {
            try {
                cluster.backgroundProcess();
            } catch (Exception e) {
                log.warn(sm.getString("containerBase.backgroundProcess.cluster",
                        cluster), e);
            }
        }

        /** ����Realm���backgroundProcess ���� **/
        Realm realm = getRealmInternal();
        if (realm != null) {
            try {
                realm.backgroundProcess();
            } catch (Exception e) {
                log.warn(sm.getString("containerBase.backgroundProcess.realm", realm), e);
            }
        }

        /** ����pipeline���������Value��backgroundProcess ���� **/
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
     * ���������¼�
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


    // jmx��ط���

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
     * �����̣߳���ʱ����ǰ������������������backgroundProcess����
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
     * �رմ���ʱ�����߳�
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
     * ��ʱ�������̶߳���
     */
    protected class ContainerBackgroundProcessor implements Runnable {

        @Override
        public void run() {
            Throwable t = null;
            String unexpectedDeathMessage = sm.getString(
                    "containerBase.backgroundProcess.unexpectedThreadDeath",
                    Thread.currentThread().getName());
            try {
                /** ���backgroundProcessorDelayʱ�䣬����processChildren���� **/
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
         * ���õ�ǰ�����Լ������������backgroundProcess����
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
