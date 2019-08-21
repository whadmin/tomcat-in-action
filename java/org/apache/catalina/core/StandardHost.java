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


public class StandardHost extends ContainerBase implements Host {

    private static final Log log = LogFactory.getLog(StandardHost.class);

    /**
     * 实例化StandardHost组件
     */
    public StandardHost() {
        super();
        pipeline.setBasic(new StandardHostValve());
    }

    /**
     * Host组件别名
     */
    private String[] aliases = new String[0];

    /**
     * 处理Host组件别名同步锁对象
     */
    private final Object aliasesLock = new Object();

    /**
     * host组件存放web应用程序,或静态资源文件相对路径或绝对路径，如果是相对路径则完整路径为$catalinaHome/appBase
     */
    private String appBase = "webapps";

    /**
     * host组件部署web应用程序,或静态资源文件的根的目录文件
     * 如果appBase为相对路径，appBaseFile= new File($catalinaHome/appBase)
     * 如果appBase为相对路径，appBaseFile= new File(appBase)
     */
    private volatile File appBaseFile = null;

    /**
     * host组件配置文件相对路径，完整路径为$catalinaBase/xmlBase
     * 静态资源文件可以通过配置aa.xml，放入host配置文件目录下，部署到host
     */
    private String xmlBase = null;

    /**
     * host组件配置文件对象，对应路径为$catalinaBase/xmlBase
     */
    private volatile File hostConfigBase = null;

    /**
     * 是否支持热部署
     * 如果为true，虚拟主机Host会定期检查appBase和xmlBase目录下新Web应用程序或静态资源，如果发生更新则会触发对应context组件的重新加载
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
     * 标识在启动Host组件时是否应自动部署Host组件的Web应用程序。标志的值默认为true。
     */
    private boolean deployOnStartup = true;


    /**
     * 是否要禁止应用程序中定义/META-INF/context.xml
     */
    private boolean deployXML = !Globals.IS_SECURITY_ENABLED;


    /**
     * 如果在应用程序中定义了/META-INF/context.xml，是否要拷贝到$catalinaBase/xmlBase目录下
     */
    private boolean copyXML = false;


    /**
     * Host组件子组件Pilpline组件内处理异常Valve实现类
     */
    private String errorReportValveClass =
        "org.apache.catalina.valves.ErrorReportValve";


    /**
     * 是否解压war包种应用程序在执行，默认为true
     */
    private boolean unpackWARs = true;


    /**
     * 标识host组件工作的临时目录
     * $catalinaBase/workDir
     */
    private String workDir = null;


    /**
     * 标识是否需要在启动时创建appbase和xmlbase目录
     */
    private boolean createDirs = true;


    /**
     * 使用WeakHashMap保存子容器组件context绑定的WebAppClassLoader,以及对应子容器组件context的名称
     */
    private final Map<ClassLoader, String> childClassLoaders =
            new WeakHashMap<>();


    /**
     * 表示正则表达式，用来定义自动部署哪些应用程序
     */
    private Pattern deployIgnore = null;


    /**
     * 是否检查现在可以取消部署的旧版本的应用程序
     */
    private boolean undeployOldVersions = false;


    /** 如果在启动tomcat中加载servelt时发生异常是否忽略 默认不忽略**/
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

        name = name.toLowerCase(Locale.ENGLISH);
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
