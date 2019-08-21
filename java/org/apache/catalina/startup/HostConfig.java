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
package org.apache.catalina.startup;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.DistributedManager;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Manager;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.security.DeployXmlPermission;
import org.apache.catalina.util.ContextName;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.UriUtil;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.res.StringManager;


public class HostConfig implements LifecycleListener {

    private static final Log log = LogFactory.getLog(HostConfig.class);

    /**
     * 管理打印日志模板组件
     */
    protected static final StringManager sm = StringManager.getManager(HostConfig.class);

    /**
     * 文件修改时间的分辨率
     */
    protected static final long FILE_MODIFICATION_RESOLUTION_MS = 1000;


    /**
     * 组件Context实现类
     */
    protected String contextClass = "org.apache.catalina.core.StandardContext";


    /**
     * 关联监听host组件
     */
    protected Host host = null;


    /**
     * 当前对象HostConfig 注册到JMX ObjectName
     */
    protected ObjectName oname = null;


    /**
     * 是否要禁止应用程序中定义/META-INF/context.xml
     */
    protected boolean deployXML = false;


    /**
     * 如果在应用程序中定义了/META-INF/context.xml，是否要拷贝到$catalinaBase/xmlBase目录下
     */
    protected boolean copyXML = false;


    /**
     * 是否解压war包种应用程序在执行，默认为true
     */
    protected boolean unpackWARs = false;


    /**
     * deployed Map存储已经部署context上下文。key中存储context的名称，value中存储DeployedApplication类对象
     * DeployedApplication类用来描述部署context相关信息
     */
    protected final Map<String, DeployedApplication> deployed =
            new ConcurrentHashMap<>();


    /**
     * 存储正在运行且禁止（部署/卸载/重新部署）的Context列表
     */
    protected final ArrayList<String> serviced = new ArrayList<>();


    /**
     * digester对象，负责管理解析context标签规则
     */
    protected Digester digester = createDigester(contextClass);


    /**
     * digester使用时同步锁对象
     */
    private final Object digesterLock = new Object();

    /**
     * 存储用忽略部署应用程序war文件
     */
    protected final Set<String> invalidWars = new HashSet<>();

    // ------------------------------------------------------------- Properties


    public String getContextClass() {
        return (this.contextClass);
    }


    public void setContextClass(String contextClass) {
        String oldContextClass = this.contextClass;
        this.contextClass = contextClass;
        if (!oldContextClass.equals(contextClass)) {
            synchronized (digesterLock) {
                /** 重新创建digester **/
                digester = createDigester(getContextClass());
            }
        }
    }


    public boolean isDeployXML() {
        return (this.deployXML);
    }


    public void setDeployXML(boolean deployXML) {
        this.deployXML = deployXML;
    }


    private boolean isDeployThisXML(File docBase, ContextName cn) {
        boolean deployThisXML = isDeployXML();
        if (Globals.IS_SECURITY_ENABLED && !deployThisXML) {
            // When running under a SecurityManager, deployXML may be overridden
            // on a per Context basis by the granting of a specific permission
            Policy currentPolicy = Policy.getPolicy();
            if (currentPolicy != null) {
                URL contextRootUrl;
                try {
                    contextRootUrl = docBase.toURI().toURL();
                    CodeSource cs = new CodeSource(contextRootUrl, (Certificate[]) null);
                    PermissionCollection pc = currentPolicy.getPermissions(cs);
                    Permission p = new DeployXmlPermission(cn.getBaseName());
                    if (pc.implies(p)) {
                        deployThisXML = true;
                    }
                } catch (MalformedURLException e) {
                    // Should never happen
                    log.warn("hostConfig.docBaseUrlInvalid", e);
                }
            }
        }

        return deployThisXML;
    }


    public boolean isCopyXML() {
        return (this.copyXML);
    }


    public void setCopyXML(boolean copyXML) {
        this.copyXML= copyXML;
    }


    public boolean isUnpackWARs() {
        return (this.unpackWARs);
    }


    public void setUnpackWARs(boolean unpackWARs) {
        this.unpackWARs = unpackWARs;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 监听host组件生命周期时间
     */
    @Override
    public void lifecycleEvent(LifecycleEvent event) {

        /**  获取host组件配置属性设置HostConfig对应属性中  **/
        try {
            host = (Host) event.getLifecycle();
            if (host instanceof StandardHost) {
                setCopyXML(((StandardHost) host).isCopyXML());
                setDeployXML(((StandardHost) host).isDeployXML());
                setUnpackWARs(((StandardHost) host).isUnpackWARs());
                setContextClass(((StandardHost) host).getContextClass());
            }
        } catch (ClassCastException e) {
            log.error(sm.getString("hostConfig.cce", event.getLifecycle()), e);
            return;
        }

        /** 处理相应的host组件生命周期事件 **/
        if (event.getType().equals(Lifecycle.PERIODIC_EVENT)) {
            /**
             * 监听host组件周期性任务触发事件，
             * 获取host组件下所有部署context组件，检查每一个context组件中需要监听资源目录是否发生变更，
             * 如果发生变更则触发其context重新加载或启动
             **/
            check();
        } else if (event.getType().equals(Lifecycle.BEFORE_START_EVENT)) {
            /**
             * 监听host组件启动前触发事件，
             * 创建host组件定义appbase和xmlbase路径对应的目录文件
             **/
            beforeStart();
        } else if (event.getType().equals(Lifecycle.START_EVENT)) {
            /**
             * 监听host组件启动事件，启动hostConfig组件.
             * 1 将HostConfig对象注册到Jmx bean中
             * 2 如果Host组件启用了在启动时自动部署appBase，xmlBase目录下应用程序或静态资源
             * 则扫描appBase，xmlBase目录下应用程序或静态资源，构建context组件，添加到host组件，并启动*/
            start();
        } else if (event.getType().equals(Lifecycle.STOP_EVENT)) {
            /**
             * 监听host组件停止事件，停止hostConfig组件.
             * 将HostConfig对象从Jmx bean中注销
             */
            stop();
        }
    }


    /**
     * 向server列表中添加context名称
     */
    public synchronized void addServiced(String name) {
        serviced.add(name);
    }


    /**
     * server列表中是否包含指定context名称
     */
    public synchronized boolean isServiced(String name) {
        return (serviced.contains(name));
    }


    /**
     * 向server列表中删除指定context名称
     */
    public synchronized void removeServiced(String name) {
        serviced.remove(name);
    }


    /**
     * 获取指定context部署的时间
     */
    public long getDeploymentTime(String name) {
        DeployedApplication app = deployed.get(name);
        if (app == null) {
            return 0L;
        }
        return app.timestamp;
    }


    /**
     * 指定context是否已部署
     */
    public boolean isDeployed(String name) {
        return deployed.containsKey(name);
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 创建Digester对象，负责管理解析context标签规则
     */
    protected static Digester createDigester(String contextClassName) {
        Digester digester = new Digester();
        digester.setValidating(false);
        // Add object creation rule
        digester.addObjectCreate("Context", contextClassName, "className");
        // Set the properties on that object (it doesn't matter if extra
        // properties are set)
        digester.addSetProperties("Context");
        return (digester);
    }


    /**
     * 获取指定路径文件对象，new File(CatalinaBase/path)
     */
    protected File returnCanonicalPath(String path) {
        File file = new File(path);
        if (!file.isAbsolute())
            file = new File(host.getCatalinaBase(), path);
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file;
        }
    }


    /**
     * 获取配置文件绝对路径
     */
    public String getConfigBaseName() {
        return host.getConfigBaseFile().getAbsolutePath();
    }


    /**
     * 扫描appBase，xmlBase目录下应用程序或静态资源，构建context组件，添加到host组件，并启动
     */
    protected void deployApps() {

        /** 获取host组件部署web应用程序,或静态资源文件的根的目录文件  **/
        File appBase = host.getAppBaseFile();
        /** 获取host配置根的目录文件 **/
        File configBase = host.getConfigBaseFile();
        /** 获取host容器组件deployIgnore正则表达式过滤appBase子文件**/
        String[] filteredAppPaths = filterAppPaths(appBase.list());

        /** 将xmlBase目录xml配置文件表示静态资源文件构建为context容器组件,添加（部署）到host子容器组件，并启动**/
        deployDescriptors(configBase, configBase.list());
        /** 将appBase目录web应用程序war包构建为context容器组件,添加（部署）到host子容器组件，并启动 **/
        deployWARs(appBase, filteredAppPaths);
        /** 将appBase目录静态资源文件构建为context容器组件,添加（部署）到host子容器组件，并启动 **/
        deployDirectories(appBase, filteredAppPaths);
    }

    /**
     * 遍历$catalinaBase/xmlBase目录下xml配置文件，将每个xml配置文件对应静态文件部署动作，
     * 封装为一个任务对象DeployDescriptor，交给线程池处理
     */
    protected void deployDescriptors(File configBase, String[] files) {

        if (files == null)
            return;

        /** 获取线程池 **/
        ExecutorService es = host.getStartStopExecutor();
        List<Future<?>> results = new ArrayList<>();

        /**
         * 遍历$catalinaBase/xmlBase目录下xml文件，将每个xml文件部署动作，
         * 封装为一个任务对象DeployDescriptor，交给线程池处理
         **/
        for (int i = 0; i < files.length; i++) {
            File contextXml = new File(configBase, files[i]);

            if (files[i].toLowerCase(Locale.ENGLISH).endsWith(".xml")) {
                ContextName cn = new ContextName(files[i], true);

                if (isServiced(cn.getName()) || deploymentExists(cn.getName()))
                    continue;

                results.add(
                        es.submit(new DeployDescriptor(this, cn, contextXml)));
            }
        }

        /** 等待线程池异步处理结果 **/
        for (Future<?> result : results) {
            try {
                /** 阻塞等待异步处理结果 **/
                result.get();
            } catch (Exception e) {
                log.error(sm.getString(
                        "hostConfig.deployDescriptor.threaded.error"), e);
            }
        }
    }


    /**
     * 遍历$appBase目录下应用程序war包文件，将每个应用程序war包文件部署动作，
     * 封装为一个任务对象DeployWar，交给线程池处理
     */
    protected void deployWARs(File appBase, String[] files) {

        if (files == null)
            return;

        /** 获取线程池 **/
        ExecutorService es = host.getStartStopExecutor();
        List<Future<?>> results = new ArrayList<>();

        /** 遍历appBase目录下文件 **/
        for (int i = 0; i < files.length; i++) {

            /**  忽略META-INF文件  **/
            if (files[i].equalsIgnoreCase("META-INF"))
                continue;

            /**  忽略WEB-INF文件  **/
            if (files[i].equalsIgnoreCase("WEB-INF"))
                continue;

            /** 实例化appBase目录下war文件对象**/
            File war = new File(appBase, files[i]);

            /**
             * 如果war文件存在，且扩展名称为.war，且不存在于invalidWars中
             * invalidWars存储用忽略部署应用程序war文件
             * **/
            if (files[i].toLowerCase(Locale.ENGLISH).endsWith(".war") &&
                    war.isFile() && !invalidWars.contains(files[i]) ) {

                /**
                 * 已war包名称作为context完整名称实例化ContextName对象
                 * ContextName用来表示context名称，
                 *
                 * name 表示完整名称,部署在appBase目录下应用程序name=war包文件名=path##version
                 * baseName,表示根据name过滤特殊字符后基础名称
                 * path 表示context部署根路径，从name中解析获取
                 * version 同一个war包程序可以部署多个版本到host,只需要修改名称为test##3.war ##后3表示版本号
                 *
                 * **/
                ContextName cn = new ContextName(files[i], true);

                /**
                 * 过滤掉存在于serviced列表中war包应用程序
                 * serviced列表中存储正在运行且禁止（部署/卸载/重新部署）的Context
                 * **/
                if (isServiced(cn.getName())) {
                    continue;
                }
                /**
                 * 过滤掉已经部署到host中war包应用程序，如果存在则跳过
                 * 并更新war包应用程序对应DeployedApplication对象loggedDirWarning属性
                 *
                 * loggedDirWarning属性表示是否不解压war包直接运行war包中程序
                 * **/
                if (deploymentExists(cn.getName())) {

                    DeployedApplication app = deployed.get(cn.getName());

                    /** 获取是否直接运行war包中程序 **/
                    boolean unpackWAR = unpackWARs;
                    if (unpackWAR && host.findChild(cn.getName()) instanceof StandardContext) {
                        unpackWAR = ((StandardContext) host.findChild(cn.getName())).getUnpackWAR();
                    }

                    if (!unpackWAR && app != null) {
                        // Need to check for a directory that should not be
                        // there
                        File dir = new File(appBase, cn.getBaseName());
                        if (dir.exists()) {
                            if (!app.loggedDirWarning) {
                                log.warn(sm.getString(
                                        "hostConfig.deployWar.hiddenDir",
                                        dir.getAbsoluteFile(),
                                        war.getAbsoluteFile()));
                                app.loggedDirWarning = true;
                            }
                        } else {
                            app.loggedDirWarning = false;
                        }
                    }
                    /** 过滤 **/
                    continue;
                }

                /** 过滤掉不符合规则context 基础名称 **/
                if (!validateContextPath(appBase, cn.getBaseName())) {
                    log.error(sm.getString(
                            "hostConfig.illegalWarName", files[i]));
                    invalidWars.add(files[i]);
                    continue;
                }

                /** 遍历$appBase目录下应用程序war包文件，将每个应用程序war包文件部署动作，
                 * 封装为一个任务对象DeployWar，交给线程池处理 **/
                results.add(es.submit(new DeployWar(this, cn, war)));
            }
        }
        /** 等待线程池异步处理结果 **/
        for (Future<?> result : results) {
            try {
                /** 阻塞等待异步处理结果 **/
                result.get();
            } catch (Exception e) {
                log.error(sm.getString(
                        "hostConfig.deployWar.threaded.error"), e);
            }
        }
    }


    /**
     * 遍历$appBase目录下静态资源文件，将静态资源文件部署动作，
     * 封装为一个任务对象DeployDirectory，交给线程池处理
     */
    protected void deployDirectories(File appBase, String[] files) {

        if (files == null)
            return;

        ExecutorService es = host.getStartStopExecutor();
        List<Future<?>> results = new ArrayList<>();
        /** 遍历$appBase目录下静态资源文件 **/
        for (int i = 0; i < files.length; i++) {
            /**  忽略META-INF文件  **/
            if (files[i].equalsIgnoreCase("META-INF"))
                continue;

            /**  忽略WEB-INF文件  **/
            if (files[i].equalsIgnoreCase("WEB-INF"))
                continue;

            /** 实例化appBase目录下静态资源目录文件**/
            File dir = new File(appBase, files[i]);
            /** 判断是否是目录 **/
            if (dir.isDirectory()) {
                ContextName cn = new ContextName(files[i], false);

                /**
                 * 过滤掉存在于serviced列表中静态资源文件
                 * 过滤掉已经部署到host中静态资源文件
                 * **/
                if (isServiced(cn.getName()) || deploymentExists(cn.getName()))
                    continue;

                /** 将静态资源文件部署动作，
                 * 封装为一个任务对象DeployDirectory，交给线程池处理 **/
                results.add(es.submit(new DeployDirectory(this, cn, dir)));
            }
        }
         /** 等待线程池异步处理结果 **/
        for (Future<?> result : results) {
            try {
                /** 阻塞等待异步处理结果 **/
                result.get();
            } catch (Exception e) {
                log.error(sm.getString(
                        "hostConfig.deployDir.threaded.error"), e);
            }
        }
    }


    /**
     * 将指定应用程序或静态资源部署到Host
     */
    protected void deployApps(String name) {

        /** 获取host组件部署web应用程序,或静态资源文件的根的目录文件  **/
        File appBase = host.getAppBaseFile();
        /** 获取host配置根的目录文件 **/
        File configBase = host.getConfigBaseFile();
        /** 实例化ContextName对象 **/
        ContextName cn = new ContextName(name, false);
        /** 获取context 基础名称**/
        String baseName = cn.getBaseName();

        /** 如果应用程序或静态资源已经部署到host中返回 **/
        if (deploymentExists(cn.getName())) {
            return;
        }

        /** 判断$catalinaBase/xmlBase目录是否存在baseName + ".xml"文件 **/
        File xml = new File(configBase, baseName + ".xml");
        if (xml.exists()) {
            /** 将$catalinaBase/xmlBase/baseName.xml文件表示的静态资源部署到host **/
            deployDescriptor(cn, xml);
            return;
        }
        /** 判断appBase目录是否存在baseName + ".war"文件 **/
        File war = new File(appBase, baseName + ".war");
        if (war.exists()) {
            /** 将appBase/baseName.war文件表示的应用程序部署到host **/
            deployWAR(cn, war);
            return;
        }
        /** 判断appBase目录是否存在appBase + baseName目录文件 **/
        File dir = new File(appBase, baseName);
        if (dir.exists())
            /** 将appBase/baseName文件目录示的静态资源部署到host **/
            deployDirectory(cn, dir);
    }

    /**
     * 将$catalinaBase/xmlBase目录下xml配置文件表示的静态资源部署到host
     */
    protected void deployDescriptor(ContextName cn, File contextXml) {

        DeployedApplication deployedApp =
                new DeployedApplication(cn.getName(), true);

        long startTime = 0;

        if(log.isInfoEnabled()) {
           startTime = System.currentTimeMillis();
           log.info(sm.getString("hostConfig.deployDescriptor",
                    contextXml.getAbsolutePath()));
        }

        Context context = null;
        /** 是否存在外部war包 **/
        boolean isExternalWar = false;
        /** 是否存在外部资源 **/
        boolean isExternal = false;
        /** 网络文档根路径**/
        File expandedDocBase = null;


        try (FileInputStream fis = new FileInputStream(contextXml)) {

            /** 使用digester解析xml实例context组件 **/
            synchronized (digesterLock) {
                try {
                    context = (Context) digester.parse(fis);
                } catch (Exception e) {
                    log.error(sm.getString(
                            "hostConfig.deployDescriptor.error",
                            contextXml.getAbsolutePath()), e);
                } finally {
                    digester.reset();
                    if (context == null) {
                        context = new FailedContext();
                    }
                }
            }

            /** 实例化contextConfig对象，添加到context组件生命周期监听器列表中 **/
            Class<?> clazz = Class.forName(host.getConfigClass());
            LifecycleListener listener = (LifecycleListener) clazz.getConstructor().newInstance();
            context.addLifecycleListener(listener);

            /** 设置context配置文件路径 **/
            context.setConfigFile(contextXml.toURI().toURL());
            /** 设置context全名称 **/
            context.setName(cn.getName());
            /** 设置context根路径 **/
            context.setPath(cn.getPath());
            /** 设置context版本号 **/
            context.setWebappVersion(cn.getVersion());
            /** 获取context文档的绝对路径名或相对路径名，判断是否存在 **/
            if (context.getDocBase() != null) {
                /** 获取context文档文件对象 **/
                File docBase = new File(context.getDocBase());
                if (!docBase.isAbsolute()) {
                    docBase = new File(host.getAppBaseFile(), context.getDocBase());
                }
                /** 判断context文档对象是否保存在appBase目录下 **/
                if (!docBase.getCanonicalPath().startsWith(
                        host.getAppBaseFile().getAbsolutePath() + File.separator)) {
                    isExternal = true;
                    /**
                     * 将context.xml文件添加到deployedApp
                     * deployedApp.redeployResources用来保存和部署context相关联外部资源，被称为外部资源是因为资源并不存放在AppBase目录下
                     * **/
                    deployedApp.redeployResources.put(
                            contextXml.getAbsolutePath(),
                            Long.valueOf(contextXml.lastModified()));
                    /**
                     * 将context文档对象添加到deployedApp
                     * deployedApp.redeployResources用来保存和部署context相关联外部资源，被称为外部资源是因为资源并不存放在AppBase目录下
                     * **/
                    deployedApp.redeployResources.put(docBase.getAbsolutePath(),
                            Long.valueOf(docBase.lastModified()));

                    /** 判断文档文件扩展名称是否为war **/
                    if (docBase.getAbsolutePath().toLowerCase(Locale.ENGLISH).endsWith(".war")) {
                        isExternalWar = true;
                    }
                } else {
                    log.warn(sm.getString("hostConfig.deployDescriptor.localDocBaseSpecified",
                             docBase));
                    // Ignore specified docBase
                    context.setDocBase(null);
                }
            }
            /** 将新实例化context组件添加到host,添加过程中会启动context **/
            host.addChild(context);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("hostConfig.deployDescriptor.error",
                                   contextXml.getAbsolutePath()), t);
        } finally {
            expandedDocBase = new File(host.getAppBaseFile(), cn.getBaseName());
            if (context.getDocBase() != null
                    && !context.getDocBase().toLowerCase(Locale.ENGLISH).endsWith(".war")) {
                expandedDocBase = new File(context.getDocBase());
                if (!expandedDocBase.isAbsolute()) {
                    expandedDocBase = new File(host.getAppBaseFile(), context.getDocBase());
                }
            }

            boolean unpackWAR = unpackWARs;
            if (unpackWAR && context instanceof StandardContext) {
                unpackWAR = ((StandardContext) context).getUnpackWAR();
            }

            if (isExternalWar) {
                if (unpackWAR) {
                    deployedApp.redeployResources.put(expandedDocBase.getAbsolutePath(),
                            Long.valueOf(expandedDocBase.lastModified()));
                    addWatchedResources(deployedApp, expandedDocBase.getAbsolutePath(), context);
                } else {
                    addWatchedResources(deployedApp, null, context);
                }
            } else {
                if (!isExternal) {
                    File warDocBase = new File(expandedDocBase.getAbsolutePath() + ".war");
                    if (warDocBase.exists()) {
                        deployedApp.redeployResources.put(warDocBase.getAbsolutePath(),
                                Long.valueOf(warDocBase.lastModified()));
                    } else {
                        deployedApp.redeployResources.put(
                                warDocBase.getAbsolutePath(),
                                Long.valueOf(0));
                    }
                }
                if (unpackWAR) {
                    deployedApp.redeployResources.put(expandedDocBase.getAbsolutePath(),
                            Long.valueOf(expandedDocBase.lastModified()));
                    addWatchedResources(deployedApp,
                            expandedDocBase.getAbsolutePath(), context);
                } else {
                    addWatchedResources(deployedApp, null, context);
                }
                if (!isExternal) {
                    deployedApp.redeployResources.put(
                            contextXml.getAbsolutePath(),
                            Long.valueOf(contextXml.lastModified()));
                }
            }
            addGlobalRedeployResources(deployedApp);
        }

        if (host.findChild(context.getName()) != null) {
            deployed.put(context.getName(), deployedApp);
        }

        if (log.isInfoEnabled()) {
            log.info(sm.getString("hostConfig.deployDescriptor.finished",
                contextXml.getAbsolutePath(), Long.valueOf(System.currentTimeMillis() - startTime)));
        }
    }

    /**
     * 将appBase目录下war包应用程序部署到host
     */
    protected void deployWAR(ContextName cn, File war) {

        File xml = new File(host.getAppBaseFile(),
                cn.getBaseName() + "/" + Constants.ApplicationContextXml);

        File warTracker = new File(host.getAppBaseFile(), cn.getBaseName() + Constants.WarTracker);

        boolean xmlInWar = false;
        try (JarFile jar = new JarFile(war)) {
            JarEntry entry = jar.getJarEntry(Constants.ApplicationContextXml);
            if (entry != null) {
                xmlInWar = true;
            }
        } catch (IOException e) {
            /* Ignore */
        }

        // If there is an expanded directory then any xml in that directory
        // should only be used if the directory is not out of date and
        // unpackWARs is true. Note the code below may apply further limits
        boolean useXml = false;
        // If the xml file exists then expandedDir must exists so no need to
        // test that here
        if (xml.exists() && unpackWARs &&
                (!warTracker.exists() || warTracker.lastModified() == war.lastModified())) {
            useXml = true;
        }

        Context context = null;
        boolean deployThisXML = isDeployThisXML(war, cn);

        try {
            if (deployThisXML && useXml && !copyXML) {
                synchronized (digesterLock) {
                    try {
                        context = (Context) digester.parse(xml);
                    } catch (Exception e) {
                        log.error(sm.getString(
                                "hostConfig.deployDescriptor.error",
                                war.getAbsolutePath()), e);
                    } finally {
                        digester.reset();
                        if (context == null) {
                            context = new FailedContext();
                        }
                    }
                }
                context.setConfigFile(xml.toURI().toURL());
            } else if (deployThisXML && xmlInWar) {
                synchronized (digesterLock) {
                    try (JarFile jar = new JarFile(war)) {
                        JarEntry entry = jar.getJarEntry(Constants.ApplicationContextXml);
                        try (InputStream istream = jar.getInputStream(entry)) {
                            context = (Context) digester.parse(istream);
                        }
                    } catch (Exception e) {
                        log.error(sm.getString(
                                "hostConfig.deployDescriptor.error",
                                war.getAbsolutePath()), e);
                    } finally {
                        digester.reset();
                        if (context == null) {
                            context = new FailedContext();
                        }
                        context.setConfigFile(
                                UriUtil.buildJarUrl(war, Constants.ApplicationContextXml));
                    }
                }
            } else if (!deployThisXML && xmlInWar) {
                // Block deployment as META-INF/context.xml may contain security
                // configuration necessary for a secure deployment.
                log.error(sm.getString("hostConfig.deployDescriptor.blocked",
                        cn.getPath(), Constants.ApplicationContextXml,
                        new File(host.getConfigBaseFile(), cn.getBaseName() + ".xml")));
            } else {
                context = (Context) Class.forName(contextClass).getConstructor().newInstance();
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("hostConfig.deployWar.error",
                    war.getAbsolutePath()), t);
        } finally {
            if (context == null) {
                context = new FailedContext();
            }
        }

        boolean copyThisXml = false;
        if (deployThisXML) {
            if (host instanceof StandardHost) {
                copyThisXml = ((StandardHost) host).isCopyXML();
            }

            // If Host is using default value Context can override it.
            if (!copyThisXml && context instanceof StandardContext) {
                copyThisXml = ((StandardContext) context).getCopyXML();
            }

            if (xmlInWar && copyThisXml) {
                // Change location of XML file to config base
                xml = new File(host.getConfigBaseFile(),
                        cn.getBaseName() + ".xml");
                try (JarFile jar = new JarFile(war)) {
                    JarEntry entry = jar.getJarEntry(Constants.ApplicationContextXml);
                    try (InputStream istream = jar.getInputStream(entry);
                            FileOutputStream fos = new FileOutputStream(xml);
                            BufferedOutputStream ostream = new BufferedOutputStream(fos, 1024)) {
                        byte buffer[] = new byte[1024];
                        while (true) {
                            int n = istream.read(buffer);
                            if (n < 0) {
                                break;
                            }
                            ostream.write(buffer, 0, n);
                        }
                        ostream.flush();
                    }
                } catch (IOException e) {
                    /* Ignore */
                }
            }
        }

        DeployedApplication deployedApp = new DeployedApplication(cn.getName(),
                xml.exists() && deployThisXML && copyThisXml);

        long startTime = 0;
        // Deploy the application in this WAR file
        if(log.isInfoEnabled()) {
            startTime = System.currentTimeMillis();
            log.info(sm.getString("hostConfig.deployWar",
                    war.getAbsolutePath()));
        }

        try {
            // Populate redeploy resources with the WAR file
            deployedApp.redeployResources.put
                (war.getAbsolutePath(), Long.valueOf(war.lastModified()));

            if (deployThisXML && xml.exists() && copyThisXml) {
                deployedApp.redeployResources.put(xml.getAbsolutePath(),
                        Long.valueOf(xml.lastModified()));
            } else {
                // In case an XML file is added to the config base later
                deployedApp.redeployResources.put(
                        (new File(host.getConfigBaseFile(),
                                cn.getBaseName() + ".xml")).getAbsolutePath(),
                        Long.valueOf(0));
            }

            Class<?> clazz = Class.forName(host.getConfigClass());
            LifecycleListener listener = (LifecycleListener) clazz.getConstructor().newInstance();
            context.addLifecycleListener(listener);

            context.setName(cn.getName());
            context.setPath(cn.getPath());
            context.setWebappVersion(cn.getVersion());
            context.setDocBase(cn.getBaseName() + ".war");
            host.addChild(context);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("hostConfig.deployWar.error",
                    war.getAbsolutePath()), t);
        } finally {
            // If we're unpacking WARs, the docBase will be mutated after
            // starting the context
            boolean unpackWAR = unpackWARs;
            if (unpackWAR && context instanceof StandardContext) {
                unpackWAR = ((StandardContext) context).getUnpackWAR();
            }
            if (unpackWAR && context.getDocBase() != null) {
                File docBase = new File(host.getAppBaseFile(), cn.getBaseName());
                deployedApp.redeployResources.put(docBase.getAbsolutePath(),
                        Long.valueOf(docBase.lastModified()));
                addWatchedResources(deployedApp, docBase.getAbsolutePath(),
                        context);
                if (deployThisXML && !copyThisXml && (xmlInWar || xml.exists())) {
                    deployedApp.redeployResources.put(xml.getAbsolutePath(),
                            Long.valueOf(xml.lastModified()));
                }
            } else {
                // Passing null for docBase means that no resources will be
                // watched. This will be logged at debug level.
                addWatchedResources(deployedApp, null, context);
            }
            // Add the global redeploy resources (which are never deleted) at
            // the end so they don't interfere with the deletion process
            addGlobalRedeployResources(deployedApp);
        }

        deployed.put(cn.getName(), deployedApp);

        if (log.isInfoEnabled()) {
            log.info(sm.getString("hostConfig.deployWar.finished",
                war.getAbsolutePath(), Long.valueOf(System.currentTimeMillis() - startTime)));
        }
    }


    /**
     * 将appBase目录下静态资源文件部署到host
     */
    protected void deployDirectory(ContextName cn, File dir) {


        long startTime = 0;
        // Deploy the application in this directory
        if( log.isInfoEnabled() ) {
            startTime = System.currentTimeMillis();
            log.info(sm.getString("hostConfig.deployDir",
                    dir.getAbsolutePath()));
        }

        Context context = null;
        File xml = new File(dir, Constants.ApplicationContextXml);
        File xmlCopy =
                new File(host.getConfigBaseFile(), cn.getBaseName() + ".xml");


        DeployedApplication deployedApp;
        boolean copyThisXml = isCopyXML();
        boolean deployThisXML = isDeployThisXML(dir, cn);

        try {
            if (deployThisXML && xml.exists()) {
                synchronized (digesterLock) {
                    try {
                        context = (Context) digester.parse(xml);
                    } catch (Exception e) {
                        log.error(sm.getString(
                                "hostConfig.deployDescriptor.error",
                                xml), e);
                        context = new FailedContext();
                    } finally {
                        digester.reset();
                        if (context == null) {
                            context = new FailedContext();
                        }
                    }
                }

                if (copyThisXml == false && context instanceof StandardContext) {
                    // Host is using default value. Context may override it.
                    copyThisXml = ((StandardContext) context).getCopyXML();
                }

                if (copyThisXml) {
                    Files.copy(xml.toPath(), xmlCopy.toPath());
                    context.setConfigFile(xmlCopy.toURI().toURL());
                } else {
                    context.setConfigFile(xml.toURI().toURL());
                }
            } else if (!deployThisXML && xml.exists()) {
                // Block deployment as META-INF/context.xml may contain security
                // configuration necessary for a secure deployment.
                log.error(sm.getString("hostConfig.deployDescriptor.blocked",
                        cn.getPath(), xml, xmlCopy));
                context = new FailedContext();
            } else {
                context = (Context) Class.forName(contextClass).getConstructor().newInstance();
            }

            Class<?> clazz = Class.forName(host.getConfigClass());
            LifecycleListener listener = (LifecycleListener) clazz.getConstructor().newInstance();
            context.addLifecycleListener(listener);

            context.setName(cn.getName());
            context.setPath(cn.getPath());
            context.setWebappVersion(cn.getVersion());
            context.setDocBase(cn.getBaseName());
            host.addChild(context);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("hostConfig.deployDir.error",
                    dir.getAbsolutePath()), t);
        } finally {
            deployedApp = new DeployedApplication(cn.getName(),
                    xml.exists() && deployThisXML && copyThisXml);

            // Fake re-deploy resource to detect if a WAR is added at a later
            // point
            deployedApp.redeployResources.put(dir.getAbsolutePath() + ".war",
                    Long.valueOf(0));
            deployedApp.redeployResources.put(dir.getAbsolutePath(),
                    Long.valueOf(dir.lastModified()));
            if (deployThisXML && xml.exists()) {
                if (copyThisXml) {
                    deployedApp.redeployResources.put(
                            xmlCopy.getAbsolutePath(),
                            Long.valueOf(xmlCopy.lastModified()));
                } else {
                    deployedApp.redeployResources.put(
                            xml.getAbsolutePath(),
                            Long.valueOf(xml.lastModified()));
                    // Fake re-deploy resource to detect if a context.xml file is
                    // added at a later point
                    deployedApp.redeployResources.put(
                            xmlCopy.getAbsolutePath(),
                            Long.valueOf(0));
                }
            } else {
                // Fake re-deploy resource to detect if a context.xml file is
                // added at a later point
                deployedApp.redeployResources.put(
                        xmlCopy.getAbsolutePath(),
                        Long.valueOf(0));
                if (!xml.exists()) {
                    deployedApp.redeployResources.put(
                            xml.getAbsolutePath(),
                            Long.valueOf(0));
                }
            }
            addWatchedResources(deployedApp, dir.getAbsolutePath(), context);
            // Add the global redeploy resources (which are never deleted) at
            // the end so they don't interfere with the deletion process
            addGlobalRedeployResources(deployedApp);
        }

        deployed.put(cn.getName(), deployedApp);

        if( log.isInfoEnabled() ) {
            log.info(sm.getString("hostConfig.deployDir.finished",
                    dir.getAbsolutePath(), Long.valueOf(System.currentTimeMillis() - startTime)));
        }
    }


    /**
     * 检查contextName是否已经部署到host中
     */
    protected boolean deploymentExists(String contextName) {
        return (deployed.containsKey(contextName) ||
                (host.findChild(contextName) != null));
    }


    /**
     * 将Context监视资源添加到对应DeployedApplication对象reloadResources属性中
     */
    protected void addWatchedResources(DeployedApplication app, String docBase,
            Context context) {
        File docBaseFile = null;
        if (docBase != null) {
            docBaseFile = new File(docBase);
            if (!docBaseFile.isAbsolute()) {
                docBaseFile = new File(host.getAppBaseFile(), docBase);
            }
        }
        String[] watchedResources = context.findWatchedResources();
        for (int i = 0; i < watchedResources.length; i++) {
            File resource = new File(watchedResources[i]);
            if (!resource.isAbsolute()) {
                if (docBase != null) {
                    resource = new File(docBaseFile, watchedResources[i]);
                } else {
                    if(log.isDebugEnabled())
                        log.debug("Ignoring non-existent WatchedResource '" +
                                resource.getAbsolutePath() + "'");
                    continue;
                }
            }
            if(log.isDebugEnabled())
                log.debug("Watching WatchedResource '" +
                        resource.getAbsolutePath() + "'");
            app.reloadResources.put(resource.getAbsolutePath(),
                    Long.valueOf(resource.lastModified()));
        }
    }


    /**
     * 将CATALINA_BASE/context.xml.default，CATALINA_BASE/conf/context.xml文件作为外部资源添加到
     * context部署对象DeployedApplication.redeployResources属性中
     */
    protected void addGlobalRedeployResources(DeployedApplication app) {
        // Redeploy resources processing is hard-coded to never delete this file
        File hostContextXml =
                new File(getConfigBaseName(), Constants.HostContextXml);
        if (hostContextXml.isFile()) {
            app.redeployResources.put(hostContextXml.getAbsolutePath(),
                    Long.valueOf(hostContextXml.lastModified()));
        }

        // Redeploy resources in CATALINA_BASE/conf are never deleted
        File globalContextXml =
                returnCanonicalPath(Constants.DefaultContextXml);
        if (globalContextXml.isFile()) {
            app.redeployResources.put(globalContextXml.getAbsolutePath(),
                    Long.valueOf(globalContextXml.lastModified()));
        }
    }


    /**
     * 检查context中资源是否发生变更，如果发生变更则重新部署或重新加载context。
     */
    protected synchronized void checkResources(DeployedApplication app,
            boolean skipFileModificationResolutionCheck) {

        /** 获取context外部资源路径 **/
        String[] resources =
            app.redeployResources.keySet().toArray(new String[0]);

        long currentTimeWithResolutionOffset =
                System.currentTimeMillis() - FILE_MODIFICATION_RESOLUTION_MS;

        /** 遍历context外部资源路径，如果外部资源文件不存在则卸载当前context**/
        for (int i = 0; i < resources.length; i++) {
            File resource = new File(resources[i]);

            if (log.isDebugEnabled())
                log.debug("Checking context[" + app.name +
                        "] redeploy resource " + resource);

            long lastModified =
                    app.redeployResources.get(resources[i]).longValue();

            if (resource.exists() || lastModified == 0) {
                // File.lastModified() has a resolution of 1s (1000ms). The last
                // modified time has to be more than 1000ms ago to ensure that
                // modifications that take place in the same second are not
                // missed. See Bug 57765.
                if (resource.lastModified() != lastModified && (!host.getAutoDeploy() ||
                        resource.lastModified() < currentTimeWithResolutionOffset ||
                        skipFileModificationResolutionCheck)) {
                    if (resource.isDirectory()) {
                        // No action required for modified directory
                        app.redeployResources.put(resources[i],
                                Long.valueOf(resource.lastModified()));
                    } else if (app.hasDescriptor &&
                            resource.getName().toLowerCase(
                                    Locale.ENGLISH).endsWith(".war")) {
                        // Modified WAR triggers a reload if there is an XML
                        // file present
                        // The only resource that should be deleted is the
                        // expanded WAR (if any)
                        Context context = (Context) host.findChild(app.name);
                        String docBase = context.getDocBase();
                        if (!docBase.toLowerCase(Locale.ENGLISH).endsWith(".war")) {
                            // This is an expanded directory
                            File docBaseFile = new File(docBase);
                            if (!docBaseFile.isAbsolute()) {
                                docBaseFile = new File(host.getAppBaseFile(),
                                        docBase);
                            }
                            reload(app, docBaseFile, resource.getAbsolutePath());
                        } else {
                            reload(app, null, null);
                        }
                        // Update times
                        app.redeployResources.put(resources[i],
                                Long.valueOf(resource.lastModified()));
                        app.timestamp = System.currentTimeMillis();
                        boolean unpackWAR = unpackWARs;
                        if (unpackWAR && context instanceof StandardContext) {
                            unpackWAR = ((StandardContext) context).getUnpackWAR();
                        }
                        if (unpackWAR) {
                            addWatchedResources(app, context.getDocBase(), context);
                        } else {
                            addWatchedResources(app, null, context);
                        }
                        return;
                    } else {
                        // Everything else triggers a redeploy
                        // (just need to undeploy here, deploy will follow)
                        undeploy(app);
                        deleteRedeployResources(app, resources, i, false);
                        return;
                    }
                }
            } else {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e1) {
                }
                if (resource.exists()) {
                    continue;
                }
                undeploy(app);
                deleteRedeployResources(app, resources, i, true);
                return;
            }
        }

        /** 获取context需要监视的资源数组 **/
        resources = app.reloadResources.keySet().toArray(new String[0]);
        boolean update = false;

        /** 遍历context需要监视的资源数组 **/
        for (int i = 0; i < resources.length; i++) {
            File resource = new File(resources[i]);
            if (log.isDebugEnabled()) {
                log.debug("Checking context[" + app.name + "] reload resource " + resource);
            }
            long lastModified = app.reloadResources.get(resources[i]).longValue();
            //参见bug 57765
            /** 检查是否有资源是否有更新 **/
            if ((resource.lastModified() != lastModified &&
                    (!host.getAutoDeploy() ||
                            resource.lastModified() < currentTimeWithResolutionOffset ||
                            skipFileModificationResolutionCheck)) ||
                    update) {
                if (!update) {
                    /**重新加载context **/
                    reload(app, null, null);
                    update = true;
                }
                /**  对于重新加载的context，更新对应DeployedApplication对象reloadResources属性，跟新context部署事件 **/
                app.reloadResources.put(resources[i],
                        Long.valueOf(resource.lastModified()));
            }
            app.timestamp = System.currentTimeMillis();
        }
    }


    /**
     * 重新加载指定context
     */
    private void reload(DeployedApplication app, File fileToRemove, String newDocBase) {
        if(log.isInfoEnabled())
            log.info(sm.getString("hostConfig.reload", app.name));
        Context context = (Context) host.findChild(app.name);
        if (context.getState().isAvailable()) {
            if (fileToRemove != null && newDocBase != null) {
                context.addLifecycleListener(
                        new ExpandedDirectoryRemovalListener(fileToRemove, newDocBase));
            }
            // Reload catches and logs exceptions
            context.reload();
        } else {
            // If the context was not started (for example an error
            // in web.xml) we'll still get to try to start
            if (fileToRemove != null && newDocBase != null) {
                ExpandWar.delete(fileToRemove);
                context.setDocBase(newDocBase);
            }
            try {
                context.start();
            } catch (Exception e) {
                log.warn(sm.getString
                         ("hostConfig.context.restart", app.name), e);
            }
        }
    }


    /**
     * 卸载指定context
     */
    private void undeploy(DeployedApplication app) {
        if (log.isInfoEnabled())
            log.info(sm.getString("hostConfig.undeploy", app.name));

        /** 从context子容器中删除 **/
        Container context = host.findChild(app.name);
        try {
            host.removeChild(context);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.warn(sm.getString
                     ("hostConfig.context.remove", app.name), t);
        }
        /** 从已经部署context上下文Map中删除  **/
        deployed.remove(app.name);
    }


    /**
     * 删除context关联的外部资源，和监视资源
     * @param app
     * @param resources
     * @param i
     * @param deleteReloadResources
     */
    private void deleteRedeployResources(DeployedApplication app, String[] resources, int i,
            boolean deleteReloadResources) {


        for (int j = i + 1; j < resources.length; j++) {
            File current = new File(resources[j]);
            if (Constants.HostContextXml.equals(current.getName())) {
                continue;
            }
            if (isDeletableResource(app, current)) {
                if (log.isDebugEnabled()) {
                    log.debug("Delete " + current);
                }
                ExpandWar.delete(current);
            }
        }

        if (deleteReloadResources) {
            String[] resources2 = app.reloadResources.keySet().toArray(new String[0]);
            for (int j = 0; j < resources2.length; j++) {
                File current = new File(resources2[j]);
                if (Constants.HostContextXml.equals(current.getName())) {
                    continue;
                }
                if (isDeletableResource(app, current)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Delete " + current);
                    }
                    ExpandWar.delete(current);
                }
            }
        }
    }


    /**
     * 判断当前context内指定的资源能够删除
     *  -位于AppBase中的任何资源
     *  -位于配置数据库下的任何部署描述符
     *  -以上任一项的AppBase或ConfigBase中的符号链接
     */
    private boolean isDeletableResource(DeployedApplication app, File resource) {
        // The resource may be a file, a directory or a symlink to a file or
        // directory.

        // Check that the resource is absolute. This should always be the case.
        if (!resource.isAbsolute()) {
            log.warn(sm.getString("hostConfig.resourceNotAbsolute", app.name, resource));
            return false;
        }

        // Determine where the resource is located
        String canonicalLocation;
        try {
            canonicalLocation = resource.getParentFile().getCanonicalPath();
        } catch (IOException e) {
            log.warn(sm.getString(
                    "hostConfig.canonicalizing", resource.getParentFile(), app.name), e);
            return false;
        }

        String canonicalAppBase;
        try {
            canonicalAppBase = host.getAppBaseFile().getCanonicalPath();
        } catch (IOException e) {
            log.warn(sm.getString(
                    "hostConfig.canonicalizing", host.getAppBaseFile(), app.name), e);
            return false;
        }

        if (canonicalLocation.equals(canonicalAppBase)) {
            // Resource is located in the appBase so it may be deleted
            return true;
        }

        String canonicalConfigBase;
        try {
            canonicalConfigBase = host.getConfigBaseFile().getCanonicalPath();
        } catch (IOException e) {
            log.warn(sm.getString(
                    "hostConfig.canonicalizing", host.getConfigBaseFile(), app.name), e);
            return false;
        }

        if (canonicalLocation.equals(canonicalConfigBase) &&
                resource.getName().endsWith(".xml")) {
            // Resource is an xml file in the configBase so it may be deleted
            return true;
        }

        // All other resources should not be deleted
        return false;
    }


    /**
     * 创建host组件定义appbase和xmlbase路径对应的目录文件
     */
    public void beforeStart() {
        if (host.getCreateDirs()) {
            File[] dirs = new File[] {host.getAppBaseFile(),host.getConfigBaseFile()};
            for (int i=0; i<dirs.length; i++) {
                if (!dirs[i].mkdirs() && !dirs[i].isDirectory()) {
                    log.error(sm.getString("hostConfig.createDirs",dirs[i]));
                }
            }
        }
    }


    /**
     * 启动hostConfig组件.
     *
     * 如果Host组件启用了在启动时自动部署appBase，xmlBase目录下应用程序或静态资源
     * 则扫描appBase，xmlBase目录下应用程序或静态资源，构建context组件，添加到host组件，并启动
     */
    public void start() {

        if (log.isDebugEnabled())
            log.debug(sm.getString("hostConfig.start"));

        try {
            /** 将HostConfig对象注册到JMX Bean中 **/
            ObjectName hostON = host.getObjectName();
            oname = new ObjectName
                (hostON.getDomain() + ":type=Deployer,host=" + host.getName());
            Registry.getRegistry(null, null).registerComponent
                (this, oname, this.getClass().getName());
        } catch (Exception e) {
            log.error(sm.getString("hostConfig.jmx.register", oname), e);
        }

        /**
         * 如果Host组件appBase路径下的文件不是目录s
         * 则关闭启动Host组件时自动部署Web应用程序，同时关闭热部署
         * **/
        if (!host.getAppBaseFile().isDirectory()) {
            log.error(sm.getString("hostConfig.appBase", host.getName(),
                    host.getAppBaseFile().getPath()));
            host.setDeployOnStartup(false);
            host.setAutoDeploy(false);
        }

        /**
         * 如果Host组件启用了在启动时自动部署appBase，xmlBase目录下应用程序或静态资源
         * 则调用deployApps()扫描appBase，xmlBase目录下应用程序或静态资源，构建context组件，添加到host组件，并启动
         **/
        if (host.getDeployOnStartup())
            deployApps();

    }


    /**
     * 将HostConfig对象从Jmx bean中注销
     */
    public void stop() {

        if (log.isDebugEnabled())
            log.debug(sm.getString("hostConfig.stop"));

        if (oname != null) {
            try {
                Registry.getRegistry(null, null).unregisterComponent(oname);
            } catch (Exception e) {
                log.error(sm.getString("hostConfig.jmx.unregister", oname), e);
            }
        }
        oname = null;
    }


    /**
     * 获取host组件下所有部署context组件，检查每一个context组件中需要监听资源目录是否发生变更，
     * 如果发生变更则触发其context重新加载或启动
     */
    protected void check() {

        if (host.getAutoDeploy()) {
            /**
             * 获取部署到host中所有context对应DeployedApplication对象集合
             * DeployedApplication对象用来描述context部署信息
             **/
            DeployedApplication[] apps =
                deployed.values().toArray(new DeployedApplication[0]);
            for (int i = 0; i < apps.length; i++) {
                /** 过滤掉禁止部署context **/
                if (!isServiced(apps[i].name))
                    /** 检查context中资源是否发生变更，如果发生变更则重新部署或重新加载context。 **/
                    checkResources(apps[i], false);
            }

            /** 如果context存在多个版本是否要卸载旧版本context **/
            if (host.getUndeployOldVersions()) {
                checkUndeploy();
            }

            /** 扫描appBase，xmlBase目录下应用程序或静态资源，构建context组件，添加到host组件，并启动 **/
            deployApps();
        }
    }


    /**
     * 检查指定context组件中需要监听资源目录是否发生变更，
     * 如果发生变更则触发其context重新加载或启动
     */
    public void check(String name) {
        DeployedApplication app = deployed.get(name);
        if (app != null) {
            /** 检查context中资源是否发生变更，如果发生变更则重新部署或重新加载context。 **/
            checkResources(app, true);
        }
        /** 扫描appBase，xmlBase目录下应用程序或静态资源，构建context组件，添加到host组件，并启动 **/
        deployApps(name);
    }

    /**
     * 检查使用并行部署的旧版本的应用程序，将其卸载
     */
    public synchronized void checkUndeploy() {
        if (deployed.size() < 2) {
            return;
        }

        // Need ordered set of names
        SortedSet<String> sortedAppNames = new TreeSet<>();
        sortedAppNames.addAll(deployed.keySet());

        Iterator<String> iter = sortedAppNames.iterator();

        ContextName previous = new ContextName(iter.next(), false);
        do {
            ContextName current = new ContextName(iter.next(), false);

            if (current.getPath().equals(previous.getPath())) {
                // Current and previous are same path - current will always
                // be a later version
                Context previousContext = (Context) host.findChild(previous.getName());
                Context currentContext = (Context) host.findChild(current.getName());
                if (previousContext != null && currentContext != null &&
                        currentContext.getState().isAvailable() &&
                        !isServiced(previous.getName())) {
                    Manager manager = previousContext.getManager();
                    if (manager != null) {
                        int sessionCount;
                        if (manager instanceof DistributedManager) {
                            sessionCount = ((DistributedManager) manager).getActiveSessionsFull();
                        } else {
                            sessionCount = manager.getActiveSessions();
                        }
                        if (sessionCount == 0) {
                            if (log.isInfoEnabled()) {
                                log.info(sm.getString(
                                        "hostConfig.undeployVersion", previous.getName()));
                            }
                            DeployedApplication app = deployed.get(previous.getName());
                            String[] resources = app.redeployResources.keySet().toArray(new String[0]);
                            // Version is unused - undeploy it completely
                            // The -1 is a 'trick' to ensure all redeploy
                            // resources are removed
                            undeploy(app);
                            deleteRedeployResources(app, resources, -1, true);
                        }
                    }
                }
            }
            previous = current;
        } while (iter.hasNext());
    }

    /**
     * 添加context
     */
    public void manageApp(Context context)  {

        String contextName = context.getName();


        if (deployed.containsKey(contextName))
            return;

        DeployedApplication deployedApp =
                new DeployedApplication(contextName, false);

        // Add the associated docBase to the redeployed list if it's a WAR
        boolean isWar = false;
        if (context.getDocBase() != null) {
            File docBase = new File(context.getDocBase());
            if (!docBase.isAbsolute()) {
                docBase = new File(host.getAppBaseFile(), context.getDocBase());
            }
            deployedApp.redeployResources.put(docBase.getAbsolutePath(),
                    Long.valueOf(docBase.lastModified()));
            if (docBase.getAbsolutePath().toLowerCase(Locale.ENGLISH).endsWith(".war")) {
                isWar = true;
            }
        }
        host.addChild(context);
        // Add the eventual unpacked WAR and all the resources which will be
        // watched inside it
        boolean unpackWAR = unpackWARs;
        if (unpackWAR && context instanceof StandardContext) {
            unpackWAR = ((StandardContext) context).getUnpackWAR();
        }
        if (isWar && unpackWAR) {
            File docBase = new File(host.getAppBaseFile(), context.getBaseName());
            deployedApp.redeployResources.put(docBase.getAbsolutePath(),
                        Long.valueOf(docBase.lastModified()));
            addWatchedResources(deployedApp, docBase.getAbsolutePath(), context);
        } else {
            addWatchedResources(deployedApp, null, context);
        }
        deployed.put(contextName, deployedApp);
    }

    /**
     * 删除context
     */
    public void unmanageApp(String contextName) {
        if(isServiced(contextName)) {
            deployed.remove(contextName);
            host.removeChild(host.findChild(contextName));
        }
    }

    /**
     * 校验contextPath是否符合规则
     */
    private boolean validateContextPath(File appBase, String contextPath) {

        StringBuilder docBase;
        String canonicalDocBase = null;

        try {
            String canonicalAppBase = appBase.getCanonicalPath();
            docBase = new StringBuilder(canonicalAppBase);
            if (canonicalAppBase.endsWith(File.separator)) {
                docBase.append(contextPath.substring(1).replace(
                        '/', File.separatorChar));
            } else {
                docBase.append(contextPath.replace('/', File.separatorChar));
            }

            canonicalDocBase =
                    (new File(docBase.toString())).getCanonicalPath();

            if (canonicalDocBase.endsWith(File.separator)) {
                docBase.append(File.separator);
            }
        } catch (IOException ioe) {
            return false;
        }

        return canonicalDocBase.equals(docBase.toString());
    }


    /**
     * 使用host容器组件deployIgnore正则表达式过滤tomcat存放web应用程序的目录子文件
     */
    protected String[] filterAppPaths(String[] unfilteredAppPaths) {
        Pattern filter = host.getDeployIgnorePattern();
        if (filter == null || unfilteredAppPaths == null) {
            return unfilteredAppPaths;
        }

        List<String> filteredList = new ArrayList<>();
        Matcher matcher = null;
        for (String appPath : unfilteredAppPaths) {
            if (matcher == null) {
                matcher = filter.matcher(appPath);
            } else {
                matcher.reset(appPath);
            }
            if (matcher.matches()) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("hostConfig.ignorePath", appPath));
                }
            } else {
                filteredList.add(appPath);
            }
        }
        return filteredList.toArray(new String[filteredList.size()]);
    }

    // ----------------------------------------------------- Instance Variables


    /**
     * 用来描述context部署信息
     */
    protected static class DeployedApplication {
        public DeployedApplication(String name, boolean hasDescriptor) {
            this.name = name;
            this.hasDescriptor = hasDescriptor;
        }

        /**
         * context根路径
         */
        public final String name;

        /**
         * context作为应用程序是否配置/META-INF/context.xml
         */
        public final boolean hasDescriptor;

        /**
         * 存储已部署context外部资源，外部资源是处于放置AppBase目录之外的资源
         * 其中key为外部资源的绝对路径,value为资源部署时的最后修改时间
         */
        public final LinkedHashMap<String, Long> redeployResources =
                new LinkedHashMap<>();

        /**
         * 存储已部署context需要监听的资源，一旦资源发生变更会tomcat热部署机制会重新加载context
         */
        public final HashMap<String, Long> reloadResources = new HashMap<>();

        /**
         * context最后一次部署时间
         */
        public long timestamp = System.currentTimeMillis();

        /**
         * context作为应用程序是否不解压war包，直接运行wai包程序
         */
        public boolean loggedDirWarning = false;
    }


    /**
     * 部署xml文件任务线程
     */
    private static class DeployDescriptor implements Runnable {

        private HostConfig config;
        private ContextName cn;
        private File descriptor;

        public DeployDescriptor(HostConfig config, ContextName cn,
                File descriptor) {
            this.config = config;
            this.cn = cn;
            this.descriptor= descriptor;
        }

        @Override
        public void run() {
            config.deployDescriptor(cn, descriptor);
        }
    }

    /**
     * 部署war文件任务线程
     */
    private static class DeployWar implements Runnable {

        private HostConfig config;
        private ContextName cn;
        private File war;

        public DeployWar(HostConfig config, ContextName cn, File war) {
            this.config = config;
            this.cn = cn;
            this.war = war;
        }

        @Override
        public void run() {
            config.deployWAR(cn, war);
        }
    }

    /**
     * 部署目录文件任务线程
     */
    private static class DeployDirectory implements Runnable {

        private HostConfig config;
        private ContextName cn;
        private File dir;

        public DeployDirectory(HostConfig config, ContextName cn, File dir) {
            this.config = config;
            this.cn = cn;
            this.dir = dir;
        }

        @Override
        public void run() {
            config.deployDirectory(cn, dir);
        }
    }


    /*
     * The purpose of this class is to provide a way for HostConfig to get
     * a Context to delete an expanded WAR after the Context stops. This is to
     * resolve this issue described in Bug 57772. The alternative solutions
     * require either duplicating a lot of the Context.reload() code in
     * HostConfig or adding a new reload(boolean) method to Context that allows
     * the caller to optionally delete any expanded WAR.
     *
     * The LifecycleListener approach offers greater flexibility and enables the
     * behaviour to be changed / extended / removed in future without changing
     * the Context API.
     */
    private static class ExpandedDirectoryRemovalListener implements LifecycleListener {

        private final File toDelete;
        private final String newDocBase;

        /**
         * Create a listener that will ensure that any expanded WAR is removed
         * and the docBase set to the specified WAR.
         *
         * @param toDelete The file (a directory representing an expanded WAR)
         *                 to be deleted
         * @param newDocBase The new docBase for the Context
         */
        public ExpandedDirectoryRemovalListener(File toDelete, String newDocBase) {
            this.toDelete = toDelete;
            this.newDocBase = newDocBase;
        }

        @Override
        public void lifecycleEvent(LifecycleEvent event) {
            if (Lifecycle.AFTER_STOP_EVENT.equals(event.getType())) {
                // The context has stopped.
                Context context = (Context) event.getLifecycle();

                // Remove the old expanded WAR.
                ExpandWar.delete(toDelete);

                // Reset the docBase to trigger re-expansion of the WAR.
                context.setDocBase(newDocBase);

                // Remove this listener from the Context else it will run every
                // time the Context is stopped.
                context.removeLifecycleListener(this);
            }
        }
    }
}
