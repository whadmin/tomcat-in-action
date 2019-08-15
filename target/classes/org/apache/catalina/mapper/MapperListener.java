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


    /** ��־  **/
    private static final Log log = LogFactory.getLog(MapperListener.class);


    // ----------------------------------------------------- Instance Variables
    /**
     * Mapper���
     */
    private final Mapper mapper;

    /**
     * Service���
     */
    private final Service service;

    /**
     * ������־������
     */
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);

    /**
     * ��ǰ�����jmx�������ռ�
     */
    private final String domain = null;


    // ----------------------------------------------------------- Constructors

    /**
     * ʵ����MapperListener���
     */
    public MapperListener(Service service) {
        this.service = service;
        this.mapper = service.getMapper();
    }


    // ------------------------------------------------------- Lifecycle Methods

    /**
     * �������ģ�巽��ʵ��
     */
    @Override
    public void startInternal() throws LifecycleException {

        /** ���õ�ǰ�����״̬LifecycleState.STARTING **/
        setState(LifecycleState.STARTING);

        /**  engine���������ڣ���MapperListenerҲ����Ҫ���� **/
        Engine engine = service.getContainer();
        if (engine == null) {
            return;
        }

        /**
         * ����engine.defaultHost���Զ�Ӧ�����engine������Ƿ���ڣ�
         * ������ڵ���mapper.setDefaultHostName(defaultHost);
         * ���õ�mapper��defaultHostName������
         */
        findDefaultHost();

        /** ��Container�����������Container�����������ӵ�ǰ������Ϊ���� **/
        addListeners(engine);


        /** ����engine������Host���,����Host������������ע�ᵽmapper��  **/
        Container[] conHosts = engine.findChildren();
        for (Container conHost : conHosts) {
            Host host = (Host) conHost;
            if (!LifecycleState.NEW.equals(host.getState())) {
                /** ��Host������������ע�ᵽmapper��  **/
                registerHost(host);
            }
        }
    }


    @Override
    public void stopInternal() throws LifecycleException {
        /** ���õ�ǰ�����״̬LifecycleState.STOPPING **/
        setState(LifecycleState.STOPPING);

        /**  engine���������ڣ���MapperListenerҲ����Ҫֹͣ **/
        Engine engine = service.getContainer();
        if (engine == null) {
            return;
        }

        /** ��Container�����������Container���������ע����ǰ������� **/
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
     * ContainerEvent�¼�����
     */
    @Override
    public void containerEvent(ContainerEvent event) {

        /** ContainerEvent ��ʾ���һ����������� **/
        if (Container.ADD_CHILD_EVENT.equals(event.getType())) {
            /** ��ȡ������������ **/
            Container child = (Container) event.getData();
            /** Ϊ������������������������ӵ�ǰ������Ϊ������ **/
            addListeners(child);
            /** �����ǰ����״̬����������ӵ�mapper�� **/
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
        /** ContainerEvent ��ʾɾ��һ����������� **/
        else if (Container.REMOVE_CHILD_EVENT.equals(event.getType())) {
            /** ��ȡ������������ **/
            Container child = (Container) event.getData();

            /** Ϊ����������������������ע����ǰ��������� **/
            removeListeners(child);

        }
        /** ContainerEvent ��ʾ���һ������ **/
        else if (Host.ADD_ALIAS_EVENT.equals(event.getType())) {
            /** Ϊ���mapper��Host�����ӱ��� **/
            mapper.addHostAlias(((Host) event.getSource()).getName(),
                    event.getData().toString());
        }
        /** ContainerEvent ��ʾɾ��һ������ **/
        else if (Host.REMOVE_ALIAS_EVENT.equals(event.getType())) {
            /** Ϊ���mapper��Host���ɾ������ **/
            mapper.removeHostAlias(event.getData().toString());
        }
        /** ContainerEvent ��ʾ���һ��ӳ�� **/
        else if (Wrapper.ADD_MAPPING_EVENT.equals(event.getType())) {
            /** ��ȡ���ӳ���������Wrapper **/
            Wrapper wrapper = (Wrapper) event.getSource();

            /** ��ȡWrapper����ĸ����Context **/
            Context context = (Context) wrapper.getParent();

            /** ��ȡContext��·�� **/
            String contextPath = context.getPath();
            if ("/".equals(contextPath)) {
                contextPath = "";
            }
            /** ��ȡ context����汾��Ϣ **/
            String version = context.getWebappVersion();

            /** ��ȡwrapper�����host���� **/
            String hostName = context.getParent().getName();

            /** ��ȡwrapper��������� **/
            String wrapperName = wrapper.getName();

            /** ��ȡӳ���������� **/
            String mapping = (String) event.getData();
            /** �ж�wrapper����Ƿ���jsp **/
            boolean jspWildCard = ("jsp".equals(wrapperName)
                    && mapping.endsWith("/*"));

            /** ���һ��wrapper���ӳ�䵽mapper�� **/
            mapper.addWrapper(hostName, contextPath, version, mapping, wrapper,
                    jspWildCard, context.isResourceOnlyServlet(wrapperName));
        }
        /** ContainerEvent ��ʾɾ��һ��ӳ�� **/
        else if (Wrapper.REMOVE_MAPPING_EVENT.equals(event.getType())) {
            /** ��ȡ���ӳ���������Wrapper **/
            Wrapper wrapper = (Wrapper) event.getSource();

            /** ��ȡWrapper����ĸ����Context **/
            Context context = (Context) wrapper.getParent();

            /** ��ȡContext��·�� **/
            String contextPath = context.getPath();
            if ("/".equals(contextPath)) {
                contextPath = "";
            }

            /** ��ȡ context����汾��Ϣ **/
            String version = context.getWebappVersion();

            /** ��ȡwrapper�����host���� **/
            String hostName = context.getParent().getName();

            /** ��ȡӳ���������� **/
            String mapping = (String) event.getData();

            /** ��һ��wrapper���ӳ���mapper��ע�� **/
            mapper.removeWrapper(hostName, contextPath, version, mapping);
        }
        /** ContainerEvent ��ʾ���һ����ӭ�ļ��б� **/
        else if (Context.ADD_WELCOME_FILE_EVENT.equals(event.getType())) {

            /** ��ȡ���һ����ӭ�ļ��б��������Context **/
            Context context = (Context) event.getSource();

            /** ��ȡ���������Host���� **/
            String hostName = context.getParent().getName();

            /** ��ȡContext��·�� **/
            String contextPath = context.getPath();
            if ("/".equals(contextPath)) {
                contextPath = "";
            }

            String welcomeFile = (String) event.getData();

            /** ע��һ����ӭ�ļ��б�mapper�� **/
            mapper.addWelcomeFile(hostName, contextPath,
                    context.getWebappVersion(), welcomeFile);
        }
        /** ContainerEvent ��ʾɾ��һ����ӭ�ļ��б� **/
        else if (Context.REMOVE_WELCOME_FILE_EVENT.equals(event.getType())) {

            /** ��ȡɾ����ӭ�ļ��б��������Context **/
            Context context = (Context) event.getSource();

            /** ��ȡ���������Host���� **/
            String hostName = context.getParent().getName();

            /** ��ȡContext��·�� **/
            String contextPath = context.getPath();
            if ("/".equals(contextPath)) {
                contextPath = "";
            }

            String welcomeFile = (String) event.getData();

            /** ��һ����ӭ�ļ��б��mapper��ע�� **/
            mapper.removeWelcomeFile(hostName, contextPath,
                    context.getWebappVersion(), welcomeFile);
        }
        /** ContainerEvent ��ʾ�����ӭ�ļ��б� **/
        else if (Context.CLEAR_WELCOME_FILES_EVENT.equals(event.getType())) {

            /** ��ȡɾ����ӭ�ļ��б��������Context **/
            Context context = (Context) event.getSource();

            /** ��ȡ���������Host���� **/
            String hostName = context.getParent().getName();

            /** ��ȡContext��·�� **/
            String contextPath = context.getPath();
            if ("/".equals(contextPath)) {
                contextPath = "";
            }

            /**��mapper��ע�����л�ӭ�ļ��б� **/
            mapper.clearWelcomeFiles(hostName, contextPath,
                    context.getWebappVersion());
        }
    }

    /**
     * LifecycleEvent�¼�����
     */
    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        /** AFTER_START_EVENT ��ʾ����һ������ **/
        if (event.getType().equals(Lifecycle.AFTER_START_EVENT)) {
            Object obj = event.getSource();
            /** �ж���������ע�ᵽmapper **/
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
        /** AFTER_START_EVENT ��ʾֹͣһ����������֮ǰ **/
        else if (event.getType().equals(Lifecycle.BEFORE_STOP_EVENT)) {
            Object obj = event.getSource();
            /** �ж��������ʹ�mapperע�� **/
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
     * ����engine.defaultHost���Զ�Ӧ�����engine������Ƿ���ڣ�
     * ������ڵ���mapper.setDefaultHostName(defaultHost);
     * ���õ�mapper��defaultHostName������
     */
    private void findDefaultHost() {
        /** ��ȡengine.defaultHost���� **/
        Engine engine = service.getContainer();
        String defaultHost = engine.getDefaultHost();

        boolean found = false;

        /** ��Engine�в���engine.defaultHost�������ƶ�ӦHost���**/
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

        /** ����ҵ�����engine.defaultHost�������õ�mapper.defaultHostName������ **/
        if(found) {
            mapper.setDefaultHostName(defaultHost);
        } else {
            log.warn(sm.getString("mapperListener.unknownDefaultHost",
                    defaultHost, service));
        }
    }


    /**
     * ��Host������������ע�ᵽmapper��
     */
    private void registerHost(Host host) {

        String[] aliases = host.findAliases();
        /** ��Host���ע�ᵽmapper�� **/
        mapper.addHost(host.getName(), aliases, host);

        /** ��Host���������Contextע�ᵽmapper�� **/
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
     * ��Host���mapper��ע��
     */
    private void unregisterHost(Host host) {
        /** ��ȡhost��������� **/
        String hostname = host.getName();
        /** ��Host���mapper��ע��  **/
        mapper.removeHost(hostname);

        if(log.isDebugEnabled()) {
            log.debug(sm.getString("mapperListener.unregisterHost", hostname,
                    domain, service));
        }
    }


    /**
     * ��Wrapper���mapper��ע��
     */
    private void unregisterWrapper(Wrapper wrapper) {

        /** ��ȡwrapper�����Context **/
        Context context = ((Context) wrapper.getParent());
        /** ��ȡcontext����ĸ�·��  **/
        String contextPath = context.getPath();
        /** ��ȡwrapper���������  **/
        String wrapperName = wrapper.getName();

        if ("/".equals(contextPath)) {
            contextPath = "";
        }
        /** ��ȡ context����汾��Ϣ **/
        String version = context.getWebappVersion();
        /** ��ȡwrapper�����host������� **/
        String hostName = context.getParent().getName();
        /** ��ȡwrapper���ӳ������**/
        String[] mappings = wrapper.findMappings();

        /** ��wrapper�������ӳ�����ô�mapper��ɾ��**/
        for (String mapping : mappings) {
            mapper.removeWrapper(hostName, contextPath, version,  mapping);
        }

        if(log.isDebugEnabled()) {
            log.debug(sm.getString("mapperListener.unregisterWrapper",
                    wrapperName, contextPath, service));
        }
    }

    /**
     * ��Wrapper��ע�ᵽmapper��
     */
    private void registerWrapper(Wrapper wrapper) {
        /** ��ȡwrapper�����Context **/
        Context context = (Context) wrapper.getParent();

        /** ��ȡcontext����ĸ�·��  **/
        String contextPath = context.getPath();
        if ("/".equals(contextPath)) {
            contextPath = "";
        }

        /** ��ȡ context����汾��Ϣ **/
        String version = context.getWebappVersion();
        /** ��ȡwrapper�����host���� **/
        String hostName = context.getParent().getName();

        /** ��wrapper���ӳ�����÷�װΪWrapperMappingInfo�������б�wrappers **/
        List<WrapperMappingInfo> wrappers = new ArrayList<>();
        prepareWrapperMappingInfo(context, wrapper, wrappers);

        /** ��wrapper�������ӳ������ע�ᵽmapper**/
        mapper.addWrappers(hostName, contextPath, version, wrappers);

        if(log.isDebugEnabled()) {
            log.debug(sm.getString("mapperListener.registerWrapper",
                    wrapper.getName(), contextPath, service));
        }
    }


    /**
     * ��Context��ע�ᵽmapper��
     */
    private void registerContext(Context context) {

        /** ��ȡcontext����ĸ�·��  **/
        String contextPath = context.getPath();
        if ("/".equals(contextPath)) {
            contextPath = "";
        }

        /** ��ȡwrapper�����host **/
        Host host = (Host)context.getParent();

        WebResourceRoot resources = context.getResources();
        String[] welcomeFiles = context.findWelcomeFiles();
        List<WrapperMappingInfo> wrappers = new ArrayList<>();

        /** ����Context������������Wrapper����wrapper���ӳ������ע�ᵽmapper�� **/
        for (Container container : context.findChildren()) {
            prepareWrapperMappingInfo(context, (Wrapper) container, wrappers);

            if(log.isDebugEnabled()) {
                log.debug(sm.getString("mapperListener.registerWrapper",
                        container.getName(), contextPath, service));
            }
        }

        /** ��Context������л�ӭ�б��ļ�ע�ᵽmapper�� **/
        mapper.addContextVersion(host.getName(), host, contextPath,
                context.getWebappVersion(), context, welcomeFiles, resources,
                wrappers);

        if(log.isDebugEnabled()) {
            log.debug(sm.getString("mapperListener.registerContext",
                    contextPath, service));
        }
    }


    /**
     * ��Context�����mapper��ע��
     */
    private void unregisterContext(Context context) {

        /** ��ȡcontext����ĸ�·��  **/
        String contextPath = context.getPath();
        if ("/".equals(contextPath)) {
            contextPath = "";
        }

        /** ��ȡwrapper�����host���� **/
        String hostName = context.getParent().getName();

        /** ���context�����ͣ����ͣע�ᵽmapper��context **/
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
     * ��wrapper���ӳ�����÷�װΪWrapperMappingInfo�������б�wrappers
     *
     * @param context
     * @param wrapper
     * @param wrappers
     */
    private void prepareWrapperMappingInfo(Context context, Wrapper wrapper,
            List<WrapperMappingInfo> wrappers) {
        String wrapperName = wrapper.getName();
        /** �ж�Wrapper����Ƿ�������ʾServlet��Դ**/
        boolean resourceOnly = context.isResourceOnlyServlet(wrapperName);
        String[] mappings = wrapper.findMappings();
        for (String mapping : mappings) {
            /** �ж�Wrapper����Ƿ�������ʾJSP **/
            boolean jspWildCard = (wrapperName.equals("jsp")
                                   && mapping.endsWith("/*"));
            wrappers.add(new WrapperMappingInfo(mapping, wrapper, jspWildCard,
                    resourceOnly));
        }
    }


    /**
     * ��Container�����������Container�����������Ӽ���
     */
    private void addListeners(Container container) {
        container.addContainerListener(this);
        container.addLifecycleListener(this);
        for (Container child : container.findChildren()) {
            addListeners(child);
        }
    }


    /**
     * ��Container�����������Container���������ɾ������
     */
    private void removeListeners(Container container) {
        container.removeContainerListener(this);
        container.removeLifecycleListener(this);
        for (Container child : container.findChildren()) {
            removeListeners(child);
        }
    }
}
