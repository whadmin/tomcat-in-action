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

    //������־���
    public Log getLogger();

    //������־����
    public String getLogName();

    //��������ע�ᵽJMX bean ObjectName
    public ObjectName getObjectName();

    //��������ע�ᵽJMX bean �����ռ�
    public String getDomain();

    //��������ע�ᵽJMX bean ����
    public String getMBeanKeyProperties();

    //������������Pipeline���
    public Pipeline getPipeline();

    //������������Cluster���
    public Cluster getCluster();

    //������������Cluster���
    public void setCluster(Cluster cluster);

    //��������������ִ�м���¼�
    public int getBackgroundProcessorDelay();

    //��������������ִ�м���¼�
    public void setBackgroundProcessorDelay(int delay);

    //������������
    public String getName();

    //������������
    public void setName(String name);

    //���ظ�����
    public Container getParent();

    //���ø�����
    public void setParent(Container container);

    //���ظ��������
    public ClassLoader getParentClassLoader();

    //���ø��������
    public void setParentClassLoader(ClassLoader parent);

    //������������Realm���
    public Realm getRealm();

    // ������������Realm���
    public void setRealm(Realm realm);

    //����Ĭ����������������÷���
    public void backgroundProcess();

    //Ϊ��ǰ�������������������
    public void addChild(Container child);

    //��������¼�������
    public void addContainerListener(ContainerListener listener);

    //������Ա��������
    public void addPropertyChangeListener(PropertyChangeListener listener);

    //����ָ�����Ƶ�������
    public Container findChild(String name);

    //��ȡ�������������
    public Container[] findChildren();

    //�������������¼�������
    public ContainerListener[] findContainerListeners();

    //ɾ��������
    public void removeChild(Container child);

    //��ǰ����ɾ�������¼�������
    public void removeContainerListener(ContainerListener listener);

    //��ǰ����ɾ�����Ա��������
    public void removePropertyChangeListener(PropertyChangeListener listener);

    //���������¼�
    public void fireContainerEvent(String type, Object data);

    //ʹ��AccessLog�����ӡ������־
    public void logAccess(Request request, Response response, long time,
            boolean useDefault);

    //���ط�����־���AccessLog
    public AccessLog getAccessLog();

    //�������ô��������������ر��̳߳غ����߳�����
    public int getStartStopThreads();

    //���ô��������������ر��̳߳غ����߳�����
    public void setStartStopThreads(int startStopThreads);

    //����tomcat����Ŀ¼
    public File getCatalinaBase();

    //����tomcat����Ŀ¼
    public File getCatalinaHome();
}
