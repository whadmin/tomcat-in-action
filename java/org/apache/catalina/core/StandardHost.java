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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.JmxEnabled;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Valve;
import org.apache.catalina.loader.WebappClassLoaderBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;

/**
 * Standard implementation of the <b>Host</b> interface.  Each
 * child container must be a Context implementation to process the
 * requests directed to a particular web application.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
public class StandardHost extends ContainerBase implements Host {

    private static final Log log = LogFactory.getLog(StandardHost.class);

    // ----------------------------------------------------------- Constructors
    /**
     * 实例化StandardHost组件
     */
    public StandardHost() {
        super();
        pipeline.setBasic(new StandardHostValve());
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 别名数组
     */
    private String[] aliases = new String[0];

    private final Object aliasesLock = new Object();

    /**
     * Host组件的应用程序根目录。
     */
    private String appBase = "webapps";
    private volatile File appBaseFile = null;

    /**
     * Host组件的XML根目录
     */
    private String xmlBase = null;

    /**
     * Host组件默认配置路径
     */
    private volatile File hostConfigBase = null;

    /**
     * 自动部署
     * 是否应在Tomcat运行时Host组件定期检查新的或更新的Web应用程序。如果为true，则Tomcat会定期检查appBase和xmlBase目录
     * 对于更新的应用程序触发Web应用程序的重新加载
     */
    private boolean autoDeploy = true;


    /**
     * 子组件Context配置实现类
     */
    private String configClass =
        "org.apache.catalina.startup.ContextConfig";


    /**
     * 子组件Context实现类
     */
    private String contextClass =
        "org.apache.catalina.core.StandardContext";


    /**
     * 标志值指示在启动Host组件时是否应自动部署Host组件的Web应用程序。标志的值默认为true。
     */
    private boolean deployOnStartup = true;


    /**
     * 禁用解析嵌入式应用（位于内上下文XML描述符 /META-INF/context.xml）
     */
    private boolean deployXML = !Globals.IS_SECURITY_ENABLED;


    /**
     * 启动想嵌入在应用程序（位于内上下文XML描述符 /META-INF/context.xml）
     */
    private boolean copyXML = false;


    /**
     * Host组件子组件Pilpline组件内处理错误Valve实现类
     */
    private String errorReportValveClass =
        "org.apache.catalina.valves.ErrorReportValve";


    /**
     * 是否解压放在Host组件的应用程序根目录war包在加载运行
     */
    private boolean unpackWARs = true;


    /**
     * 应用程序要使用的临时目录的路径名，每个应用程序都有自己的子目录
     * catalina_home\work\Catalina\localhost
     */
    private String workDir = null;


    /**
     * 启动时为appbase和xmlbase创建目录
     */
    private boolean createDirs = true;


    /**
     * 跟踪子Web应用程序的类加载器，以便检测内存泄漏。
     */
    private final Map<ClassLoader, String> childClassLoaders =
            new WeakHashMap<>();


    /**
     * 正则表达式定义自动部署应用
     */
    private Pattern deployIgnore = null;


    /**
     * 此标志确定作为自动部署过程的一部分
     */
    private boolean undeployOldVersions = false;

    /** true如果任何具有load-on-startup> = 0的servlet无法自己启动，则设置为让每个子上下文都无法启动它。 **/
    private boolean failCtxIfServletStartFails = false;


    // ------------------------------------------------------------- Properties

    @Override
    public boolean getUndeployOldVersions() {
        return undeployOldVersions;
    }


    @Override
    public void setUndeployOldVersions(boolean undeployOldVersions) {
        this.undeployOldVersions = undeployOldVersions;
    }


    @Override
    public ExecutorService getStartStopExecutor() {
        return startStopExecutor;
    }


    @Override
    public String getAppBase() {
        return (this.appBase);
    }


    @Override
    public File getAppBaseFile() {
        if (appBaseFile != null) {
            return appBaseFile;
        }

        File file = new File(getAppBase());

        if (!file.isAbsolute()) {
            file = new File(getCatalinaBase(), file.getPath());
        }

        try {
            file = file.getCanonicalFile();
        } catch (IOException ioe) {
        }
        this.appBaseFile = file;
        return file;
    }


    @Override
    public void setAppBase(String appBase) {

        if (appBase.trim().equals("")) {
            log.warn(sm.getString("standardHost.problematicAppBase", getName()));
        }
        String oldAppBase = this.appBase;
        this.appBase = appBase;
        support.firePropertyChange("appBase", oldAppBase, this.appBase);
        this.appBaseFile = null;
    }


    @Override
    public String getXmlBase() {
        return (this.xmlBase);
    }


    @Override
    public void setXmlBase(String xmlBase) {
        String oldXmlBase = this.xmlBase;
        this.xmlBase = xmlBase;
        support.firePropertyChange("xmlBase", oldXmlBase, this.xmlBase);
    }


    @Override
    public File getConfigBaseFile() {
        if (hostConfigBase != null) {
            return hostConfigBase;
        }
        String path = null;
        if (getXmlBase()!=null) {
            path = getXmlBase();
        } else {
            StringBuilder xmlDir = new StringBuilder("conf");
            Container parent = getParent();
            if (parent instanceof Engine) {
                xmlDir.append('/');
                xmlDir.append(parent.getName());
            }
            xmlDir.append('/');
            xmlDir.append(getName());
            path = xmlDir.toString();
        }
        File file = new File(path);
        if (!file.isAbsolute())
            file = new File(getCatalinaBase(), path);
        try {
            file = file.getCanonicalFile();
        } catch (IOException e) {// ignore
        }
        this.hostConfigBase = file;
        return file;
    }


    @Override
    public boolean getCreateDirs() {
        return createDirs;
    }


    @Override
    public void setCreateDirs(boolean createDirs) {
        this.createDirs = createDirs;
    }


    @Override
    public boolean getAutoDeploy() {
        return (this.autoDeploy);
    }


    @Override
    public void setAutoDeploy(boolean autoDeploy) {
        boolean oldAutoDeploy = this.autoDeploy;
        this.autoDeploy = autoDeploy;
        support.firePropertyChange("autoDeploy", oldAutoDeploy,
                                   this.autoDeploy);
    }


    @Override
    public String getConfigClass() {
        return (this.configClass);
    }


    @Override
    public void setConfigClass(String configClass) {
        String oldConfigClass = this.configClass;
        this.configClass = configClass;
        support.firePropertyChange("configClass",
                                   oldConfigClass, this.configClass);
    }


    public String getContextClass() {
        return (this.contextClass);
    }


    public void setContextClass(String contextClass) {
        String oldContextClass = this.contextClass;
        this.contextClass = contextClass;
        support.firePropertyChange("contextClass",
                                   oldContextClass, this.contextClass);
    }


    @Override
    public boolean getDeployOnStartup() {
        return (this.deployOnStartup);
    }


    @Override
    public void setDeployOnStartup(boolean deployOnStartup) {
        boolean oldDeployOnStartup = this.deployOnStartup;
        this.deployOnStartup = deployOnStartup;
        support.firePropertyChange("deployOnStartup", oldDeployOnStartup,
                                   this.deployOnStartup);
    }


    public boolean isDeployXML() {
        return deployXML;
    }


    public void setDeployXML(boolean deployXML) {
        this.deployXML = deployXML;
    }


    public boolean isCopyXML() { return this.copyXML;

    }


    public void setCopyXML(boolean copyXML) {
        this.copyXML = copyXML;
    }


    public String getErrorReportValveClass() {
        return (this.errorReportValveClass);
    }


    public void setErrorReportValveClass(String errorReportValveClass) {
        String oldErrorReportValveClassClass = this.errorReportValveClass;
        this.errorReportValveClass = errorReportValveClass;
        support.firePropertyChange("errorReportValveClass",
                                   oldErrorReportValveClassClass,
                                   this.errorReportValveClass);
    }

    @Override
    public String getName() {
        return (name);
    }


    @Override
    public void setName(String name) {
        if (name == null)
            throw new IllegalArgumentException
                (sm.getString("standardHost.nullName"));

        name = name.toLowerCase(Locale.ENGLISH);      // Internally all names are lower case
        String oldName = this.name;
        this.name = name;
        support.firePropertyChange("name", oldName, this.name);
    }

    public boolean isUnpackWARs() {
        return (unpackWARs);
    }


    public void setUnpackWARs(boolean unpackWARs) {
        this.unpackWARs = unpackWARs;
    }


    public String getWorkDir() {
        return (workDir);
    }



    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }



    @Override
    public String getDeployIgnore() {
        if (deployIgnore == null) {
            return null;
        }
        return this.deployIgnore.toString();
    }


    @Override
    public Pattern getDeployIgnorePattern() {
        return this.deployIgnore;
    }



    @Override
    public void setDeployIgnore(String deployIgnore) {
        String oldDeployIgnore;
        if (this.deployIgnore == null) {
            oldDeployIgnore = null;
        } else {
            oldDeployIgnore = this.deployIgnore.toString();
        }
        if (deployIgnore == null) {
            this.deployIgnore = null;
        } else {
            this.deployIgnore = Pattern.compile(deployIgnore);
        }
        support.firePropertyChange("deployIgnore",
                                   oldDeployIgnore,
                                   deployIgnore);
    }



    public boolean isFailCtxIfServletStartFails() {
        return failCtxIfServletStartFails;
    }



    public void setFailCtxIfServletStartFails(
            boolean failCtxIfServletStartFails) {
        boolean oldFailCtxIfServletStartFails = this.failCtxIfServletStartFails;
        this.failCtxIfServletStartFails = failCtxIfServletStartFails;
        support.firePropertyChange("failCtxIfServletStartFails",
                oldFailCtxIfServletStartFails,
                failCtxIfServletStartFails);
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 给host组件添加别名
     */
    @Override
    public void addAlias(String alias) {
        alias = alias.toLowerCase(Locale.ENGLISH);
        synchronized (aliasesLock) {
            // Skip duplicate aliases
            for (int i = 0; i < aliases.length; i++) {
                if (aliases[i].equals(alias))
                    return;
            }
            // Add this alias to the list
            String newAliases[] = new String[aliases.length + 1];
            for (int i = 0; i < aliases.length; i++)
                newAliases[i] = aliases[i];
            newAliases[aliases.length] = alias;
            aliases = newAliases;
        }
        /** 触发属性变更 **/
        fireContainerEvent(ADD_ALIAS_EVENT, alias);
    }

    /**
     * 返回host组件所有别名
     */
    @Override
    public String[] findAliases() {
        synchronized (aliasesLock) {
            return (this.aliases);
        }
    }


    /**
     * 删除host组件别名
     */
    @Override
    public void removeAlias(String alias) {

        alias = alias.toLowerCase(Locale.ENGLISH);

        synchronized (aliasesLock) {

            int n = -1;
            for (int i = 0; i < aliases.length; i++) {
                if (aliases[i].equals(alias)) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            int j = 0;
            String results[] = new String[aliases.length - 1];
            for (int i = 0; i < aliases.length; i++) {
                if (i != n)
                    results[j++] = aliases[i];
            }
            aliases = results;
        }

        // Inform interested listeners
        fireContainerEvent(REMOVE_ALIAS_EVENT, alias);
    }


    /**
     * 添加一个子容器
     */
    @Override
    public void addChild(Container child) {
        /** 给子容器组件添加MemoryLeakTrackingListener监听器 **/
        child.addLifecycleListener(new MemoryLeakTrackingListener());

        if (!(child instanceof Context))
            throw new IllegalArgumentException
                (sm.getString("standardHost.notContext"));
        super.addChild(child);
    }


    /**
     * 处理AFTER_START_EVENT生命周期事件，设置childClassLoaders属性
     */
    private class MemoryLeakTrackingListener implements LifecycleListener {
        @Override
        public void lifecycleEvent(LifecycleEvent event) {
            if (event.getType().equals(Lifecycle.AFTER_START_EVENT)) {
                if (event.getSource() instanceof Context) {
                    Context context = ((Context) event.getSource());
                    childClassLoaders.put(context.getLoader().getClassLoader(),
                            context.getServletContext().getContextPath());
                }
            }
        }
    }


    /**
     * 尝试识别有类加载器内存泄漏的上下文。
     * 这通常在上下文重新加载时触发。注意：此方法尝试
     * 强制完全收集垃圾。这应该用于极端
     * 生产系统注意事项。
     */
    public String[] findReloadedContextMemoryLeaks() {
        System.gc();
        List<String> result = new ArrayList<>();
        for (Map.Entry<ClassLoader, String> entry :
                childClassLoaders.entrySet()) {
            ClassLoader cl = entry.getKey();
            if (cl instanceof WebappClassLoaderBase) {
                if (!((WebappClassLoaderBase) cl).getState().isAvailable()) {
                    result.add(entry.getValue());
                }
            }
        }
        return result.toArray(new String[result.size()]);
    }




    /**
     * 启动组件模板实现
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        /** 将errorReportValveClass 类对象添加到Host组件Pipeline组件内 **/
        String errorValve = getErrorReportValveClass();
        if ((errorValve != null) && (!errorValve.equals(""))) {
            try {
                boolean found = false;
                Valve[] valves = getPipeline().getValves();
                for (Valve valve : valves) {
                    if (errorValve.equals(valve.getClass().getName())) {
                        found = true;
                        break;
                    }
                }
                if(!found) {
                    Valve valve =
                        (Valve) Class.forName(errorValve).getConstructor().newInstance();
                    getPipeline().addValve(valve);
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.error(sm.getString(
                        "standardHost.invalidErrorReportValveClass",
                        errorValve), t);
            }
        }
        super.startInternal();
    }


    // -------------------- JMX  --------------------
     public String[] getValveNames() throws Exception {
         Valve [] valves = this.getPipeline().getValves();
         String [] mbeanNames = new String[valves.length];
         for (int i = 0; i < valves.length; i++) {
             if (valves[i] instanceof JmxEnabled) {
                 ObjectName oname = ((JmxEnabled) valves[i]).getObjectName();
                 if (oname != null) {
                     mbeanNames[i] = oname.toString();
                 }
             }
         }

         return mbeanNames;

     }

    public String[] getAliases() {
        synchronized (aliasesLock) {
            return aliases;
        }
    }

    @Override
    protected String getObjectNameKeyProperties() {

        StringBuilder keyProperties = new StringBuilder("type=Host");
        keyProperties.append(getMBeanKeyProperties());

        return keyProperties.toString();
    }

}
