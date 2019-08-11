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


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.net.ConnectException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.LogManager;

import org.apache.catalina.Container;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Server;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.security.SecurityConfig;
import org.apache.juli.ClassLoaderLogManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.Rule;
import org.apache.tomcat.util.digester.RuleSet;
import org.apache.tomcat.util.log.SystemLogHandler;
import org.apache.tomcat.util.res.StringManager;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;


/**
 * Startup/Shutdown shell program for Catalina.  The following command line
 * options are recognized:
 * <ul>
 * <li><b>-config {pathname}</b> - Set the pathname of the configuration file
 *     to be processed.  If a relative path is specified, it will be
 *     interpreted as relative to the directory pathname specified by the
 *     "catalina.base" system property.   [conf/server.xml]</li>
 * <li><b>-help</b>      - Display usage information.</li>
 * <li><b>-nonaming</b>  - Disable naming support.</li>
 * <li><b>configtest</b> - Try to test the config</li>
 * <li><b>start</b>      - Start an instance of Catalina.</li>
 * <li><b>stop</b>       - Stop the currently running instance of Catalina.</li>
 * </ul>
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
public class Catalina {


    /** 管理打印日志模板组件 **/
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);

    // ----------------------------------------------------- Instance Variables
    /**
     * 标识是否需要启动一个Socket等待接受shutdown命令，用来停止Tomcat
     */
    protected boolean await = false;

    /**
     * tomcat配置文件，用来实例化Tomcat Server组件
     */
    protected String configFile = "conf/server.xml";

    /**
     * Shared类加载器
     */
    protected ClassLoader parentClassLoader =
        Catalina.class.getClassLoader();

    /**
     * Tomcat Server组件
     */
    protected Server server = null;

    /**
     * 是否需要向JVM注册关闭钩子线程，当JVM发生以下情况关闭前，会触发注册钩子线程动作
     * 1. 程序正常退出
     * 2. 使用System.exit()
     * 3. 终端使用Ctrl+C触发的中断
     * 4. 系统关闭
     * 5. 使用Kill pid命令干掉进程
     */
    protected boolean useShutdownHook = true;


    /**
     * 处理关闭tomcat的线程
     */
    protected Thread shutdownHook = null;


    /**
     * 是否开启JNDI服务
     */
    protected boolean useNaming = true;


    /**
     * 加载标识，防止重复加载。
     */
    protected boolean loaded = false;


    // ----------------------------------------------------------- Constructors

    /**
     * 实例化Catalina
     */
    public Catalina() {
        /** 从catalina.properties读取受保护类，注册到Security中  **/
        setSecurityProtection();
        ExceptionUtils.preload();
    }


    // ------------------------------------------------------------- Properties

    public void setConfigFile(String file) {
        configFile = file;
    }

    public String getConfigFile() {
        return configFile;
    }

    public void setUseShutdownHook(boolean useShutdownHook) {
        this.useShutdownHook = useShutdownHook;
    }

    public boolean getUseShutdownHook() {
        return useShutdownHook;
    }


    public void setParentClassLoader(ClassLoader parentClassLoader) {
        this.parentClassLoader = parentClassLoader;
    }

    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null) {
            return (parentClassLoader);
        }
        return ClassLoader.getSystemClassLoader();
    }


    public void setServer(Server server) {
        this.server = server;
    }

    public Server getServer() {
        return server;
    }

    public boolean isUseNaming() {
        return (this.useNaming);
    }

    public void setUseNaming(boolean useNaming) {
        this.useNaming = useNaming;
    }


    public void setAwait(boolean b) {
        await = b;
    }

    public boolean isAwait() {
        return await;
    }

    // ------------------------------------------------------ Protected Methods


    /**
     * 处理指定的命令行参数。
     * 没有看到哪里有调用，可能用于JMX?
     * "-nonaming" 标识开启JDNI
     */
    protected boolean arguments(String args[]) {

        boolean isConfig = false;

        if (args.length < 1) {
            usage();
            return false;
        }

        for (int i = 0; i < args.length; i++) {
            if (isConfig) {
                configFile = args[i];
                isConfig = false;
            } else if (args[i].equals("-config")) {
                isConfig = true;
            } else if (args[i].equals("-nonaming")) {
                setUseNaming(false);
            } else if (args[i].equals("-help")) {
                usage();
                return false;
            } else if (args[i].equals("start")) {
                // NOOP
            } else if (args[i].equals("configtest")) {
                // NOOP
            } else if (args[i].equals("stop")) {
                // NOOP
            } else {
                usage();
                return false;
            }
        }

        return true;
    }


    /**
     * 返回配置文件的绝对路径File对象
     * @return the main configuration file
     */
    protected File configFile() {

        File file = new File(configFile);
        /** 判断路径名是否是绝对的 **/
        if (!file.isAbsolute()) {
            file = new File(Bootstrap.getCatalinaBase(), configFile);
        }
        return (file);

    }


    /**
     * 创建并配置我们将用于启动的Digester。
     * 主要用于解析server.xml
     */
    protected Digester createStartDigester() {
        long t1=System.currentTimeMillis();
        // Initialize the digester
        Digester digester = new Digester();
        /** 设置为false表示解析xml时不需要进行DTD的规则校验   **/
        digester.setValidating(false);
        /** 是否进行节点设置规则校验,如果xml中相应节点没有设置解析规则会在控制台显示提示信息   **/
        digester.setRulesValidation(true);

        /** 设置无效的属性，也就是在检查到这些属性时SetProperties规则不会将其设置到规则指定对象属性中 **/
        Map<Class<?>, List<String>> fakeAttributes = new HashMap<>();
        List<String> objectAttrs = new ArrayList<>();
        objectAttrs.add("className");
        fakeAttributes.put(Object.class, objectAttrs);
        // Ignore attribute added by Eclipse for its internal tracking
        List<String> contextAttrs = new ArrayList<>();
        contextAttrs.add("source");
        fakeAttributes.put(StandardContext.class, contextAttrs);
        digester.setFakeAttributes(fakeAttributes);
        digester.setUseContextClassLoader(true);

        //解析<Server>标签
        /** 解析<server>标签实例化StandardServer对象，并push到操作栈中 **/
        digester.addObjectCreate("Server",
                "org.apache.catalina.core.StandardServer",
                "className");

        /** 解析<server>标签将标签中属性值映射到StandardServer对象中**/
        digester.addSetProperties("Server");

        /** 解析</server>标签将操作栈栈顶对象作为次栈顶对象Catalina.setServer方法调用的参数，设置到Catalina属性中**/
        digester.addSetNext("Server",
                "setServer",
                "org.apache.catalina.Server");


        //解析<Server>GlobalNamingResources>标签
        /** 解析<GlobalNamingResources>标签实例化NamingResourcesImpl对象，并push到操作栈中 **/
        digester.addObjectCreate("Server/GlobalNamingResources",
                "org.apache.catalina.deploy.NamingResourcesImpl");

        /** 解析<GlobalNamingResources>标签将标签中属性值映射到NamingResourcesImpl对象中**/
        digester.addSetProperties("Server/GlobalNamingResources");

        /** 解析</GlobalNamingResources>标签将操作栈栈顶对象作为次栈顶对象StandardServer.setGlobalNamingResources方法调用的参数，设置到StandardServer属性中**/
        digester.addSetNext("Server/GlobalNamingResources",
                "setGlobalNamingResources",
                "org.apache.catalina.deploy.NamingResourcesImpl");

        //解析<Server><Listener>标签
        /** 解析<Listener>标签实例化标签中className属性定义的对象，并push到操作栈中 **/
        digester.addObjectCreate("Server/Listener",
                                 null, // MUST be specified in the element
                                 "className");
        /** 解析<Listener>标签将标签中属性值映射到其实例化对象中**/
        digester.addSetProperties("Server/Listener");

        /** 解析</Listener>标签将操作栈栈顶对象作为次栈顶对象StandardServer.addLifecycleListener方法调用的参数，设置到StandardServer属性中**/
        digester.addSetNext("Server/Listener",
                            "addLifecycleListener",
                            "org.apache.catalina.LifecycleListener");

        //解析<Server><Service>标签
        /** 解析<Service>标签实例化StandardService对象，并push到操作栈中 **/
        digester.addObjectCreate("Server/Service",
                                 "org.apache.catalina.core.StandardService",
                                 "className");
        /** 解析<Service>标签将标签中属性值映射到StandardService对象中**/
        digester.addSetProperties("Server/Service");
        /** 解析</Service>标签将操作栈栈顶对象作为次栈顶对象StandardServer.addService方法调用的参数，设置到StandardServer属性中**/
        digester.addSetNext("Server/Service",
                            "addService",
                            "org.apache.catalina.Service");

        //解析<Server><Service><Listener>标签
        /** 解析<Listener>标签实例化标签中className属性定义的对象，并push到操作栈中 **/
        digester.addObjectCreate("Server/Service/Listener",
                                 null, // MUST be specified in the element
                                 "className");
        /** 解析<Listener>标签将标签中属性值映射到其实例化对象中**/
        digester.addSetProperties("Server/Service/Listener");


        /** 解析</Listener>标签将操作栈栈顶对象作为次栈顶对象StandardService.addLifecycleListener方法调用的参数，设置到StandardServer属性中**/
        digester.addSetNext("Server/Service/Listener",
                            "addLifecycleListener",
                            "org.apache.catalina.LifecycleListener");

        //解析<Server><Service><Executor>标签
        /** 解析<Executor>标签实例化StandardThreadExecutor对象，并push到操作栈中 **/
        digester.addObjectCreate("Server/Service/Executor",
                         "org.apache.catalina.core.StandardThreadExecutor",
                         "className");
        /** 解析<Executor>标签将标签中属性值映射到其实例化对象中**/
        digester.addSetProperties("Server/Service/Executor");

        /** 解析</Executor>标签将操作栈栈顶对象作为次栈顶对象StandardService.addExecutor方法调用的参数，设置到StandardServer属性中**/
        digester.addSetNext("Server/Service/Executor",
                            "addExecutor",
                            "org.apache.catalina.Executor");

        //解析<Server><Service><Connector>标签
        /** 解析<Connector>标签使用自定义规则ConnectorCreateRule**/
        digester.addRule("Server/Service/Connector",
                         new ConnectorCreateRule());
        /** 解析<Connector>标签属性使用自定义规则SetAllPropertiesRule**/
        digester.addRule("Server/Service/Connector",
                         new SetAllPropertiesRule(new String[]{"executor", "sslImplementationName"}));

        /** 解析</Connector>标签将操作栈栈顶对象作为次栈顶对象StandardService.addConnector方法调用的参数，设置到StandardServer属性中**/
        digester.addSetNext("Server/Service/Connector",
                            "addConnector",
                            "org.apache.catalina.connector.Connector");





        digester.addObjectCreate("Server/Service/Connector/SSLHostConfig",
                                 "org.apache.tomcat.util.net.SSLHostConfig");
        digester.addSetProperties("Server/Service/Connector/SSLHostConfig");
        digester.addSetNext("Server/Service/Connector/SSLHostConfig",
                "addSslHostConfig",
                "org.apache.tomcat.util.net.SSLHostConfig");

        digester.addRule("Server/Service/Connector/SSLHostConfig/Certificate",
                         new CertificateCreateRule());
        digester.addRule("Server/Service/Connector/SSLHostConfig/Certificate",
                         new SetAllPropertiesRule(new String[]{"type"}));
        digester.addSetNext("Server/Service/Connector/SSLHostConfig/Certificate",
                            "addCertificate",
                            "org.apache.tomcat.util.net.SSLHostConfigCertificate");

        digester.addObjectCreate("Server/Service/Connector/SSLHostConfig/OpenSSLConf",
                                 "org.apache.tomcat.util.net.openssl.OpenSSLConf");
        digester.addSetProperties("Server/Service/Connector/SSLHostConfig/OpenSSLConf");
        digester.addSetNext("Server/Service/Connector/SSLHostConfig/OpenSSLConf",
                            "setOpenSslConf",
                            "org.apache.tomcat.util.net.openssl.OpenSSLConf");

        digester.addObjectCreate("Server/Service/Connector/SSLHostConfig/OpenSSLConf/OpenSSLConfCmd",
                                 "org.apache.tomcat.util.net.openssl.OpenSSLConfCmd");
        digester.addSetProperties("Server/Service/Connector/SSLHostConfig/OpenSSLConf/OpenSSLConfCmd");
        digester.addSetNext("Server/Service/Connector/SSLHostConfig/OpenSSLConf/OpenSSLConfCmd",
                            "addCmd",
                            "org.apache.tomcat.util.net.openssl.OpenSSLConfCmd");

        digester.addObjectCreate("Server/Service/Connector/Listener",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties("Server/Service/Connector/Listener");
        digester.addSetNext("Server/Service/Connector/Listener",
                            "addLifecycleListener",
                            "org.apache.catalina.LifecycleListener");

        digester.addObjectCreate("Server/Service/Connector/UpgradeProtocol",
                                  null, // MUST be specified in the element
                                  "className");
        digester.addSetProperties("Server/Service/Connector/UpgradeProtocol");
        digester.addSetNext("Server/Service/Connector/UpgradeProtocol",
                            "addUpgradeProtocol",
                            "org.apache.coyote.UpgradeProtocol");

        /** 为指定xml标签添加一组规则 **/
        digester.addRuleSet(new NamingRuleSet("Server/GlobalNamingResources/"));
        digester.addRuleSet(new EngineRuleSet("Server/Service/"));

        //解析<Server><Service><Connector>标签
        digester.addRuleSet(new HostRuleSet("Server/Service/Engine/"));
        digester.addRuleSet(new ContextRuleSet("Server/Service/Engine/Host/"));
        addClusterRuleSet(digester, "Server/Service/Engine/Host/Cluster/");
        digester.addRuleSet(new NamingRuleSet("Server/Service/Engine/Host/Context/"));

        // When the 'engine' is found, set the parentClassLoader.
        digester.addRule("Server/Service/Engine",
                         new SetParentClassLoaderRule(parentClassLoader));
        addClusterRuleSet(digester, "Server/Service/Engine/Cluster/");

        long t2=System.currentTimeMillis();
        if (log.isDebugEnabled()) {
            log.debug("Digester for server.xml created " + ( t2-t1 ));
        }
        return (digester);

    }

    /**
     * 使用digester为指定xml规则添加  ClusterRuleSet规则组
     */
    private void addClusterRuleSet(Digester digester, String prefix) {
        Class<?> clazz = null;
        Constructor<?> constructor = null;
        try {
            clazz = Class.forName("org.apache.catalina.ha.ClusterRuleSet");
            constructor = clazz.getConstructor(String.class);
            RuleSet ruleSet = (RuleSet) constructor.newInstance(prefix);
            digester.addRuleSet(ruleSet);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("catalina.noCluster",
                        e.getClass().getName() + ": " +  e.getMessage()), e);
            } else if (log.isInfoEnabled()) {
                log.info(sm.getString("catalina.noCluster",
                        e.getClass().getName() + ": " +  e.getMessage()));
            }
        }
    }

    /**
     * 创建并配置我们将用于关闭的Digester。
     */
    protected Digester createStopDigester() {

        Digester digester = new Digester();
        /** 使用上下文类加载器 **/
        digester.setUseContextClassLoader(true);

        digester.addObjectCreate("Server",
                                 "org.apache.catalina.core.StandardServer",
                                 "className");
        digester.addSetProperties("Server");
        digester.addSetNext("Server",
                            "setServer",
                            "org.apache.catalina.Server");

        return (digester);
    }


    /**
     * 停止Tomcat容器
     */
    public void stopServer() {
        stopServer(null);
    }

    /**
     * 停止Tomcat容器
     */
    public void stopServer(String[] arguments) {

        /** 处理指定的命令行参数 **/
        if (arguments != null) {
            arguments(arguments);
        }

        /** 获取tomcat Server组件实例 **/
        Server s = getServer();
        /** 如果omcat Server组件实例不存在，使用Digester构造一个tomcat Server组件 **/
        if (s == null) {
            Digester digester = createStopDigester();
            File file = configFile();
            try (FileInputStream fis = new FileInputStream(file)) {
                InputSource is =
                    new InputSource(file.toURI().toURL().toString());
                is.setByteStream(fis);
                digester.push(this);
                digester.parse(is);
            } catch (Exception e) {
                log.error("Catalina.stop: ", e);
                System.exit(1);
            }
        } else {
            /** 对存在Tomcat Server组件执行停止，销毁动作 **/
            try {
                s.stop();
                s.destroy();
            } catch (LifecycleException e) {
                log.error("Catalina.stop: ", e);
            }
            return;
        }

        /**
         * 向Tomcat Server指定的端口发送shutdown指令
         */
        s = getServer();
        if (s.getPort()>0) {
            try (Socket socket = new Socket(s.getAddress(), s.getPort());
                    OutputStream stream = socket.getOutputStream()) {
                String shutdown = s.getShutdown();
                for (int i = 0; i < shutdown.length(); i++) {
                    stream.write(shutdown.charAt(i));
                }
                stream.flush();
            } catch (ConnectException ce) {
                log.error(sm.getString("catalina.stopServer.connectException",
                                       s.getAddress(),
                                       String.valueOf(s.getPort())));
                log.error("Catalina.stop: ", ce);
                System.exit(1);
            } catch (IOException e) {
                log.error("Catalina.stop: ", e);
                System.exit(1);
            }
        } else {
            log.error(sm.getString("catalina.stopServer"));
            System.exit(1);
        }
    }


    /**
     * 加载tomcat容器
     */
    public void load() {

        /** 判断是否已经加载过，防止重复加载 **/
        if (loaded) {
            return;
        }
        loaded = true;

        long t1 = System.nanoTime();

        /** 检查java.io.tmdir系统属性值对应目录是否存在  **/
        initDirs();

        /** 初始化JNDI系统属性 **/
        initNaming();

        /** 创建Digester实例，定义解析/conf/Server.xml文件规则**/
        Digester digester = createStartDigester();

            InputSource inputSource = null;
            InputStream inputStream = null;
            File file = null;
            try {
            /** 读取 catalina_home\conf\server.xml 配置文件 **/
            try {

                file = configFile();
                inputStream = new FileInputStream(file);
                inputSource = new InputSource(file.toURI().toURL().toString());
            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("catalina.configFail", file), e);
                }
            }
            /** 读取 classpath:\conf\server.xml **/
            if (inputStream == null) {
                try {
                    inputStream = getClass().getClassLoader()
                        .getResourceAsStream(getConfigFile());
                    inputSource = new InputSource
                        (getClass().getClassLoader()
                         .getResource(getConfigFile()).toString());
                } catch (Exception e) {
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("catalina.configFail",
                                getConfigFile()), e);
                    }
                }
            }

            /** 读取 classpath:\conf\server-embed.xml **/
            if (inputStream == null) {
                try {
                    inputStream = getClass().getClassLoader()
                            .getResourceAsStream("server-embed.xml");
                    inputSource = new InputSource
                    (getClass().getClassLoader()
                            .getResource("server-embed.xml").toString());
                } catch (Exception e) {
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("catalina.configFail",
                                "server-embed.xml"), e);
                    }
                }
            }

            /** 没有找到配置文件返回 **/
            if (inputStream == null || inputSource == null) {
                if  (file == null) {
                    log.warn(sm.getString("catalina.configFail",
                            getConfigFile() + "] or [server-embed.xml]"));
                } else {
                    log.warn(sm.getString("catalina.configFail",
                            file.getAbsolutePath()));
                    if (file.exists() && !file.canRead()) {
                        log.warn("Permissions incorrect, read permission is not allowed on the file.");
                    }
                }
                return;
            }

            /** 将xml解析对象放置到当前对象属性中 **/
            try {
                inputSource.setByteStream(inputStream);
                digester.push(this);
                digester.parse(inputSource);
            } catch (SAXParseException spe) {
                log.warn("Catalina.start using " + getConfigFile() + ": " +
                        spe.getMessage());
                return;
            } catch (Exception e) {
                log.warn("Catalina.start using " + getConfigFile() + ": " , e);
                return;
            }
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }

        /** 给Server设置catalina信息  **/
        getServer().setCatalina(this);
        getServer().setCatalinaHome(Bootstrap.getCatalinaHomeFile());
        getServer().setCatalinaBase(Bootstrap.getCatalinaBaseFile());

        // Stream redirection
        initStreams();

        /** 初始化 进入Lifecycle**/
        try {
            getServer().init();
        } catch (LifecycleException e) {
            if (Boolean.getBoolean("org.apache.catalina.startup.EXIT_ON_INIT_FAILURE")) {
                throw new java.lang.Error(e);
            } else {
                log.error("Catalina.start", e);
            }
        }

        long t2 = System.nanoTime();
        if(log.isInfoEnabled()) {
            log.info("Initialization processed in " + ((t2 - t1) / 1000000) + " ms");
        }
    }


    /**
     * 加载Catalina
     */
    public void load(String args[]) {

        try {
            if (arguments(args)) {
                load();
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }


    /**
     * 启动Catalina
     */
    public void start() {

        /** 判断是否以设置server组件 **/
        if (getServer() == null) {
            /** 重新加载Catalina **/
            load();
        }

        /** 加载Catalina失败 **/
        if (getServer() == null) {
            log.fatal("Cannot start server. Server instance is not configured.");
            return;
        }

        /** 获取Tomcat启动时间 **/
        long t1 = System.nanoTime();

        /** 启动Server组件 **/
        try {
            getServer().start();
        }
        /** 发生LifecycleException异常 销毁Server组件 **/
        catch (LifecycleException e) {
            log.fatal(sm.getString("catalina.serverStartFail"), e);
            try {
                /** 销毁Server组件 **/
                getServer().destroy();
            } catch (LifecycleException e1) {
                log.debug("destroy() failed for failed Server ", e1);
            }
            return;
        }

        /** 打印tomcat执行启动事件 **/
        long t2 = System.nanoTime();
        if(log.isInfoEnabled()) {
            log.info("Server startup in " + ((t2 - t1) / 1000000) + " ms");
        }

        /**  判断是否需要向JVM注册一个钩子线程， **/
        if (useShutdownHook) {
            /** 初始化CatalinaShutdownHook **/
            if (shutdownHook == null) {
                shutdownHook = new CatalinaShutdownHook();
            }
            /**将shutdownHook注册JMV**/
            Runtime.getRuntime().addShutdownHook(shutdownHook);

            /**设置LogManager中注册到JVM钩子线程在JVM停止时不会执行 **/
            LogManager logManager = LogManager.getLogManager();
            if (logManager instanceof ClassLoaderLogManager) {
                ((ClassLoaderLogManager) logManager).setUseShutdownHook(
                        false);
            }
        }
        /** 执行Bootstarp.main函数的指令为start时会设置  Catalina的await属性为true，**/
        if (await) {
            /**
             * 阻塞tomcat主线程，当主线程从阻塞中唤醒并从await()方法返回表示tomcat被停止
             *
             * 如下两种情况会导致tomcat停止
             *
             * 1 内部调用server组件stopAwait()方法
             *
             * 2 接收到客户端发起SHUTDOWN命令socket请求
             **/
            await();
            /** 停止Catalina **/
            stop();
        }
    }


    /**
     * 停止Catalina
     */
    public void stop() {

        try {
            /** 判断是否向JVM注册了钩子线程 **/
            if (useShutdownHook) {
                /**  向JVM清理掉注册的钩子线程 **/
                Runtime.getRuntime().removeShutdownHook(shutdownHook);

                /**设置LogManager中注册到JVM钩子线程在JVM停止时会执行，重置日志系统 **/
                LogManager logManager = LogManager.getLogManager();
                if (logManager instanceof ClassLoaderLogManager) {
                    ((ClassLoaderLogManager) logManager).setUseShutdownHook(
                            true);
                }
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
        }

        /** 获取Tomcat Server组件，调用stop()关闭，destroy()清理 **/
        try {
            Server s = getServer();
            LifecycleState state = s.getState();
            /** 如果Tomcat Server组件 状态为'STOPPING_PREP', 'DESTROYED' 忽略当前动作**/
            if (LifecycleState.STOPPING_PREP.compareTo(state) <= 0
                    && LifecycleState.DESTROYED.compareTo(state) >= 0) {
                // Nothing to do. stop() was already called
            } else {
                s.stop();
                s.destroy();
            }
        } catch (LifecycleException e) {
            log.error("Catalina.stop", e);
        }

    }


    /**
     * 启动一个Socket等待接受shutdown命令，用来停止Tomcat
     */
    public void await() {

        getServer().await();

    }


    /**
     * 打印此应用程序的使用信息。
     */
    protected void usage() {

        System.out.println
            ("usage: java org.apache.catalina.startup.Catalina"
             + " [ -config {pathname} ]"
             + " [ -nonaming ] "
             + " { -help | start | stop }");

    }


    /**
     *  检查java.io.tmdir系统属性值对应目录是否存在
     */
    protected void initDirs() {
        String temp = System.getProperty("java.io.tmpdir");
        if (temp == null || (!(new File(temp)).isDirectory())) {
            log.error(sm.getString("embedded.notmp", temp));
        }
    }


    /**
     * 调用System类的方法重定向标准输出和标准错误输出
     */
    protected void initStreams() {
        // Replace System.out and System.err with a custom PrintStream
        System.setOut(new SystemLogHandler(System.out));
        System.setErr(new SystemLogHandler(System.err));
    }

    /** 初始化JNDI系统属性 **/
    protected void initNaming() {
        /**  是否开启JNDI服务 **/
        if (!useNaming) {
            log.info( "Catalina naming disabled");
            /** 在系统属性中设置JNDI服务关闭 **/
            System.setProperty("catalina.useNaming", "false");
        } else {
            /** 在系统属性中设置JNDI服务开启 **/
            System.setProperty("catalina.useNaming", "true");


            /** 在系统属性中设置JNDI属性"org.apache.naming" **/
            String value = "org.apache.naming";
            String oldValue =
                System.getProperty(javax.naming.Context.URL_PKG_PREFIXES);
            if (oldValue != null) {
                value = value + ":" + oldValue;
            }
            System.setProperty(javax.naming.Context.URL_PKG_PREFIXES, value);
            if( log.isDebugEnabled() ) {
                log.debug("Setting naming prefix=" + value);
            }

            /** 在系统属性中设置JNDI属性"java.naming.factory.initial" **/
            value = System.getProperty
                (javax.naming.Context.INITIAL_CONTEXT_FACTORY);
            if (value == null) {
                System.setProperty
                    (javax.naming.Context.INITIAL_CONTEXT_FACTORY,
                     "org.apache.naming.java.javaURLContextFactory");
            } else {
                log.debug( "INITIAL_CONTEXT_FACTORY already set " + value );
            }
        }
    }


    /**
     * 从catalina.properties获取受保护类，注册到Security中
     */
    protected void setSecurityProtection(){
        SecurityConfig securityConfig = SecurityConfig.newInstance();
        securityConfig.setPackageDefinition();
        securityConfig.setPackageAccess();
    }


    // --------------------------------------- CatalinaShutdownHook Inner Class

    /**
     * tomcat关闭注册到JVM中钩子线程
     */
    protected class CatalinaShutdownHook extends Thread {

        @Override
        public void run() {
            try {
                /** 如果tomcat server组件存在则调用Catalina.this.stop() **/
                if (getServer() != null) {
                    Catalina.this.stop();
                }
            } catch (Throwable ex) {
                ExceptionUtils.handleThrowable(ex);
                log.error(sm.getString("catalina.shutdownHookFail"), ex);
            } finally {
                /**  关闭ClassLoaderLogManager **/
                LogManager logManager = LogManager.getLogManager();
                if (logManager instanceof ClassLoaderLogManager) {
                    ((ClassLoaderLogManager) logManager).shutdown();
                }
            }
        }
    }


    private static final Log log = LogFactory.getLog(Catalina.class);

}


// ------------------------------------------------------------ Private Classes


/**
 * 为堆栈顶部对象设置父类加载器的规则，*必须是<code> Container </ code>
 */

final class SetParentClassLoaderRule extends Rule {

    public SetParentClassLoaderRule(ClassLoader parentClassLoader) {

        this.parentClassLoader = parentClassLoader;

    }

    ClassLoader parentClassLoader = null;

    @Override
    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {

        if (digester.getLogger().isDebugEnabled()) {
            digester.getLogger().debug("Setting parent class loader");
        }

        Container top = (Container) digester.peek();
        top.setParentClassLoader(parentClassLoader);

    }


}
