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
package org.apache.catalina;

import java.beans.PropertyChangeListener;
import java.io.File;

import javax.management.ObjectName;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.juli.logging.Log;


/**
 * A <b>Container</b> is an object that can execute requests received from
 * a client, and return responses based on those requests.  A Container may
 * optionally support a pipeline of Valves that process the request in an
 * order configured at runtime, by implementing the <b>Pipeline</b> interface
 * as well.
 * <p>
 * Containers will exist at several conceptual levels within Catalina.  The
 * following examples represent common cases:
 * <ul>
 * <li><b>Engine</b> - Representation of the entire Catalina servlet engine,
 *     most likely containing one or more subcontainers that are either Host
 *     or Context implementations, or other custom groups.
 * <li><b>Host</b> - Representation of a virtual host containing a number
 *     of Contexts.
 * <li><b>Context</b> - Representation of a single ServletContext, which will
 *     typically contain one or more Wrappers for the supported servlets.
 * <li><b>Wrapper</b> - Representation of an individual servlet definition
 *     (which may support multiple servlet instances if the servlet itself
 *     implements SingleThreadModel).
 * </ul>
 * A given deployment of Catalina need not include Containers at all of the
 * levels described above.  For example, an administration application
 * embedded within a network device (such as a router) might only contain
 * a single Context and a few Wrappers, or even a single Wrapper if the
 * application is relatively small.  Therefore, Container implementations
 * need to be designed so that they will operate correctly in the absence
 * of parent Containers in a given deployment.
 * <p>
 * A Container may also be associated with a number of support components
 * that provide functionality which might be shared (by attaching it to a
 * parent Container) or individually customized.  The following support
 * components are currently recognized:
 * <ul>
 * <li><b>Loader</b> - Class loader to use for integrating new Java classes
 *     for this Container into the JVM in which Catalina is running.
 * <li><b>Logger</b> - Implementation of the <code>log()</code> method
 *     signatures of the <code>ServletContext</code> interface.
 * <li><b>Manager</b> - Manager for the pool of Sessions associated with
 *     this Container.
 * <li><b>Realm</b> - Read-only interface to a security domain, for
 *     authenticating user identities and their corresponding roles.
 * <li><b>Resources</b> - JNDI directory context enabling access to static
 *     resources, enabling custom linkages to existing server components when
 *     Catalina is embedded in a larger server.
 * </ul>
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
public interface Container extends Lifecycle {

    public static final String ADD_CHILD_EVENT = "addChild";

    public static final String ADD_VALVE_EVENT = "addValve";

    public static final String REMOVE_CHILD_EVENT = "removeChild";

    public static final String REMOVE_VALVE_EVENT = "removeValve";

    //返回日志组件
    public Log getLogger();

    //返回日志名称
    public String getLogName();

    //返回容器注册到JMX bean ObjectName
    public ObjectName getObjectName();

    //返回容器注册到JMX bean 命名空间
    public String getDomain();

    //返回容器注册到JMX bean 属性
    public String getMBeanKeyProperties();

    //返回容器依赖Pipeline组件
    public Pipeline getPipeline();

    //返回容器依赖Cluster组件
    public Cluster getCluster();

    //设置容器依赖Cluster组件
    public void setCluster(Cluster cluster);

    //返回周期性任务执行间隔事件
    public int getBackgroundProcessorDelay();

    //设置周期性任务执行间隔事件
    public void setBackgroundProcessorDelay(int delay);

    //返回容器名称
    public String getName();

    //设置容器名称
    public void setName(String name);

    //返回父容器
    public Container getParent();

    //设置父容器
    public void setParent(Container container);

    //返回父类加载器
    public ClassLoader getParentClassLoader();

    //设置父类加载器
    public void setParentClassLoader(ClassLoader parent);

    //返回容器依赖Realm组件
    public Realm getRealm();

    // 设置容器依赖Realm组件
    public void setRealm(Realm realm);

    //容器默认周期性任务处理调用方法
    public void backgroundProcess();

    //为当前容器组件添加子容器组件
    public void addChild(Container child);

    //添加容器事件监听器
    public void addContainerListener(ContainerListener listener);

    //添加属性变更监听器
    public void addPropertyChangeListener(PropertyChangeListener listener);

    //查找指定名称的子容器
    public Container findChild(String name);

    //获取所有子容器组件
    public Container[] findChildren();

    //返回所有容器事件监听器
    public ContainerListener[] findContainerListeners();

    //删除子容器
    public void removeChild(Container child);

    //当前容器删除容器事件监听器
    public void removeContainerListener(ContainerListener listener);

    //当前容器删除属性变更监听器
    public void removePropertyChangeListener(PropertyChangeListener listener);

    //处理容器事件
    public void fireContainerEvent(String type, Object data);

    //使用AccessLog组件打印请求日志
    public void logAccess(Request request, Response response, long time,
            boolean useDefault);

    //返回访问日志组件AccessLog
    public AccessLog getAccessLog();

    //返回设置处理子容器启动关闭线程池核心线程数。
    public int getStartStopThreads();

    //设置处理子容器启动关闭线程池核心线程数。
    public void setStartStopThreads(int startStopThreads);

    //返回tomcat工作目录
    public File getCatalinaBase();

    //返回tomcat按照目录
    public File getCatalinaHome();
}
