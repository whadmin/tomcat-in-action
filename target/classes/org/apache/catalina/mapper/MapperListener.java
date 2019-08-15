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
package org.apache.catalina.mapper;

import java.util.ArrayList;
import java.util.List;

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
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Service;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.Wrapper;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;


/**
 * Mapper listener.
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 */
public class MapperListener extends LifecycleMBeanBase
        implements ContainerListener, LifecycleListener {


    /** 日志  **/
    private static final Log log = LogFactory.getLog(MapperListener.class);


    // ----------------------------------------------------- Instance Variables
    /**
     * Mapper组件
     */
    private final Mapper mapper;

    /**
     * Service组件
     */
    private final Service service;

    /**
     * 错误日志管理器
     */
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);

    /**
     * 当前组件在jmx中命名空间
     */
    private final String domain = null;


    // ----------------------------------------------------------- Constructors

    /**
     * 实例化MapperListener组件
     */
    public MapperListener(Service service) {
        this.service = service;
        this.mapper = service.getMapper();
    }


    // ------------------------------------------------------- Lifecycle Methods

    /**
     * 组件启动模板方法实现
     */
    @Override
    public void startInternal() throws LifecycleException {

        /** 设置当前组件的状态LifecycleState.STARTING **/
        setState(LifecycleState.STARTING);

        /**  engine容器不存在，则MapperListener也不需要启动 **/
        Engine engine = service.getContainer();
        if (engine == null) {
            return;
        }

        /**
         * 查找engine.defaultHost属性对应组件在engine组件中是否存在，
         * 如果存在调用mapper.setDefaultHostName(defaultHost);
         * 设置到mapper的defaultHostName属性中
         */
        findDefaultHost();

        /** 对Container容器组件及其Container容器子组件添加当前对象作为监听 **/
        addListeners(engine);


        /** 遍历engine下所有Host组件,并将Host组件及其子组件注册到mapper中  **/
        Container[] conHosts = engine.findChildren();
        for (Container conHost : conHosts) {
            Host host = (Host) conHost;
            if (!LifecycleState.NEW.equals(host.getState())) {
                /** 将Host组件及其子组件注册到mapper中  **/
                registerHost(host);
            }
        }
    }


    @Override
    public void stopInternal() throws LifecycleException {
        /** 设置当前组件的状态LifecycleState.STOPPING **/
        setState(LifecycleState.STOPPING);

        /**  engine容器不存在，则MapperListener也不需要停止 **/
        Engine engine = service.getContainer();
        if (engine == null) {
            return;
        }

        /** 对Container容器组件及其Container容器子组件注销当前对象监听 **/
        removeListeners(engine);
    }


    @Override
    protected String getDomainInternal() {
        if (service instanceof LifecycleMBeanBase) {
            return ((LifecycleMBeanBase) service).getDomain();
        } else {
            return null;
        }
    }


    @Override
    protected String getObjectNameKeyProperties() {
        // Same as connector but Mapper rather than Connector
        return ("type=Mapper");
    }

    // --------------------------------------------- Container Listener methods

    /**
     * ContainerEvent事件处理
     */
    @Override
    public void containerEvent(ContainerEvent event) {

        /** ContainerEvent 表示添加一个子容器组件 **/
        if (Container.ADD_CHILD_EVENT.equals(event.getType())) {
            /** 获取添加子容器组件 **/
            Container child = (Container) event.getData();
            /** 为子容器组件及其子容器组件添加当前对象作为监听器 **/
            addListeners(child);
            /** 如果当前容器状态正常运行添加到mapper中 **/
            if (child.getState().isAvailable()) {
                if (child instanceof Host) {
                    registerHost((Host) child);
                } else if (child instanceof Context) {
                    registerContext((Context) child);
                } else if (child instanceof Wrapper) {
                    if (child.getParent().getState().isAvailable()) {
                        registerWrapper((Wrapper) child);
                    }
                }
            }
        }
        /** ContainerEvent 表示删除一个子容器组件 **/
        else if (Container.REMOVE_CHILD_EVENT.equals(event.getType())) {
            /** 获取添加子容器组件 **/
            Container child = (Container) event.getData();

            /** 为子容器组件及其子容器组件注销当前对象监听器 **/
            removeListeners(child);

        }
        /** ContainerEvent 表示添加一个别名 **/
        else if (Host.ADD_ALIAS_EVENT.equals(event.getType())) {
            /** 为添加mapper中Host组件添加别名 **/
            mapper.addHostAlias(((Host) event.getSource()).getName(),
                    event.getData().toString());
        }
        /** ContainerEvent 表示删除一个别名 **/
        else if (Host.REMOVE_ALIAS_EVENT.equals(event.getType())) {
            /** 为添加mapper中Host组件删除别名 **/
            mapper.removeHostAlias(event.getData().toString());
        }
        /** ContainerEvent 表示添加一个映射 **/
        else if (Wrapper.ADD_MAPPING_EVENT.equals(event.getType())) {
            /** 获取添加映射容器组件Wrapper **/
            Wrapper wrapper = (Wrapper) event.getSource();

            /** 获取Wrapper组件的父组件Context **/
            Context context = (Context) wrapper.getParent();

            /** 获取Context根路径 **/
            String contextPath = context.getPath();
            if ("/".equals(contextPath)) {
                contextPath = "";
            }
            /** 获取 context组件版本信息 **/
            String version = context.getWebappVersion();

            /** 获取wrapper父组件host名称 **/
            String hostName = context.getParent().getName();

            /** 获取wrapper组件的名称 **/
            String wrapperName = wrapper.getName();

            /** 获取映射配置名称 **/
            String mapping = (String) event.getData();
            /** 判断wrapper组件是否是jsp **/
            boolean jspWildCard = ("jsp".equals(wrapperName)
                    && mapping.endsWith("/*"));

            /** 添加一个wrapper组件映射到mapper中 **/
            mapper.addWrapper(hostName, contextPath, version, mapping, wrapper,
                    jspWildCard, context.isResourceOnlyServlet(wrapperName));
        }
        /** ContainerEvent 表示删除一个映射 **/
        else if (Wrapper.REMOVE_MAPPING_EVENT.equals(event.getType())) {
            /** 获取添加映射容器组件Wrapper **/
            Wrapper wrapper = (Wrapper) event.getSource();

            /** 获取Wrapper组件的父组件Context **/
            Context context = (Context) wrapper.getParent();

            /** 获取Context根路径 **/
            String contextPath = context.getPath();
            if ("/".equals(contextPath)) {
                contextPath = "";
            }

            /** 获取 context组件版本信息 **/
            String version = context.getWebappVersion();

            /** 获取wrapper父组件host名称 **/
            String hostName = context.getParent().getName();

            /** 获取映射配置名称 **/
            String mapping = (String) event.getData();

            /** 将一个wrapper组件映射从mapper中注销 **/
            mapper.removeWrapper(hostName, contextPath, version, mapping);
        }
        /** ContainerEvent 表示添加一个欢迎文件列表 **/
        else if (Context.ADD_WELCOME_FILE_EVENT.equals(event.getType())) {

            /** 获取添加一个欢迎文件列表容器组件Context **/
            Context context = (Context) event.getSource();

            /** 获取父容器组件Host名称 **/
            String hostName = context.getParent().getName();

            /** 获取Context根路径 **/
            String contextPath = context.getPath();
            if ("/".equals(contextPath)) {
                contextPath = "";
            }

            String welcomeFile = (String) event.getData();

            /** 注册一个欢迎文件列表到mapper中 **/
            mapper.addWelcomeFile(hostName, contextPath,
                    context.getWebappVersion(), welcomeFile);
        }
        /** ContainerEvent 表示删除一个欢迎文件列表 **/
        else if (Context.REMOVE_WELCOME_FILE_EVENT.equals(event.getType())) {

            /** 获取删除欢迎文件列表容器组件Context **/
            Context context = (Context) event.getSource();

            /** 获取父容器组件Host名称 **/
            String hostName = context.getParent().getName();

            /** 获取Context根路径 **/
            String contextPath = context.getPath();
            if ("/".equals(contextPath)) {
                contextPath = "";
            }

            String welcomeFile = (String) event.getData();

            /** 将一个欢迎文件列表从mapper中注销 **/
            mapper.removeWelcomeFile(hostName, contextPath,
                    context.getWebappVersion(), welcomeFile);
        }
        /** ContainerEvent 表示清除欢迎文件列表 **/
        else if (Context.CLEAR_WELCOME_FILES_EVENT.equals(event.getType())) {

            /** 获取删除欢迎文件列表容器组件Context **/
            Context context = (Context) event.getSource();

            /** 获取父容器组件Host名称 **/
            String hostName = context.getParent().getName();

            /** 获取Context根路径 **/
            String contextPath = context.getPath();
            if ("/".equals(contextPath)) {
                contextPath = "";
            }

            /**从mapper中注销所有欢迎文件列表 **/
            mapper.clearWelcomeFiles(hostName, contextPath,
                    context.getWebappVersion());
        }
    }

    /**
     * LifecycleEvent事件处理
     */
    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        /** AFTER_START_EVENT 表示启动一个容器 **/
        if (event.getType().equals(Lifecycle.AFTER_START_EVENT)) {
            Object obj = event.getSource();
            /** 判断容器类型注册到mapper **/
            if (obj instanceof Wrapper) {
                Wrapper w = (Wrapper) obj;
                if (w.getParent().getState().isAvailable()) {
                    registerWrapper(w);
                }
            } else if (obj instanceof Context) {
                Context c = (Context) obj;
                if (c.getParent().getState().isAvailable()) {
                    registerContext(c);
                }
            } else if (obj instanceof Host) {
                registerHost((Host) obj);
            }
        }
        /** AFTER_START_EVENT 表示停止一个容器操作之前 **/
        else if (event.getType().equals(Lifecycle.BEFORE_STOP_EVENT)) {
            Object obj = event.getSource();
            /** 判断容器类型从mapper注销 **/
            if (obj instanceof Wrapper) {
                unregisterWrapper((Wrapper) obj);
            } else if (obj instanceof Context) {
                unregisterContext((Context) obj);
            } else if (obj instanceof Host) {
                unregisterHost((Host) obj);
            }
        }
    }




    // ------------------------------------------------------ Protected Methods

    /**
     * 查找engine.defaultHost属性对应组件在engine组件中是否存在，
     * 如果存在调用mapper.setDefaultHostName(defaultHost);
     * 设置到mapper的defaultHostName属性中
     */
    private void findDefaultHost() {
        /** 获取engine.defaultHost属性 **/
        Engine engine = service.getContainer();
        String defaultHost = engine.getDefaultHost();

        boolean found = false;

        /** 在Engine中查找engine.defaultHost属性名称对应Host组件**/
        if (defaultHost != null && defaultHost.length() >0) {
            Container[] containers = engine.findChildren();

            for (Container container : containers) {
                Host host = (Host) container;
                if (defaultHost.equalsIgnoreCase(host.getName())) {
                    found = true;
                    break;
                }

                String[] aliases = host.findAliases();
                for (String alias : aliases) {
                    if (defaultHost.equalsIgnoreCase(alias)) {
                        found = true;
                        break;
                    }
                }
            }
        }

        /** 如果找到，将engine.defaultHost属性设置到mapper.defaultHostName属性上 **/
        if(found) {
            mapper.setDefaultHostName(defaultHost);
        } else {
            log.warn(sm.getString("mapperListener.unknownDefaultHost",
                    defaultHost, service));
        }
    }


    /**
     * 将Host组件及其子组件注册到mapper中
     */
    private void registerHost(Host host) {

        String[] aliases = host.findAliases();
        /** 将Host组件注册到mapper中 **/
        mapper.addHost(host.getName(), aliases, host);

        /** 将Host所有子组件Context注册到mapper中 **/
        for (Container container : host.findChildren()) {
            if (container.getState().isAvailable()) {
                registerContext((Context) container);
            }
        }
        if(log.isDebugEnabled()) {
            log.debug(sm.getString("mapperListener.registerHost",
                    host.getName(), domain, service));
        }
    }


    /**
     * 将Host组从mapper中注销
     */
    private void unregisterHost(Host host) {
        /** 获取host组件的名称 **/
        String hostname = host.getName();
        /** 将Host组从mapper中注销  **/
        mapper.removeHost(hostname);

        if(log.isDebugEnabled()) {
            log.debug(sm.getString("mapperListener.unregisterHost", hostname,
                    domain, service));
        }
    }


    /**
     * 将Wrapper组从mapper中注销
     */
    private void unregisterWrapper(Wrapper wrapper) {

        /** 获取wrapper父组件Context **/
        Context context = ((Context) wrapper.getParent());
        /** 获取context组件的根路径  **/
        String contextPath = context.getPath();
        /** 获取wrapper组件的名称  **/
        String wrapperName = wrapper.getName();

        if ("/".equals(contextPath)) {
            contextPath = "";
        }
        /** 获取 context组件版本信息 **/
        String version = context.getWebappVersion();
        /** 获取wrapper父组件host组件名称 **/
        String hostName = context.getParent().getName();
        /** 获取wrapper组件映射配置**/
        String[] mappings = wrapper.findMappings();

        /** 将wrapper组件所有映射配置从mapper中删除**/
        for (String mapping : mappings) {
            mapper.removeWrapper(hostName, contextPath, version,  mapping);
        }

        if(log.isDebugEnabled()) {
            log.debug(sm.getString("mapperListener.unregisterWrapper",
                    wrapperName, contextPath, service));
        }
    }

    /**
     * 将Wrapper组注册到mapper中
     */
    private void registerWrapper(Wrapper wrapper) {
        /** 获取wrapper父组件Context **/
        Context context = (Context) wrapper.getParent();

        /** 获取context组件的根路径  **/
        String contextPath = context.getPath();
        if ("/".equals(contextPath)) {
            contextPath = "";
        }

        /** 获取 context组件版本信息 **/
        String version = context.getWebappVersion();
        /** 获取wrapper父组件host名称 **/
        String hostName = context.getParent().getName();

        /** 将wrapper组件映射配置封装为WrapperMappingInfo，放入列表wrappers **/
        List<WrapperMappingInfo> wrappers = new ArrayList<>();
        prepareWrapperMappingInfo(context, wrapper, wrappers);

        /** 将wrapper组件所有映射配置注册到mapper**/
        mapper.addWrappers(hostName, contextPath, version, wrappers);

        if(log.isDebugEnabled()) {
            log.debug(sm.getString("mapperListener.registerWrapper",
                    wrapper.getName(), contextPath, service));
        }
    }


    /**
     * 将Context组注册到mapper中
     */
    private void registerContext(Context context) {

        /** 获取context组件的根路径  **/
        String contextPath = context.getPath();
        if ("/".equals(contextPath)) {
            contextPath = "";
        }

        /** 获取wrapper父组件host **/
        Host host = (Host)context.getParent();

        WebResourceRoot resources = context.getResources();
        String[] welcomeFiles = context.findWelcomeFiles();
        List<WrapperMappingInfo> wrappers = new ArrayList<>();

        /** 遍历Context组件所有子组件Wrapper，将wrapper组件映射配置注册到mapper中 **/
        for (Container container : context.findChildren()) {
            prepareWrapperMappingInfo(context, (Wrapper) container, wrappers);

            if(log.isDebugEnabled()) {
                log.debug(sm.getString("mapperListener.registerWrapper",
                        container.getName(), contextPath, service));
            }
        }

        /** 将Context组件所有欢迎列表文件注册到mapper中 **/
        mapper.addContextVersion(host.getName(), host, contextPath,
                context.getWebappVersion(), context, welcomeFiles, resources,
                wrappers);

        if(log.isDebugEnabled()) {
            log.debug(sm.getString("mapperListener.registerContext",
                    contextPath, service));
        }
    }


    /**
     * 将Context组件从mapper中注销
     */
    private void unregisterContext(Context context) {

        /** 获取context组件的根路径  **/
        String contextPath = context.getPath();
        if ("/".equals(contextPath)) {
            contextPath = "";
        }

        /** 获取wrapper父组件host名称 **/
        String hostName = context.getParent().getName();

        /** 如果context组件暂停则暂停注册到mapper中context **/
        if (context.getPaused()) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("mapperListener.pauseContext",
                        contextPath, service));
            }

            mapper.pauseContextVersion(context, hostName, contextPath,
                    context.getWebappVersion());
        } else {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("mapperListener.unregisterContext",
                        contextPath, service));
            }

            mapper.removeContextVersion(context, hostName, contextPath,
                    context.getWebappVersion());
        }
    }

    /**
     * 将wrapper组件映射配置封装为WrapperMappingInfo，放入列表wrappers
     *
     * @param context
     * @param wrapper
     * @param wrappers
     */
    private void prepareWrapperMappingInfo(Context context, Wrapper wrapper,
            List<WrapperMappingInfo> wrappers) {
        String wrapperName = wrapper.getName();
        /** 判断Wrapper组件是否用来表示Servlet资源**/
        boolean resourceOnly = context.isResourceOnlyServlet(wrapperName);
        String[] mappings = wrapper.findMappings();
        for (String mapping : mappings) {
            /** 判断Wrapper组件是否用来表示JSP **/
            boolean jspWildCard = (wrapperName.equals("jsp")
                                   && mapping.endsWith("/*"));
            wrappers.add(new WrapperMappingInfo(mapping, wrapper, jspWildCard,
                    resourceOnly));
        }
    }


    /**
     * 对Container容器组件及其Container容器子组件添加监听
     */
    private void addListeners(Container container) {
        container.addContainerListener(this);
        container.addLifecycleListener(this);
        for (Container child : container.findChildren()) {
            addListeners(child);
        }
    }


    /**
     * 对Container容器组件及其Container容器子组件删除监听
     */
    private void removeListeners(Container container) {
        container.removeContainerListener(this);
        container.removeLifecycleListener(this);
        for (Container child : container.findChildren()) {
            removeListeners(child);
        }
    }
}
