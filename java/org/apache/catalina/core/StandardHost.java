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
     * ʵ����StandardHost���
     */
    public StandardHost() {
        super();
        pipeline.setBasic(new StandardHostValve());
    }

    /**
     * Host�������
     */
    private String[] aliases = new String[0];

    /**
     * ����Host�������ͬ��������
     */
    private final Object aliasesLock = new Object();

    /**
     * host������webӦ�ó���,��̬��Դ�ļ����·�������·������������·��������·��Ϊ$catalinaHome/appBase
     */
    private String appBase = "webapps";

    /**
     * host�������webӦ�ó���,��̬��Դ�ļ��ĸ���Ŀ¼�ļ�
     * ���appBaseΪ���·����appBaseFile= new File($catalinaHome/appBase)
     * ���appBaseΪ���·����appBaseFile= new File(appBase)
     */
    private volatile File appBaseFile = null;

    /**
     * host��������ļ����·��������·��Ϊ$catalinaBase/xmlBase
     * ��̬��Դ�ļ�����ͨ������aa.xml������host�����ļ�Ŀ¼�£�����host
     */
    private String xmlBase = null;

    /**
     * host��������ļ����󣬶�Ӧ·��Ϊ$catalinaBase/xmlBase
     */
    private volatile File hostConfigBase = null;

    /**
     * �Ƿ�֧���Ȳ���
     * ���Ϊtrue����������Host�ᶨ�ڼ��appBase��xmlBaseĿ¼����WebӦ�ó����̬��Դ���������������ᴥ����Ӧcontext��������¼���
     */
    private boolean autoDeploy = true;


    /**
     * �����Context����ʵ����
     */
    private String configClass =
        "org.apache.catalina.startup.ContextConfig";


    /**
     * �����Contextʵ����
     */
    private String contextClass =
        "org.apache.catalina.core.StandardContext";


    /**
     * ��ʶ������Host���ʱ�Ƿ�Ӧ�Զ�����Host�����WebӦ�ó��򡣱�־��ֵĬ��Ϊtrue��
     */
    private boolean deployOnStartup = true;


    /**
     * �Ƿ�Ҫ��ֹӦ�ó����ж���/META-INF/context.xml
     */
    private boolean deployXML = !Globals.IS_SECURITY_ENABLED;


    /**
     * �����Ӧ�ó����ж�����/META-INF/context.xml���Ƿ�Ҫ������$catalinaBase/xmlBaseĿ¼��
     */
    private boolean copyXML = false;


    /**
     * Host��������Pilpline����ڴ����쳣Valveʵ����
     */
    private String errorReportValveClass =
        "org.apache.catalina.valves.ErrorReportValve";


    /**
     * �Ƿ��ѹwar����Ӧ�ó�����ִ�У�Ĭ��Ϊtrue
     */
    private boolean unpackWARs = true;


    /**
     * ��ʶhost�����������ʱĿ¼
     * $catalinaBase/workDir
     */
    private String workDir = null;


    /**
     * ��ʶ�Ƿ���Ҫ������ʱ����appbase��xmlbaseĿ¼
     */
    private boolean createDirs = true;


    /**
     * ʹ��WeakHashMap�������������context�󶨵�WebAppClassLoader,�Լ���Ӧ���������context������
     */
    private final Map<ClassLoader, String> childClassLoaders =
            new WeakHashMap<>();


    /**
     * ��ʾ������ʽ�����������Զ�������ЩӦ�ó���
     */
    private Pattern deployIgnore = null;


    /**
     * �Ƿ������ڿ���ȡ������ľɰ汾��Ӧ�ó���
     */
    private boolean undeployOldVersions = false;


    /** ���������tomcat�м���serveltʱ�����쳣�Ƿ���� Ĭ�ϲ�����**/
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
     * ��host�����ӱ���
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
        /** �������Ա�� **/
        fireContainerEvent(ADD_ALIAS_EVENT, alias);
    }

    /**
     * ����host������б���
     */
    @Override
    public String[] findAliases() {
        synchronized (aliasesLock) {
            return (this.aliases);
        }
    }


    /**
     * ɾ��host�������
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
     * ���һ��������
     */
    @Override
    public void addChild(Container child) {
        /** ��������������MemoryLeakTrackingListener������ **/
        child.addLifecycleListener(new MemoryLeakTrackingListener());

        if (!(child instanceof Context))
            throw new IllegalArgumentException
                (sm.getString("standardHost.notContext"));
        super.addChild(child);
    }


    /**
     * ����AFTER_START_EVENT���������¼�������childClassLoaders����
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
     * ����ʶ������������ڴ�й©�������ġ�
     * ��ͨ�������������¼���ʱ������ע�⣺�˷�������
     * ǿ����ȫ�ռ���������Ӧ�����ڼ���
     * ����ϵͳע�����
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
     * �������ģ��ʵ��
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        /** ��errorReportValveClass �������ӵ�Host���Pipeline����� **/
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
