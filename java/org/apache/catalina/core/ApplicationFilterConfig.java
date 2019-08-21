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


import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.ObjectName;
import javax.naming.NamingException;
import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.security.SecurityUtil;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.log.SystemLogHandler;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.modeler.Util;
import org.apache.tomcat.util.res.StringManager;


/**
 * 描述Servlet规范中FilterConfig
 *
 * @author Craig R. McClanahan
 */
public final class ApplicationFilterConfig implements FilterConfig, Serializable {

    private static final long serialVersionUID = 1L;

    static final StringManager sm =
        StringManager.getManager(Constants.Package);

    /**
     * 日志组件
     */
    private final Log log = LogFactory.getLog(ApplicationFilterConfig.class);

    /**
     * 空字符串集合
     */
    private static final List<String> emptyString = Collections.emptyList();

    /**
     * 关联的Context
     */
    private final transient Context context;

    /**
     * 关联的Filter实例对象
     */
    private transient Filter filter = null;

    /**
     * 关联的Filter描述对象
     */
    private final FilterDef filterDef;

    /**
     * 构造实例管理器
     */
    private transient InstanceManager instanceManager;

    /**
     * JMX ObjectName
     */
    private ObjectName oname;


    /**
     * 实例化ApplicationFilterConfig
     */
    ApplicationFilterConfig(Context context, FilterDef filterDef)
            throws ClassCastException, ClassNotFoundException, IllegalAccessException,
            InstantiationException, ServletException, InvocationTargetException, NamingException,
            IllegalArgumentException, NoSuchMethodException, SecurityException {
        super();
        this.context = context;
        this.filterDef = filterDef;
        // Allocate a new filter instance if necessary
        if (filterDef.getFilter() == null) {
            getFilter();
        } else {
            this.filter = filterDef.getFilter();
            getInstanceManager().newInstance(filter);
            initFilter();
        }
    }

    /**
     * 获取Filter名称<filter-name></filter-name>
     */
    @Override
    public String getFilterName() {
        return (filterDef.getFilterName());
    }

    /**
     * 获取实现FilterJava类的完全限定名
     */
    public String getFilterClass() {
        return filterDef.getFilterClass();
    }

    /**
     * 获取Filter指定初始化参数key对应的值
     */
    @Override
    public String getInitParameter(String name) {
        Map<String,String> map = filterDef.getParameterMap();
        if (map == null) {
            return (null);
        }
        return map.get(name);
    }

    /**
     * 获取Filter初始化参数key集合
     */
    @Override
    public Enumeration<String> getInitParameterNames() {
        Map<String,String> map = filterDef.getParameterMap();
        if (map == null) {
            return Collections.enumeration(emptyString);
        }
        return Collections.enumeration(map.keySet());
    }

    /**
     * 获取Filter初始化参数
     */
    public Map<String, String> getFilterInitParameterMap() {
        return Collections.unmodifiableMap(filterDef.getParameterMap());
    }


    /**
     * 获取ServletContext
     */
    @Override
    public ServletContext getServletContext() {
        return this.context.getServletContext();
    }


    /**
     * Return a String representation of this object.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ApplicationFilterConfig[");
        sb.append("name=");
        sb.append(filterDef.getFilterName());
        sb.append(", filterClass=");
        sb.append(filterDef.getFilterClass());
        sb.append("]");
        return (sb.toString());

    }

    /**
     * 获取Filter实例对象
     */
    Filter getFilter() throws ClassCastException, ClassNotFoundException, IllegalAccessException,
            InstantiationException, ServletException, InvocationTargetException, NamingException,
            IllegalArgumentException, NoSuchMethodException, SecurityException {

        if (this.filter != null)
            return (this.filter);

        /** 获取实现FilterJava类的完全限定名  **/
        String filterClass = filterDef.getFilterClass();
        this.filter = (Filter) getInstanceManager().newInstance(filterClass);
        initFilter();
        return (this.filter);
    }

    private void initFilter() throws ServletException {
        if (context instanceof StandardContext &&
                context.getSwallowOutput()) {
            try {
                SystemLogHandler.startCapture();
                filter.init(this);
            } finally {
                String capturedlog = SystemLogHandler.stopCapture();
                if (capturedlog != null && capturedlog.length() > 0) {
                    getServletContext().log(capturedlog);
                }
            }
        } else {
            filter.init(this);
        }
        registerJMX();
    }


    FilterDef getFilterDef() {
        return (this.filterDef);
    }

    /**
     * 释放当前对象，销毁filter
     */
    void release() {
        unregisterJMX();
        if (this.filter != null) {
            try {
                if (Globals.IS_SECURITY_ENABLED) {
                    try {
                        SecurityUtil.doAsPrivilege("destroy", filter);
                    } finally {
                        SecurityUtil.remove(filter);
                    }
                } else {
                    filter.destroy();
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                context.getLogger().error(sm.getString(
                        "applicationFilterConfig.release",
                        filterDef.getFilterName(),
                        filterDef.getFilterClass()), t);
            }
            if (!context.getIgnoreAnnotations()) {
                try {
                    ((StandardContext) context).getInstanceManager().destroyInstance(this.filter);
                } catch (Exception e) {
                    Throwable t = ExceptionUtils
                            .unwrapInvocationTargetException(e);
                    ExceptionUtils.handleThrowable(t);
                    context.getLogger().error(
                            sm.getString("applicationFilterConfig.preDestroy",
                                    filterDef.getFilterName(), filterDef.getFilterClass()), t);
                }
            }
        }
        this.filter = null;

     }


    /**
     * 构造实例管理器
     */
    private InstanceManager getInstanceManager() {
        if (instanceManager == null) {
            if (context instanceof StandardContext) {
                instanceManager = ((StandardContext)context).getInstanceManager();
            } else {
                instanceManager = new DefaultInstanceManager(null,
                        new HashMap<String, Map<String, String>>(),
                        context,
                        getClass().getClassLoader());
            }
        }
        return instanceManager;
    }

    /**
     * 注册到JMX
     */
    private void registerJMX() {
        String parentName = context.getName();
        if (!parentName.startsWith("/")) {
            parentName = "/" + parentName;
        }
        String hostName = context.getParent().getName();
        hostName = (hostName == null) ? "DEFAULT" : hostName;

        // domain == engine name
        String domain = context.getParent().getParent().getName();

        String webMod = "//" + hostName + parentName;
        String onameStr = null;
        String filterName = filterDef.getFilterName();
        if (Util.objectNameValueNeedsQuote(filterName)) {
            filterName = ObjectName.quote(filterName);
        }
        if (context instanceof StandardContext) {
            StandardContext standardContext = (StandardContext) context;
            onameStr = domain + ":j2eeType=Filter,WebModule=" + webMod +
                    ",name=" + filterName + ",J2EEApplication=" +
                    standardContext.getJ2EEApplication() + ",J2EEServer=" +
                    standardContext.getJ2EEServer();
        } else {
            onameStr = domain + ":j2eeType=Filter,name=" + filterName +
                 ",WebModule=" + webMod;
        }
        try {
            oname = new ObjectName(onameStr);
            Registry.getRegistry(null, null).registerComponent(this, oname,
                    null);
        } catch (Exception ex) {
            log.info(sm.getString("applicationFilterConfig.jmxRegisterFail",
                    getFilterClass(), getFilterName()), ex);
        }
    }

    /**
     * 从JMX注销
     */
    private void unregisterJMX() {
        // unregister this component
        if (oname != null) {
            try {
                Registry.getRegistry(null, null).unregisterComponent(oname);
                if (log.isDebugEnabled())
                    log.debug(sm.getString(
                            "applicationFilterConfig.jmxUnregister",
                            getFilterClass(), getFilterName()));
            } catch(Exception ex) {
                log.error(sm.getString(
                        "applicationFilterConfig.jmxUnregisterFail",
                        getFilterClass(), getFilterName()), ex);
            }
        }
    }
}
