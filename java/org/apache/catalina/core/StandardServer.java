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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessControlException;
import java.util.Random;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.ObjectName;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.deploy.NamingResourcesImpl;
import org.apache.catalina.mbeans.MBeanFactory;
import org.apache.catalina.startup.Catalina;
import org.apache.catalina.util.ExtensionValidator;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.catalina.util.ServerInfo;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.StringCache;
import org.apache.tomcat.util.res.StringManager;


/**
 * Standard implementation of the <b>Server</b> interface, available for use
 * (but not required) when deploying and starting Catalina.
 *
 * @author Craig R. McClanahan
 */
public final class StandardServer extends LifecycleMBeanBase implements Server {

    private static final Log log = LogFactory.getLog(StandardServer.class);


    // ------------------------------------------------------------ Constructor


    /**
     * 默认构造函数。
     */
    public StandardServer() {
        super();
        globalNamingResources = new NamingResourcesImpl();
        globalNamingResources.setContainer(this);

        /** 判断是否开启JNDI服务 **/
        if (isUseNaming()) {
            namingContextListener = new NamingContextListener();
            addLifecycleListener(namingContextListener);
        } else {
            namingContextListener = null;
        }
    }

    // ---------JNDI相关属性 star
    private javax.naming.Context globalNamingContext = null;

    private NamingResourcesImpl globalNamingResources = null;

    private final NamingContextListener namingContextListener;
    // ---------JNDI相关属性 end


    /**
     * Service 组件数组（一个Server 存在多个Service子组件）
     */
    private Service services[] = new Service[0];

    /**
     * services组件 锁
     */
    private final Object servicesLock = new Object();


    /** 管理打印日志模板组件 **/
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);

    /**
     * 属性变更监听器
     */
    final PropertyChangeSupport support = new PropertyChangeSupport(this);


    // ---------监听ShutDown服务相关属性 star
    /**
     * 执行shutdown，字符串指令
     */
    private String shutdown = "SHUTDOWN";

    /**
     * 标识是否已停止了对ShutDown 指令监听的服务
     */
    private volatile boolean stopAwait = false;

    /**
     * 执行ShutDown命令处理线程
     */
    private volatile Thread awaitThread = null;

    /**
     * 执行ShutDown命令服务端监听Socket
     */
    private volatile ServerSocket awaitSocket = null;

    /**
     * 执行ShutDown 服务端监听Socket端口号。
     */
    private int port = 8005;

    /**
     * 执行ShutDown 服务端监听Socket地址。
     */
    private String address = "localhost";

    /**
     * 执行ShutDown 字符串超过1024个字符，则使用<strong> </ strong>的随机数生成器。
     */
    private Random random = null;

    // ---------监听ShutDown服务相关属性 end
    /**
     * catalina组件
     */
    private Catalina catalina = null;

    /**
     * 父类加载器
     */
    private ClassLoader parentClassLoader = null;


    /**
     * Tomcat 安装目录文件
     */
    private File catalinaHome = null;

    /**
     * Tomcat 工作目录文件
     */
    private File catalinaBase = null;


    private final Object namingToken = new Object();


    // ------------------------------------------------------------- Properties

    @Override
    public Object getNamingToken() {
        return namingToken;
    }


    @Override
    public javax.naming.Context getGlobalNamingContext() {
        return (this.globalNamingContext);
    }

    public void setGlobalNamingContext
        (javax.naming.Context globalNamingContext) {
        this.globalNamingContext = globalNamingContext;
    }



    @Override
    public NamingResourcesImpl getGlobalNamingResources() {
        return (this.globalNamingResources);
    }


    /**
     * 设置globalNamingResources属性
     */
    @Override
    public void setGlobalNamingResources
        (NamingResourcesImpl globalNamingResources) {

        /** 获取设置前globalNamingResources **/
        NamingResourcesImpl oldGlobalNamingResources =
            this.globalNamingResources;

        /** 设置globalNamingResources **/
        this.globalNamingResources = globalNamingResources;
        this.globalNamingResources.setContainer(this);

        /** 触发属性变更通知 **/
        support.firePropertyChange("globalNamingResources",
                                   oldGlobalNamingResources,
                                   this.globalNamingResources);
    }


    /**
     * 获取当前的Tomcat服务器版本号
     */
    public String getServerInfo() {
        return ServerInfo.getServerInfo();
    }


    /**
     * 返回当前服务器构建的时间戳
     */
    public String getServerBuilt() {
        return ServerInfo.getServerBuilt();
    }


    /**
     * 返回当前服务器的版本号
     */
    public String getServerNumber() {
        return ServerInfo.getServerNumber();
    }


    /**
     * 返回我们监听的关闭命令的端口号。
     */
    @Override
    public int getPort() {
        return (this.port);
    }


    /**
     * 设置我们监听的关闭命令的端口号。
     */
    @Override
    public void setPort(int port) {
        this.port = port;
    }


    /**
     * 返回我们监听的地址以获取shutdown命令
     */
    @Override
    public String getAddress() {
        return (this.address);
    }


    /**
     * 设置我们侦听shutdown地址的地址。
     */
    @Override
    public void setAddress(String address) {
        this.address = address;
    }

    /**
     * 返回我们正在等待的shutdown命令字符串
     */
    @Override
    public String getShutdown() {
        return (this.shutdown);
    }


    /**
     * 设置我们正在等待的shutdown命令。
     */
    @Override
    public void setShutdown(String shutdown) {
        this.shutdown = shutdown;
    }


    /**
     * 返回外部Catalina 组件
     */
    @Override
    public Catalina getCatalina() {
        return catalina;
    }


    /**
     * 设置外部Catalina 组件
     */
    @Override
    public void setCatalina(Catalina catalina) {
        this.catalina = catalina;
    }

    // --------------------------------------------------------- Server Methods


    /**
     * 将service子组件加到Tomcat Server组件中
     */
    @Override
    public void addService(Service service) {

        /** service 反向关联外部 Server组件 **/
        service.setServer(this);

        synchronized (servicesLock) {
            /** 将service组件添加到Server.ervices数组类型属性中 **/
            Service results[] = new Service[services.length + 1];
            System.arraycopy(services, 0, results, 0, services.length);
            results[services.length] = service;
            services = results;

            /** 如果当前Server组件已经启动，则启动添加Service组件 **/
            if (getState().isAvailable()) {
                try {
                    service.start();
                } catch (LifecycleException e) {
                    // Ignore
                }
            }

            /** 将service属性更改通知给监听器  **/
            support.firePropertyChange("service", null, service);
        }

    }

    /**
     * 1 设置stopAwait标识为true，stopAwait用来判断tomcat主线程是否要退出
     *
     * 2 关闭Socket服务，不在监听shutdown命令
     */
    public void stopAwait() {
        /** **/
        stopAwait=true;
        Thread t = awaitThread;
        if (t != null) {
            ServerSocket s = awaitSocket;
            if (s != null) {
                awaitSocket = null;
                try {
                    s.close();
                } catch (IOException e) {
                    // Ignored
                }
            }
            t.interrupt();
            try {
                t.join(1000);
            } catch (InterruptedException e) {
                // Ignored
            }
        }
    }

    /**
     * 阻塞tomcat主线程，
     *
     * 只要stopAwait不为true， tomcat主线程在此无限循环.监听到客户端发起SHUTDOWN命令后退出
     */
    @Override
    public void await() {
        if( port == -2 ) {
            return;
        }
        if( port==-1 ) {
            try {
                awaitThread = Thread.currentThread();
                while(!stopAwait) {
                    try {
                        Thread.sleep( 10000 );
                    } catch( InterruptedException ex ) {
                        // continue and check the flag
                    }
                }
            } finally {
                awaitThread = null;
            }
            return;
        }

        /**  创建服务端监听shutdown命令 Socket **/
        try {
            awaitSocket = new ServerSocket(port, 1,
                    InetAddress.getByName(address));
        } catch (IOException e) {
            log.error("StandardServer.await: create[" + address
                               + ":" + port
                               + "]: ", e);
            return;
        }

        try {
            /** 获取当前线程 **/
            awaitThread = Thread.currentThread();

            /** 只要stopAwait不为true， tomcat主线程在此无限循环.监听到客户端发起SHUTDOWN命令后退出 **/
            while (!stopAwait) {
                ServerSocket serverSocket = awaitSocket;
                if (serverSocket == null) {
                    break;
                }

                // Wait for the next connection
                Socket socket = null;
                StringBuilder command = new StringBuilder();
                try {
                    /** 监听阻塞当前线程 **/
                    InputStream stream;
                    long acceptStartTime = System.currentTimeMillis();
                    try {
                        socket = serverSocket.accept();
                        socket.setSoTimeout(10 * 1000);  // Ten seconds
                        stream = socket.getInputStream();
                    } catch (SocketTimeoutException ste) {
                        log.warn(sm.getString("standardServer.accept.timeout",
                                Long.valueOf(System.currentTimeMillis() - acceptStartTime)), ste);
                        continue;
                    } catch (AccessControlException ace) {
                        log.warn("StandardServer.accept security exception: "
                                + ace.getMessage(), ace);
                        continue;
                    } catch (IOException e) {
                        if (stopAwait) {
                            break;
                        }
                        log.error("StandardServer.await: accept: ", e);
                        break;
                    }

                    /** 发生指令的字符数大于1024，则最大读取字符扩容到
                     * expected += (random.nextInt() % 1024)
                     */
                    int expected = 1024; // Cut off to avoid DoS attack
                    while (expected < shutdown.length()) {
                        if (random == null)
                            random = new Random();
                        expected += (random.nextInt() % 1024);
                    }

                    /** 读取指令字符串 **/
                    while (expected > 0) {
                        int ch = -1;
                        try {
                            ch = stream.read();
                        } catch (IOException e) {
                            log.warn("StandardServer.await: read: ", e);
                            ch = -1;
                        }
                        /** 遍历到控制字符或EOF（-1）终止读取 **/
                        if (ch < 32 || ch == 127) {
                            break;
                        }
                        command.append((char) ch);
                        expected--;
                    }
                } finally {
                    // Close the socket now that we are done with it
                    try {
                        if (socket != null) {
                            socket.close();
                        }
                    } catch (IOException e) {
                        // Ignore
                    }
                }

                /**  发生执行是否为 shutdown指令字符串相同，相同则跳出循环Tomcat主线程退出**/
                boolean match = command.toString().equals(shutdown);
                if (match) {
                    log.info(sm.getString("standardServer.shutdownViaPort"));
                    break;
                } else
                    log.warn("StandardServer.await: Invalid command '"
                            + command.toString() + "' received");
            }
        } finally {
            ServerSocket serverSocket = awaitSocket;
            awaitThread = null;
            awaitSocket = null;

            // Close the server socket and return
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }


    /**
     * @return the specified Service (if it exists); otherwise return
     * <code>null</code>.
     *
     * @param name Name of the Service to be returned
     */
    @Override
    public Service findService(String name) {

        if (name == null) {
            return (null);
        }
        synchronized (servicesLock) {
            for (int i = 0; i < services.length; i++) {
                if (name.equals(services[i].getName())) {
                    return (services[i]);
                }
            }
        }
        return (null);

    }


    /**
     * 返回Server组件所有Service子组件
     */
    @Override
    public Service[] findServices() {

        return services;

    }

    /**
     * 返回Server组件所有Service子组件注册到JMX中的 对象名称ObjectName
     */
    public ObjectName[] getServiceNames() {
        ObjectName onames[]=new ObjectName[ services.length ];
        for( int i=0; i<services.length; i++ ) {
            onames[i]=((StandardService)services[i]).getObjectName();
        }
        return onames;
    }


    /**
     * 从Server组件中删除Service子组件
     *
     * @param service The Service to be removed
     */
    @Override
    public void removeService(Service service) {

        synchronized (servicesLock) {
            /** 从Service子组件数组找到删除 service 子组件 **/
            int j = -1;
            for (int i = 0; i < services.length; i++) {
                if (service == services[i]) {
                    j = i;
                    break;
                }
            }
            /** 没有找到忽略此动作 **/
            if (j < 0)
                return;

            /** 对删除service子组件 停止动作 **/
            try {
                services[j].stop();
            } catch (LifecycleException e) {
                // Ignore
            }

            /** 对service 数组中在删除service子组件后service子组件在数组中前移 **/
            int k = 0;
            Service results[] = new Service[services.length - 1];
            for (int i = 0; i < services.length; i++) {
                if (i != j)
                    results[k++] = services[i];
            }
            services = results;

            /** support通知service属性变更 **/
            support.firePropertyChange("service", service, null);
        }
    }


    @Override
    public File getCatalinaBase() {
        if (catalinaBase != null) {
            return catalinaBase;
        }

        catalinaBase = getCatalinaHome();
        return catalinaBase;
    }


    @Override
    public void setCatalinaBase(File catalinaBase) {
        this.catalinaBase = catalinaBase;
    }


    @Override
    public File getCatalinaHome() {
        return catalinaHome;
    }


    @Override
    public void setCatalinaHome(File catalinaHome) {
        this.catalinaHome = catalinaHome;
    }


    // --------------------------------------------------------- Public Methods

    /**
     * 向此组件添加属性更改侦听器
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
        StringBuilder sb = new StringBuilder("StandardServer[");
        sb.append(getPort());
        sb.append("]");
        return (sb.toString());
    }


    /**
     * JMX 注册StoreConfig JMX bean
     */
    public synchronized void storeConfig() throws InstanceNotFoundException, MBeanException {
        try {
            // Note: Hard-coded domain used since this object is per Server/JVM
            ObjectName sname = new ObjectName("Catalina:type=StoreConfig");
            if (mserver.isRegistered(sname)) {
                mserver.invoke(sname, "storeConfig", null, null);
            } else {
                log.error(sm.getString("standardServer.storeConfig.notAvailable", sname));
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(t);
        }
    }


    public synchronized void storeContext(Context context) throws InstanceNotFoundException, MBeanException {
        try {
            // Note: Hard-coded domain used since this object is per Server/JVM
            ObjectName sname = new ObjectName("Catalina:type=StoreConfig");
            if (mserver.isRegistered(sname)) {
                mserver.invoke(sname, "store",
                    new Object[] {context},
                    new String [] { "java.lang.String"});
            } else {
                log.error(sm.getString("standardServer.storeConfig.notAvailable", sname));
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(t);
        }
    }


    /**
     * 返回Tomcat是否开启JDNI服务
     */
    private boolean isUseNaming() {
        boolean useNaming = true;
        // Reading the "catalina.useNaming" environment variable
        String useNamingProperty = System.getProperty("catalina.useNaming");
        if ((useNamingProperty != null)
            && (useNamingProperty.equals("false"))) {
            useNaming = false;
        }
        return useNaming;
    }


    /**
     * 组件启动模板方法实现
     */
    @Override
    protected void startInternal() throws LifecycleException {

        /** 通知监听器当前组件触发 CONFIGURE_START_EVENT事件 **/
        fireLifecycleEvent(CONFIGURE_START_EVENT, null);
        /** 更正当前组件状态为STARTING  **/
        setState(LifecycleState.STARTING);
        /** 启动JNDI服务 **/
        globalNamingResources.start();

        /** 启动所有service组件 **/
        synchronized (servicesLock) {
            for (int i = 0; i < services.length; i++) {
                services[i].start();
            }
        }
    }


    /**
     * 组件停止模板方法实现
     */
    @Override
    protected void stopInternal() throws LifecycleException {


        /** 通知监听器当前组件触发 CONFIGURE_STOP_EVENT事件 **/
        fireLifecycleEvent(CONFIGURE_STOP_EVENT, null);

        /** 更正当前组件状态为STOPPING  **/
        setState(LifecycleState.STOPPING);

        /** 关闭所有service组件 **/
        for (int i = 0; i < services.length; i++) {
            services[i].stop();
        }

        /** 关闭JNDI服务 **/
        globalNamingResources.stop();

        /** 设置stopAwait标识为true,关闭Socket服务，不在监听shutdown命令 **/
        stopAwait();
    }

    /**
     * 组件初始化模板方法实现
     */
    @Override
    protected void initInternal() throws LifecycleException {

        super.initInternal();

        //StringCache注册到JMX bean中 StringCache ???
        onameStringCache = register(new StringCache(), "type=StringCache");

        //MBeanFactory注册到JMX bean中 MBeanFactory ???
        MBeanFactory factory = new MBeanFactory();
        factory.setContainer(this);
        onameMBeanFactory = register(factory, "type=MBeanFactory");

        /** JNDI服务初始化 **/
        globalNamingResources.init();

        /** 读取Shared类加载器 管理的jar文件，将包含MANIFEST的JAR文件，添加到容器的清单资源中 **/
        if (getCatalina() != null) {
            /** 获取Shared类加载器 **/
            ClassLoader cl = getCatalina().getParentClassLoader();

            while (cl != null && cl != ClassLoader.getSystemClassLoader()) {
                if (cl instanceof URLClassLoader) {
                    URL[] urls = ((URLClassLoader) cl).getURLs();
                    for (URL url : urls) {
                        if (url.getProtocol().equals("file")) {
                            try {
                                File f = new File (url.toURI());
                                if (f.isFile() &&
                                        f.getName().endsWith(".jar")) {
                                    ExtensionValidator.addSystemResource(f);
                                }
                            } catch (URISyntaxException e) {
                                // Ignore
                            } catch (IOException e) {
                                // Ignore
                            }
                        }
                    }
                }
                cl = cl.getParent();
            }
        }
        /** 初始化所有service组件 **/
        for (int i = 0; i < services.length; i++) {
            services[i].init();
        }
    }

    /**
     * 组件销毁模板方法实现
     */
    @Override
    protected void destroyInternal() throws LifecycleException {
        /** 遍历所有service子组件执行销毁动作 **/
        for (int i = 0; i < services.length; i++) {
            services[i].destroy();
        }
        /** JNDI 资源管理销毁 **/
        globalNamingResources.destroy();

        /** jmx bean注销MBeanFactory **/
        unregister(onameMBeanFactory);

        /** jmx bean注销StringCache **/
        unregister(onameStringCache);

        /** 调用LifecycleMBeanBase.destroyInternal
         * 将当前组件对象从jmx 注销
         */
        super.destroyInternal();
    }

    /**
     * 获取父类加载器，这里parentClassLoader默认为null
     * 返回外部组件catalina.getParentClassLoader()默认是Shared类加载器
     */
    @Override
    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null)
            return (parentClassLoader);
        if (catalina != null) {
            return (catalina.getParentClassLoader());
        }
        return (ClassLoader.getSystemClassLoader());
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
     * StringCache注册到JXM ObjectName
     */
    private ObjectName onameStringCache;


    /**
     * MBeanFactory注册到JXM ObjectName
     */
    private ObjectName onameMBeanFactory;

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
     * 获取子组件Service 组件域名空间作为自己域名空间
     */
    @Override
    protected String getDomainInternal() {

        String domain = null;

        Service[] services = findServices();
        if (services.length > 0) {
            Service service = services[0];
            if (service != null) {
                domain = service.getDomain();
            }
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
     * 域名空间：对象属性集合
     * getDomain():getObjectNameKeyProperties()
     * 该方法父类LifecycleMBeanBase模板方法实现，返回对象属性集合
     */
    @Override
    protected final String getObjectNameKeyProperties() {
        return "type=Server";
    }

}
