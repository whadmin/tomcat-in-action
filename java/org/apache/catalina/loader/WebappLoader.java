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
package org.apache.catalina.loader;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;

import javax.management.ObjectName;
import javax.servlet.ServletContext;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Loader;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.UDecoder;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.res.StringManager;

/**
 * 类加载器实现，专门以最有效的方式处理Web应用程序，
 * 同时具有catalina意识（所有对资源的访问都是通过@link org.apache.catalina.webresourceroot）
 * 这个类加载器支持检测修改的Java类，可以用来实现自动重载支持

 在调用<code>start()之前，通过其上下文的资源子级配置该类加载器。当需要一个新的类时，
 将首先咨询这些资源以定位该类。如果不存在，则将使用系统类加载器。
 *
 */
public class WebappLoader extends LifecycleMBeanBase
    implements Loader, PropertyChangeListener {


    // ----------------------------------------------------------- Constructors
    /**
     * 实例化WebappLoader
     */
    public WebappLoader() {
        this(null);
    }

    /**
     * 实例化WebappLoader，并指定WebappClassLoader类加载器对应父类加载器
     */
    public WebappLoader(ClassLoader parent) {
        super();
        this.parentClassLoader = parent;
    }

    // ----------------------------------------------------- Instance Variables

    /**
     * WebappClassLoader类加载器
     */
    private WebappClassLoaderBase classLoader = null;


    /**
     * 关联的context容器组件
     */
    private Context context = null;


    /**
     * 是否允许使用代理类加载器（非默认类加载器）
     */
    private boolean delegate = false;


    /**
     * WebappClassLoader类加载器实现类
     */
    private String loaderClass = ParallelWebappClassLoader.class.getName();


    /**
     * WebappClassLoader类加载器对应的父类加载器
     */
    private ClassLoader parentClassLoader = null;


    /**
     * 是否开启热加载机制
     * Loader组件定时任务触发调用modified方法检查检查已经加载的资源是否有修改/增加/删减,如果发现资源由变更触发StandardContext组件reload;
     */
    private boolean reloadable = false;


    /**
     * 日志格式管理组件
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


    /**
     * 属性变更监听器
     */
    protected final PropertyChangeSupport support = new PropertyChangeSupport(this);


    /**
     * WebappClassLoader类加载器加载类文件路径
     */
    private String classpath = null;


    // ------------------------------------------------------------- Properties


    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }


    @Override
    public Context getContext() {
        return context;
    }


    @Override
    public void setContext(Context context) {
        if (this.context == context) {
            return;
        }

        /** 如果当前容器组件不是运行，则抛出异常 **/
        if (getState().isAvailable()) {
            throw new IllegalStateException(
                    sm.getString("webappLoader.setContext.ise"));
        }

        /** 原始关联的context属性变更监听器中删除当前对象 **/
        if (this.context != null) {
            this.context.removePropertyChangeListener(this);
        }

        /** 将WebappLoader属性更改通知给监听器  **/
        Context oldContext = this.context;
        this.context = context;
        support.firePropertyChange("context", oldContext, this.context);

        if (this.context != null) {
            /** 从context组件获取配置是否热加载，设置到reloadable **/
            setReloadable(this.context.getReloadable());
            /** 将当前对象作为属性变更监听器设置到context容器组件 **/
            this.context.addPropertyChangeListener(this);
        }
    }


    @Override
    public boolean getDelegate() {
        return this.delegate;
    }

    @Override
    public void setDelegate(boolean delegate) {
        boolean oldDelegate = this.delegate;
        this.delegate = delegate;
        support.firePropertyChange("delegate", Boolean.valueOf(oldDelegate),
                                   Boolean.valueOf(this.delegate));
    }



    public String getLoaderClass() {
        return (this.loaderClass);
    }


    public void setLoaderClass(String loaderClass) {
        this.loaderClass = loaderClass;
    }


    @Override
    public boolean getReloadable() {
        return this.reloadable;
    }


    @Override
    public void setReloadable(boolean reloadable) {
        // Process this property change
        boolean oldReloadable = this.reloadable;
        this.reloadable = reloadable;
        support.firePropertyChange("reloadable",
                                   Boolean.valueOf(oldReloadable),
                                   Boolean.valueOf(this.reloadable));
    }


    // --------------------------------------------------------- Public Methods

    /**
     * 添加一个属性监听器
     */
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }

    /**
     * 删除一个属性监听器
     */
    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }



    /**
     * 定时任务处理，
     * reloadable=true表示支持热加载，定时执行modified方法检查查已经加载的资源是否有修改/增加/删减,如果发现资源由变更触发StandardContext组件reload
     */
    @Override
    public void backgroundProcess() {
        if (reloadable && modified()) {
            try {
                Thread.currentThread().setContextClassLoader
                    (WebappLoader.class.getClassLoader());
                if (context != null) {
                    context.reload();
                }
            } finally {
                /** 将当前组件classLoader设置到线程的上下文类加载器中 **/
                if (context != null && context.getLoader() != null) {
                    Thread.currentThread().setContextClassLoader
                        (context.getLoader().getClassLoader());
                }
            }
        }
    }


    /**
     * 获取类加载器加载的url资源路径，转为为字符串数组返回
     */
    public String[] getLoaderRepositories() {
        if (classLoader == null) {
            return new String[0];
        }
        URL[] urls = classLoader.getURLs();
        String[] result = new String[urls.length];
        for (int i = 0; i < urls.length; i++) {
            result[i] = urls[i].toExternalForm();
        }
        return result;
    }

    /**
     * 获取类加载器加载的url资源路径，转为为字符串返回
     */
    public String getLoaderRepositoriesString() {
        String repositories[]=getLoaderRepositories();
        StringBuilder sb=new StringBuilder();
        for( int i=0; i<repositories.length ; i++ ) {
            sb.append( repositories[i]).append(":");
        }
        return sb.toString();
    }



    public String getClasspath() {
        return classpath;
    }


    /**
     * 检查类加载器classLoader中已经加载的资源是否有修改/增加/删减
     */
    @Override
    public boolean modified() {
        return classLoader != null ? classLoader.modified() : false ;
    }



    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("WebappLoader[");
        if (context != null)
            sb.append(context.getName());
        sb.append("]");
        return (sb.toString());
    }


    /**
     * 启动组件模板方法实现
     */
    @Override
    protected void startInternal() throws LifecycleException {

        if (log.isDebugEnabled())
            log.debug(sm.getString("webappLoader.starting"));

        /** 如果context组件子组件resources为null,直接返回 **/
        if (context.getResources() == null) {
            log.info("No resources for " + context);
            setState(LifecycleState.STARTING);
            return;
        }

        try {
            /** 创建webAppClassLoader **/
            classLoader = createClassLoader();

            /** 将context组件子组件resources设置给webAppClassLoader属性resources **/
            classLoader.setResources(context.getResources());
            classLoader.setDelegate(this.delegate);

            setClassPath();

            setPermissions();

            ((Lifecycle) classLoader).start();

            String contextName = context.getName();
            if (!contextName.startsWith("/")) {
                contextName = "/" + contextName;
            }
            ObjectName cloname = new ObjectName(context.getDomain() + ":type=" +
                    classLoader.getClass().getSimpleName() + ",host=" +
                    context.getParent().getName() + ",context=" + contextName);
            Registry.getRegistry(null, null)
                .registerComponent(classLoader, cloname, null);

        } catch (Throwable t) {
            t = ExceptionUtils.unwrapInvocationTargetException(t);
            ExceptionUtils.handleThrowable(t);
            log.error( "LifecycleException ", t );
            throw new LifecycleException("start: ", t);
        }

        setState(LifecycleState.STARTING);
    }


    /**
     * 停止组件模板方法实现
     */
    @Override
    protected void stopInternal() throws LifecycleException {

        if (log.isDebugEnabled())
            log.debug(sm.getString("webappLoader.stopping"));

        setState(LifecycleState.STOPPING);

        // Remove context attributes as appropriate
        ServletContext servletContext = context.getServletContext();
        servletContext.removeAttribute(Globals.CLASS_PATH_ATTR);

        // Throw away our current class loader if any
        if (classLoader != null) {
            try {
                classLoader.stop();
            } finally {
                classLoader.destroy();
            }

            // classLoader must be non-null to have been registered
            try {
                String contextName = context.getName();
                if (!contextName.startsWith("/")) {
                    contextName = "/" + contextName;
                }
                ObjectName cloname = new ObjectName(context.getDomain() + ":type=" +
                        classLoader.getClass().getSimpleName() + ",host=" +
                        context.getParent().getName() + ",context=" + contextName);
                Registry.getRegistry(null, null).unregisterComponent(cloname);
            } catch (Exception e) {
                log.error("LifecycleException ", e);
            }
        }


        classLoader = null;
    }


    // ----------------------------------------- PropertyChangeListener Methods


    /**
     * 作为属性监听器处理属性变更事件
     */
    @Override
    public void propertyChange(PropertyChangeEvent event) {

        // Validate the source of this event
        if (!(event.getSource() instanceof Context))
            return;

        // Process a relevant property change
        if (event.getPropertyName().equals("reloadable")) {
            try {
                setReloadable
                    ( ((Boolean) event.getNewValue()).booleanValue() );
            } catch (NumberFormatException e) {
                log.error(sm.getString("webappLoader.reloadable",
                                 event.getNewValue().toString()));
            }
        }
    }


    // ------------------------------------------------------- Private Methods

    /**
     * Create associated classLoader.
     */
    private WebappClassLoaderBase createClassLoader()
        throws Exception {

        Class<?> clazz = Class.forName(loaderClass);
        WebappClassLoaderBase classLoader = null;

        if (parentClassLoader == null) {
            parentClassLoader = context.getParentClassLoader();
        }
        Class<?>[] argTypes = { ClassLoader.class };
        Object[] args = { parentClassLoader };
        Constructor<?> constr = clazz.getConstructor(argTypes);
        classLoader = (WebappClassLoaderBase) constr.newInstance(args);

        return classLoader;
    }


    /**
     * Configure associated class loader permissions.
     */
    private void setPermissions() {

        if (!Globals.IS_SECURITY_ENABLED)
            return;
        if (context == null)
            return;

        // Tell the class loader the root of the context
        ServletContext servletContext = context.getServletContext();

        // Assigning permissions for the work directory
        File workDir =
            (File) servletContext.getAttribute(ServletContext.TEMPDIR);
        if (workDir != null) {
            try {
                String workDirPath = workDir.getCanonicalPath();
                classLoader.addPermission
                    (new FilePermission(workDirPath, "read,write"));
                classLoader.addPermission
                    (new FilePermission(workDirPath + File.separator + "-",
                                        "read,write,delete"));
            } catch (IOException e) {
                // Ignore
            }
        }

        for (URL url : context.getResources().getBaseUrls()) {
           classLoader.addPermission(url);
        }
    }


    /**
     * Set the appropriate context attribute for our class path.  This
     * is required only because Jasper depends on it.
     */
    private void setClassPath() {

        /** 如果context为null 直接返回 **/
        if (context == null)
            return;

        /** 如果context.ServletContext为null 直接返回 **/
        ServletContext servletContext = context.getServletContext();
        if (servletContext == null)
            return;

        StringBuilder classpath = new StringBuilder();

        /** 获取WebAppClassLoader **/
        ClassLoader loader = getClassLoader();

        /** 获取WebAppClassLoade父类加载器 **/
        if (delegate && loader != null) {
            // Skip the webapp loader for now as delegation is enabled
            loader = loader.getParent();
        }

        while (loader != null) {
            if (!buildClassPath(classpath, loader)) {
                break;
            }
            loader = loader.getParent();
        }

        if (delegate) {
            // Delegation was enabled, go back and add the webapp paths
            loader = getClassLoader();
            if (loader != null) {
                buildClassPath(classpath, loader);
            }
        }

        this.classpath = classpath.toString();

        // Store the assembled class path as a servlet context attribute
        servletContext.setAttribute(Globals.CLASS_PATH_ATTR, this.classpath);
    }


    private boolean buildClassPath(StringBuilder classpath, ClassLoader loader) {
        if (loader instanceof URLClassLoader) {
            URL repositories[] = ((URLClassLoader) loader).getURLs();
                for (int i = 0; i < repositories.length; i++) {
                    String repository = repositories[i].toString();
                    if (repository.startsWith("file://"))
                        repository = UDecoder.URLDecode(repository.substring(7));
                    else if (repository.startsWith("file:"))
                        repository = UDecoder.URLDecode(repository.substring(5));
                    else
                        continue;
                    if (repository == null)
                        continue;
                    if (classpath.length() > 0)
                        classpath.append(File.pathSeparator);
                    classpath.append(repository);
                }
        } else if (loader == ClassLoader.getSystemClassLoader()){
            // Java 9 onwards. The internal class loaders no longer extend
            // URLCLassLoader
            String cp = System.getProperty("java.class.path");
            if (cp != null && cp.length() > 0) {
                if (classpath.length() > 0) {
                    classpath.append(File.pathSeparator);
                }
                classpath.append(cp);
            }
            return false;
        } else {
            log.info( "Unknown loader " + loader + " " + loader.getClass());
            return false;
        }
        return true;
    }


    private static final Log log = LogFactory.getLog(WebappLoader.class);


    @Override
    protected String getDomainInternal() {
        return context.getDomain();
    }


    @Override
    protected String getObjectNameKeyProperties() {

        StringBuilder name = new StringBuilder("type=Loader");

        name.append(",host=");
        name.append(context.getParent().getName());

        name.append(",context=");

        String contextName = context.getName();
        if (!contextName.startsWith("/")) {
            name.append("/");
        }
        name.append(contextName);

        return name.toString();
    }
}
