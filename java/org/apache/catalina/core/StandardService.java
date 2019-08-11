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
import java.util.ArrayList;

import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Engine;
import org.apache.catalina.Executor;
import org.apache.catalina.JmxEnabled;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.mapper.Mapper;
import org.apache.catalina.mapper.MapperListener;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;


/**
 * Standard implementation of the <code>Service</code> interface.  The
 * associated Container is generally an instance of Engine, but this is
 * not required.
 *
 * @author Craig R. McClanahan
 */

public class StandardService extends LifecycleMBeanBase implements Service {

    private static final Log log = LogFactory.getLog(StandardService.class);


    // ----------------------------------------------------- Instance Variables

    /**
     * 服务的名称对应  <Service name="Catalina">
     */
    private String name = null;


    /**
     * 管理打印日志模板组件
     */
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);

    /**
     * 外部 server组件
     */
    private Server server = null;

    /**
     *  属性变更监听器
     */
    protected final PropertyChangeSupport support = new PropertyChangeSupport(this);


    /**
     * Connector子组件（连接器）
     */
    protected Connector connectors[] = new Connector[0];


    private final Object connectorsLock = new Object();

    /**
     * 线程池对象
     */
    protected final ArrayList<Executor> executors = new ArrayList<>();

    /**
     * Engine 子组件（Servlet容器）
     */
    private Engine engine = null;

    /**
     * 默认为null
     */
    private ClassLoader parentClassLoader = null;

    /**
     * 请求映射对象
     */
    protected final Mapper mapper = new Mapper();


    /**
     * 请求映射对象监听器
     */
    protected final MapperListener mapperListener = new MapperListener(this);


    // ------------------------------------------------------------- Properties
    @Override
    public Mapper getMapper() {
        return mapper;
    }

    @Override
    public Engine getContainer() {
        return engine;
    }

    @Override
    public void setContainer(Engine engine) {
        /** 获取原始Engine组件引用 **/
        Engine oldEngine = this.engine;
        if (oldEngine != null) {
            oldEngine.setService(null);
        }
        /** 设置engine **/
        this.engine = engine;
        if (this.engine != null) {
            this.engine.setService(this);
        }
        /** 如果StandardService组件运行，则启动添加engine组件,并重启MapperListener**/
        if (getState().isAvailable()) {
            if (this.engine != null) {
                try {
                    this.engine.start();
                } catch (LifecycleException e) {
                    log.warn(sm.getString("standardService.engine.startFailed"), e);
                }
            }
            // Restart MapperListener to pick up new engine.
            try {
                mapperListener.stop();
            } catch (LifecycleException e) {
                log.warn(sm.getString("standardService.mapperListener.stopFailed"), e);
            }
            try {
                mapperListener.start();
            } catch (LifecycleException e) {
                log.warn(sm.getString("standardService.mapperListener.startFailed"), e);
            }
            if (oldEngine != null) {
                try {
                    oldEngine.stop();
                } catch (LifecycleException e) {
                    log.warn(sm.getString("standardService.engine.stopFailed"), e);
                }
            }
        }

        /** 将Engine属性更改通知给监听器  **/
        support.firePropertyChange("container", oldEngine, this.engine);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public Server getServer() {
        return this.server;
    }

    @Override
    public void setServer(Server server) {
        this.server = server;
    }
    // --------------------------------------------------------- Public Methods

    /**
     * 为Service组件添加 Connector子组件
     */
    @Override
    public void addConnector(Connector connector) {

        synchronized (connectorsLock) {
            /** connector 反向关联父组件 Service **/
            connector.setService(this);

            /** 将Connector组件添加到Service 组件的connectors数组类型属性connectors中 **/
            Connector results[] = new Connector[connectors.length + 1];
            System.arraycopy(connectors, 0, results, 0, connectors.length);
            results[connectors.length] = connector;
            connectors = results;

            /** 如果当前Service组件正在运行，则启动添加Connector 组件 **/
            if (getState().isAvailable()) {
                try {
                    connector.start();
                } catch (LifecycleException e) {
                    log.error(sm.getString(
                            "standardService.connector.startFailed",
                            connector), e);
                }
            }

            /** 将connector属性更改通知给监听器  **/
            support.firePropertyChange("connector", null, connector);
        }
    }

    /**
     * 返回所有 Connector子组件
     */
    @Override
    public Connector[] findConnectors() {
        return connectors;
    }


    /**
     * 从Service组件中删除Connector子组件
     */
    @Override
    public void removeConnector(Connector connector) {

        synchronized (connectorsLock) {
            /** 从Connector子组件数组找到删除,connector子组件 **/
            int j = -1;
            for (int i = 0; i < connectors.length; i++) {
                if (connector == connectors[i]) {
                    j = i;
                    break;
                }
            }
            /** 没有找到忽略此动作 **/
            if (j < 0)
                return;

            /** 对删除connector组件 停止动作**/
            if (connectors[j].getState().isAvailable()) {
                try {
                    connectors[j].stop();
                } catch (LifecycleException e) {
                    log.error(sm.getString(
                            "standardService.connector.stopFailed",
                            connectors[j]), e);
                }
            }
            /** 将connector中Service设置为null **/
            connector.setService(null);

            /** 对connector数组中在删除connector子组件后connector子组件在数组中前移 **/
            int k = 0;
            Connector results[] = new Connector[connectors.length - 1];
            for (int i = 0; i < connectors.length; i++) {
                if (i != j)
                    results[k++] = connectors[i];
            }
            connectors = results;

            /** support通知 connector属性变更 **/
            support.firePropertyChange("connector", connector, null);
        }
    }


    /**
     * 返回所有Connector在JMX 中ObjectName
     */
    public ObjectName[] getConnectorNames() {
        ObjectName results[] = new ObjectName[connectors.length];
        for (int i=0; i<results.length; i++) {
            results[i] = connectors[i].getObjectName();
        }
        return results;
    }


    /**
     * 向此组件添加属性更改侦听器。
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }


    /**
     * 从此组件中删除属性更改侦听器。
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }


    /**
     * 返回此组件的String表示形式。
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("StandardService[");
        sb.append(getName());
        sb.append("]");
        return (sb.toString());
    }


    /**
     * 向Service组件添加线程池
     * Executor 扩展了线程池，作为tomcat的一个组件
     */
    @Override
    public void addExecutor(Executor ex) {
        synchronized (executors) {
            if (!executors.contains(ex)) {
                executors.add(ex);

                /** 如果当前Service组件已经启动，则启动 线程池 组件 **/
                if (getState().isAvailable()) {
                    try {
                        ex.start();
                    } catch (LifecycleException x) {
                        log.error("Executor.start", x);
                    }
                }
            }
        }
    }


    /**
     * 返回Service组件中所有线程池组件
     */
    @Override
    public Executor[] findExecutors() {
        synchronized (executors) {
            Executor[] arr = new Executor[executors.size()];
            executors.toArray(arr);
            return arr;
        }
    }


    /**
     * 通过名称获取Service中指定线程池组件
     */
    @Override
    public Executor getExecutor(String executorName) {
        synchronized (executors) {
            for (Executor executor: executors) {
                if (executorName.equals(executor.getName()))
                    return executor;
            }
        }
        return null;
    }


    /**
     * 删除Service线程池中只当线程池组件
     */
    @Override
    public void removeExecutor(Executor ex) {
        synchronized (executors) {
            if ( executors.remove(ex) && getState().isAvailable() ) {
                try {
                    ex.stop();
                } catch (LifecycleException e) {
                    log.error("Executor.stop", e);
                }
            }
        }
    }


    /**
     * 组件启动模板方法实现
     */
    @Override
    protected void startInternal() throws LifecycleException {

        if(log.isInfoEnabled())
            log.info(sm.getString("standardService.start.name", this.name));

        /** 更正当前组件状态为STARTING  **/
        setState(LifecycleState.STARTING);

        /** 启动engine 子组件 **/
        if (engine != null) {
            synchronized (engine) {
                engine.start();
            }
        }

        /** 启动所有Executor 子组件 **/
        synchronized (executors) {
            for (Executor executor: executors) {
                executor.start();
            }
        }

        /** 启动mapperListener 子组件 **/
        mapperListener.start();

        /** 启动所有Connector 子组件 **/
        synchronized (connectorsLock) {
            for (Connector connector: connectors) {
                try {
                    // If it has already failed, don't try and start it
                    if (connector.getState() != LifecycleState.FAILED) {
                        connector.start();
                    }
                } catch (Exception e) {
                    log.error(sm.getString(
                            "standardService.connector.startFailed",
                            connector), e);
                }
            }
        }
    }


    /**
     * 组件停止模板方法实现
     */
    @Override
    protected void stopInternal() throws LifecycleException {

        /** 暂停所有Connector 子组件 **/
        synchronized (connectorsLock) {
            for (Connector connector: connectors) {
                try {
                    connector.pause();
                } catch (Exception e) {
                    log.error(sm.getString(
                            "standardService.connector.pauseFailed",
                            connector), e);
                }
                /** 关闭服务Socket **/
                connector.getProtocolHandler().closeServerSocketGraceful();
            }
        }

        if(log.isInfoEnabled())
            log.info(sm.getString("standardService.stop.name", this.name));
        /** 更正当前组件状态为STOPPING  **/
        setState(LifecycleState.STOPPING);

        /** 关闭engine 子组件 **/
        if (engine != null) {
            synchronized (engine) {
                engine.stop();
            }
        }

        /** 关闭所有状态为STARTED Connector子组件 **/
        synchronized (connectorsLock) {
            for (Connector connector: connectors) {
                if (!LifecycleState.STARTED.equals(
                        connector.getState())) {
                    // Connectors only need stopping if they are currently
                    // started. They may have failed to start or may have been
                    // stopped (e.g. via a JMX call)
                    continue;
                }
                try {
                    connector.stop();
                } catch (Exception e) {
                    log.error(sm.getString(
                            "standardService.connector.stopFailed",
                            connector), e);
                }
            }
        }

        //关闭mapperListener ???
        if (mapperListener.getState() != LifecycleState.INITIALIZED) {
            mapperListener.stop();
        }

        /** 关闭所有Executor 子组件 **/
        synchronized (executors) {
            for (Executor executor: executors) {
                executor.stop();
            }
        }
    }


    /**
     * 组件初始化模板方法实现
     */
    @Override
    protected void initInternal() throws LifecycleException {


        super.initInternal();

        /** 初始化engine 子组件 **/
        if (engine != null) {
            engine.init();
        }

        /** 初始化所有Executor 子组件 **/
        for (Executor executor : findExecutors()) {
            if (executor instanceof JmxEnabled) {
                ((JmxEnabled) executor).setDomain(getDomain());
            }
            executor.init();
        }

        /** 初始化mapperListener 子组件 **/
        mapperListener.init();

        /** 初始化所有Connector 子组件 **/
        synchronized (connectorsLock) {
            for (Connector connector : connectors) {
                try {
                    connector.init();
                } catch (Exception e) {
                    String message = sm.getString(
                            "standardService.connector.initFailed", connector);
                    log.error(message, e);

                    if (Boolean.getBoolean("org.apache.catalina.startup.EXIT_ON_INIT_FAILURE"))
                        throw new LifecycleException(message);
                }
            }
        }
    }

    /**
     * 组件销毁模板方法实现
     */
    @Override
    protected void destroyInternal() throws LifecycleException {
        /** 销毁 mapperListener **/
        mapperListener.destroy();

        /** 销毁所有Connector 子组件 **/
        synchronized (connectorsLock) {
            for (Connector connector : connectors) {
                try {
                    connector.destroy();
                } catch (Exception e) {
                    log.error(sm.getString(
                            "standardService.connector.destroyFailed", connector), e);
                }
            }
        }

        /** 销毁所有Executor 子组件 **/
        for (Executor executor : findExecutors()) {
            executor.destroy();
        }

        /** 销毁engine 子组件 **/
        if (engine != null) {
            engine.destroy();
        }

        super.destroyInternal();
    }


    /**
     * 获取父类加载器，这里parentClassLoader默认为null
     * 返回外部组件server.getParentClassLoader()默认是Shared类加载器
     */
    @Override
    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null)
            return parentClassLoader;
        if (server != null) {
            return server.getParentClassLoader();
        }
        return ClassLoader.getSystemClassLoader();
    }


    /**
     * 设置父类加载器
     */
    @Override
    public void setParentClassLoader(ClassLoader parent) {
        ClassLoader oldParentClassLoader = this.parentClassLoader;
        this.parentClassLoader = parent;
        support.firePropertyChange("parentClassLoader", oldParentClassLoader,
                                   this.parentClassLoader);
    }


    /**
     * ObjectName 表示注册到JMX中Bean所对应的对象名称
     *
     * StringBuilder name = new StringBuilder(getDomain());
     * name.append(':');
     * name.append(objectNameKeyProperties);
     * ObjectName on = new ObjectName(name.toString());
     *
     * ObjectName名称组成由
     * 域名空间：对象属性组成
     * getDomain():getObjectNameKeyProperties()
     *
     * 当前方法是getDomain()方法扩展子类实现，该方法父类LifecycleMBeanBase模板方法实现，返回域名空间
     * 获取子组件engine 组件域名空间作为自己域名空间
     */
    @Override
    protected String getDomainInternal() {
        String domain = null;
        Container engine = getContainer();

        // Use the engine name first
        if (engine != null) {
            domain = engine.getName();
        }

        // No engine or no engine name, use the service name
        if (domain == null) {
            domain = getName();
        }

        // No service name, return null which will trigger the use of the
        // default
        return domain;
    }

    /**
     * ObjectName 表示注册到JMX中Bean所对应的对象名称
     *
     * StringBuilder name = new StringBuilder(getDomain());
     * name.append(':');
     * name.append(objectNameKeyProperties);
     * ObjectName on = new ObjectName(name.toString());
     *
     * ObjectName名称组成由
     * 域名空间：对象属性集合
     * getDomain():getObjectNameKeyProperties()
     * 该方法父类LifecycleMBeanBase模板方法实现，返回对象属性集合
     */
    @Override
    public final String getObjectNameKeyProperties() {
        return "type=Service";
    }
}
