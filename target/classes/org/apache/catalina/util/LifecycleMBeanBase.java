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

package org.apache.catalina.util;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.catalina.Globals;
import org.apache.catalina.JmxEnabled;
import org.apache.catalina.LifecycleException;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.res.StringManager;

public abstract class LifecycleMBeanBase extends LifecycleBase
        implements JmxEnabled {


    private static final Log log = LogFactory.getLog(LifecycleMBeanBase.class);

    /** 管理打印日志模板组件 **/
    private static final StringManager sm =
        StringManager.getManager("org.apache.catalina.util");


    /**
     * ObjectName 表示注册到JMX中Bean所对应的对象名称
     *
     * StringBuilder name = new StringBuilder(getDomain());
     * name.append(':');
     * name.append(objectNameKeyProperties);
     * ObjectName on = new ObjectName(name.toString());
     *
     * ObjectName由
     * 域名空间：对象属性组成
     * getDomain():getObjectNameKeyProperties()
     *
     * Jmx bean ObjectName中域名空间
     */
    private String domain = null;
    /**
     * 当前组件在JMX ObjectName
     */
    private ObjectName oname = null;

    /**
     * JMX MBeanServer
     */
    protected MBeanServer mserver = null;


    /**
     * 初始化模板方法实现
     * 将当前组件注册到JMX MBeanServer中
     */
    @Override
    protected void initInternal() throws LifecycleException {
        if (oname == null) {
            mserver = Registry.getRegistry(null, null).getMBeanServer();

            oname = register(this, getObjectNameKeyProperties());
        }
    }


    /**
     * 销毁模板方法实现
     * 将当前组件对象从jmx 注销
     */
    @Override
    protected void destroyInternal() throws LifecycleException {
        unregister(oname);
    }


    /**
     * 设置当前组件ObjectName 域名空间
     */
    @Override
    public final void setDomain(String domain) {
        this.domain = domain;
    }


    /**
     * 返回当前组件ObjectName 域名空间
     */
    @Override
    public final String getDomain() {
        if (domain == null) {
            domain = getDomainInternal();
        }

        if (domain == null) {
            domain = Globals.DEFAULT_MBEAN_DOMAIN;
        }

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
     * 域名空间：对象属性组成
     * getDomain():getObjectNameKeyProperties()
     * 该方法为子组件模板方法实现，返回域名空间
     */
    protected abstract String getDomainInternal();



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
    protected abstract String getObjectNameKeyProperties();



    /**
     * 返回当前组件注册到JMX 对象名称
     */
    @Override
    public final ObjectName getObjectName() {
        return oname;
    }


    /**
     * 向JMX MBeanServer注册 当前组件JMX Bean
     */
    protected final ObjectName register(Object obj,
            String objectNameKeyProperties) {

        // Construct an object name with the right domain
        StringBuilder name = new StringBuilder(getDomain());
        name.append(':');
        name.append(objectNameKeyProperties);

        ObjectName on = null;

        try {
            on = new ObjectName(name.toString());

            Registry.getRegistry(null, null).registerComponent(obj, on, null);
        } catch (MalformedObjectNameException e) {
            log.warn(sm.getString("lifecycleMBeanBase.registerFail", obj, name),
                    e);
        } catch (Exception e) {
            log.warn(sm.getString("lifecycleMBeanBase.registerFail", obj, name),
                    e);
        }

        return on;
    }


    /**
     * 向JMX MBeanServer注销JMX Bean
     */
    protected final void unregister(ObjectName on) {

        if (on == null) {
            return;
        }

        if (mserver == null) {
            log.warn(sm.getString("lifecycleMBeanBase.unregisterNoServer", on));
            return;
        }

        try {
            /** 注销 **/
            mserver.unregisterMBean(on);
        } catch (MBeanRegistrationException e) {
            log.warn(sm.getString("lifecycleMBeanBase.unregisterFail", on), e);
        } catch (InstanceNotFoundException e) {
            log.warn(sm.getString("lifecycleMBeanBase.unregisterFail", on), e);
        }

    }


    /**
     * Not used - NOOP.
     */
    @Override
    public final void postDeregister() {
        // NOOP
    }


    /**
     * Not used - NOOP.
     */
    @Override
    public final void postRegister(Boolean registrationDone) {
        // NOOP
    }


    /**
     * Not used - NOOP.
     */
    @Override
    public final void preDeregister() throws Exception {
        // NOOP
    }


    /**
     * 重置当前对象属性
     */
    @Override
    public final ObjectName preRegister(MBeanServer server, ObjectName name)
            throws Exception {

        this.mserver = server;
        this.oname = name;
        this.domain = name.getDomain().intern();

        return oname;
    }

}
