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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.catalina.AccessLog;
import org.apache.catalina.Container;
import org.apache.catalina.ContainerEvent;
import org.apache.catalina.ContainerListener;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Realm;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.realm.NullRealm;
import org.apache.catalina.util.ServerInfo;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


public class StandardEngine extends ContainerBase implements Engine {

    private static final Log log = LogFactory.getLog(StandardEngine.class);

    // ----------------------------------------------------------- Constructors


    /**
     * 实例化StandardEngine
     */
    public StandardEngine() {
        super();
        /** 设置StandardEngineValve作为pipeline组件尾Value 阀门 **/
        pipeline.setBasic(new StandardEngineValve());
        /** 从系统属性jvmRoute值，设置 Tomcat实例的JVM路由ID **/
        try {
            setJvmRoute(System.getProperty("jvmRoute"));
        } catch(Exception ex) {
            log.warn(sm.getString("standardEngine.jvmRouteFail"));
        }
        /** 设置周期任务执行间隔事件 **/
        backgroundProcessorDelay = 10;
    }


    // ----------------------------------------------------- Instance Variables
    /**
     * 默认host子组件名称
     */
    private String defaultHost = null;


    /**
     * 上层Service组件
     */
    private Service service = null;


    /**
     * Tomcat实例的JVM路由ID。所有路由ID必须唯一,用于集群环境中
     */
    private String jvmRouteId;

    /**
     * AccessLog组件
     */
    private final AtomicReference<AccessLog> defaultAccessLog =
        new AtomicReference<>();

    // ------------------------------------------------------------- Properties


    @Override
    public Realm getRealm() {
        Realm configured = super.getRealm();
        // If no set realm has been called - default to NullRealm
        // This can be overridden at engine, context and host level
        if (configured == null) {
            configured = new NullRealm();
            this.setRealm(configured);
        }
        return configured;
    }


    @Override
    public String getDefaultHost() {
        return (defaultHost);
    }

    @Override
    public void setDefaultHost(String host) {
        String oldDefaultHost = this.defaultHost;
        if (host == null) {
            this.defaultHost = null;
        } else {
            this.defaultHost = host.toLowerCase(Locale.ENGLISH);
        }
        support.firePropertyChange("defaultHost", oldDefaultHost,
                                   this.defaultHost);
    }


    @Override
    public void setJvmRoute(String routeId) {
        jvmRouteId = routeId;
    }


    @Override
    public String getJvmRoute() {
        return jvmRouteId;
    }


    @Override
    public Service getService() {
        return (this.service);
    }

    @Override
    public void setService(Service service) {
        this.service = service;
    }

    // --------------------------------------------------------- Public Methods


    @Override
    public void addChild(Container child) {
        if (!(child instanceof Host)){
            throw new IllegalArgumentException
                    (sm.getString("standardEngine.notHost"));
        }
        super.addChild(child);
    }


    @Override
    public void setParent(Container container) {
        throw new IllegalArgumentException
            (sm.getString("standardEngine.notParent"));
    }


    @Override
    protected void initInternal() throws LifecycleException {
        getRealm();
        super.initInternal();
    }


    @Override
    protected synchronized void startInternal() throws LifecycleException {
        if(log.isInfoEnabled()){
            log.info( "Starting Servlet Engine: " + ServerInfo.getServerInfo());
        }
        super.startInternal();
    }


    /**
     * 使用AccessLog组件打印日志，
     *
     * 如果useDefault=true,使用defaultAccessLog属性中保存AccessLog打印日志
     * 如果defaultAccessLog未初始化AccessLog，则尝试从子容器host,context获取绑定的AccessLog组件设置到defaultAccessLog，使用新设置AccessLog组件打印日志
     */
    @Override
    public void logAccess(Request request, Response response, long time,
            boolean useDefault) {

        boolean logged = false;

        if (getAccessLog() != null) {
            accessLog.log(request, response, time);
            logged = true;
        }

        if (!logged && useDefault) {
            AccessLog newDefaultAccessLog = defaultAccessLog.get();
            /** 如果newDefaultAccessLog未初始化 **/
            if (newDefaultAccessLog == null) {
                /** 获取默认host子容器组件 **/
                Host host = (Host) findChild(getDefaultHost());
                Context context = null;
                if (host != null && host.getState().isAvailable()) {
                    /** 获取host容器绑定的AccessLog 设置给 newDefaultAccessLog**/
                    newDefaultAccessLog = host.getAccessLog();

                    if (newDefaultAccessLog != null) {
                        if (defaultAccessLog.compareAndSet(null,
                                newDefaultAccessLog)) {
                            AccessLogListener l = new AccessLogListener(this,
                                    host, null);
                            l.install();
                        }
                    } else {
                        /** 获取context容器绑定的AccessLog 设置给 newDefaultAccessLog**/
                        context = (Context) host.findChild("");
                        if (context != null &&
                                context.getState().isAvailable()) {
                            newDefaultAccessLog = context.getAccessLog();
                            if (newDefaultAccessLog != null) {
                                if (defaultAccessLog.compareAndSet(null,
                                        newDefaultAccessLog)) {
                                    /** 并设置AccessLogListener 作为当前容器对象engine，和子容器host的监听器。 **/
                                    AccessLogListener l = new AccessLogListener(
                                            this, null, context);
                                    l.install();
                                }
                            }
                        }
                    }
                }

                /** 如果无法从子容器获取AccessLog，默认使用NoopAccessLog作为默认的AccessLog **/
                if (newDefaultAccessLog == null) {
                    newDefaultAccessLog = new NoopAccessLog();
                    if (defaultAccessLog.compareAndSet(null,
                            newDefaultAccessLog)) {
                        /** 并设置AccessLogListener 作为当前容器对象engine，和子容器host的监听器。 **/
                        AccessLogListener l = new AccessLogListener(this, host,
                                context);
                        l.install();
                    }
                }
            }
            /** 使用newDefaultAccessLog打印日志 **/
            newDefaultAccessLog.log(request, response, time);
        }
    }


    /**
     * Return the parent class loader for this component.
     */
    @Override
    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null)
            return (parentClassLoader);
        if (service != null) {
            return (service.getParentClassLoader());
        }
        return (ClassLoader.getSystemClassLoader());
    }


    @Override
    public File getCatalinaBase() {
        if (service != null) {
            Server s = service.getServer();
            if (s != null) {
                File base = s.getCatalinaBase();
                if (base != null) {
                    return base;
                }
            }
        }
        // Fall-back
        return super.getCatalinaBase();
    }


    @Override
    public File getCatalinaHome() {
        if (service != null) {
            Server s = service.getServer();
            if (s != null) {
                File base = s.getCatalinaHome();
                if (base != null) {
                    return base;
                }
            }
        }
        // Fall-back
        return super.getCatalinaHome();
    }


    // -------------------- JMX registration  --------------------

    @Override
    protected String getObjectNameKeyProperties() {
        return "type=Engine";
    }


    @Override
    protected String getDomainInternal() {
        return getName();
    }


    // ----------------------------------------------------------- Inner classes
    protected static final class NoopAccessLog implements AccessLog {

        @Override
        public void log(Request request, Response response, long time) {
            // NOOP
        }

        @Override
        public void setRequestAttributesEnabled(
                boolean requestAttributesEnabled) {
            // NOOP

        }

        @Override
        public boolean getRequestAttributesEnabled() {
            // NOOP
            return false;
        }
    }

    protected static final class AccessLogListener
            implements PropertyChangeListener, LifecycleListener,
            ContainerListener {

        private final StandardEngine engine;
        private final Host host;
        private final Context context;
        private volatile boolean disabled = false;

        public AccessLogListener(StandardEngine engine, Host host,
                Context context) {
            this.engine = engine;
            this.host = host;
            this.context = context;
        }

        public void install() {
            engine.addPropertyChangeListener(this);
            if (host != null) {
                host.addContainerListener(this);
                host.addLifecycleListener(this);
            }
            if (context != null) {
                context.addLifecycleListener(this);
            }
        }

        private void uninstall() {
            disabled = true;
            if (context != null) {
                context.removeLifecycleListener(this);
            }
            if (host != null) {
                host.removeLifecycleListener(this);
                host.removeContainerListener(this);
            }
            engine.removePropertyChangeListener(this);
        }

        @Override
        public void lifecycleEvent(LifecycleEvent event) {
            if (disabled) return;

            String type = event.getType();
            if (Lifecycle.AFTER_START_EVENT.equals(type) ||
                    Lifecycle.BEFORE_STOP_EVENT.equals(type) ||
                    Lifecycle.BEFORE_DESTROY_EVENT.equals(type)) {
                engine.defaultAccessLog.set(null);
                uninstall();
            }
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (disabled) return;
            if ("defaultHost".equals(evt.getPropertyName())) {
                engine.defaultAccessLog.set(null);
                uninstall();
            }
        }

        @Override
        public void containerEvent(ContainerEvent event) {
            if (disabled) return;
            if (Container.ADD_CHILD_EVENT.equals(event.getType())) {
                Context context = (Context) event.getData();
                if ("".equals(context.getPath())) {
                    engine.defaultAccessLog.set(null);
                    uninstall();
                }
            }
        }
    }
}
