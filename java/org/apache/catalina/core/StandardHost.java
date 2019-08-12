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
     * ʵ����StandardHost���
     */
    public StandardHost() {
        super();
        pipeline.setBasic(new StandardHostValve());
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * ��������
     */
    private String[] aliases = new String[0];

    private final Object aliasesLock = new Object();

    /**
     * Host�����Ӧ�ó����Ŀ¼��
     */
    private String appBase = "webapps";
    private volatile File appBaseFile = null;

    /**
     * Host�����XML��Ŀ¼
     */
    private String xmlBase = null;

    /**
     * Host���Ĭ������·��
     */
    private volatile File hostConfigBase = null;

    /**
     * �Զ�����
     * �Ƿ�Ӧ��Tomcat����ʱHost������ڼ���µĻ���µ�WebӦ�ó������Ϊtrue����Tomcat�ᶨ�ڼ��appBase��xmlBaseĿ¼
     * ���ڸ��µ�Ӧ�ó��򴥷�WebӦ�ó�������¼���
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
     * ��־ֵָʾ������Host���ʱ�Ƿ�Ӧ�Զ�����Host�����WebӦ�ó��򡣱�־��ֵĬ��Ϊtrue��
     */
    private boolean deployOnStartup = true;


    /**
     * ���ý���Ƕ��ʽӦ�ã�λ����������XML������ /META-INF/context.xml��
     */
    private boolean deployXML = !Globals.IS_SECURITY_ENABLED;


    /**
     * ������Ƕ����Ӧ�ó���λ����������XML������ /META-INF/context.xml��
     */
    private boolean copyXML = false;


    /**
     * Host��������Pilpline����ڴ������Valveʵ����
     */
    private String errorReportValveClass =
        "org.apache.catalina.valves.ErrorReportValve";


    /**
     * �Ƿ��ѹ����Host�����Ӧ�ó����Ŀ¼war���ڼ�������
     */
    private boolean unpackWARs = true;


    /**
     * Ӧ�ó���Ҫʹ�õ���ʱĿ¼��·������ÿ��Ӧ�ó������Լ�����Ŀ¼
     * catalina_home\work\Catalina\localhost
     */
    private String workDir = null;


    /**
     * ����ʱΪappbase��xmlbase����Ŀ¼
     */
    private boolean createDirs = true;


    /**
     * ������WebӦ�ó��������������Ա����ڴ�й©��
     */
    private final Map<ClassLoader, String> childClassLoaders =
            new WeakHashMap<>();


    /**
     * ������ʽ�����Զ�����Ӧ��
     */
    private Pattern deployIgnore = null;


    /**
     * �˱�־ȷ����Ϊ�Զ�������̵�һ����
     */
    private boolean undeployOldVersions = false;

    /** true����κξ���load-on-startup> = 0��servlet�޷��Լ�������������Ϊ��ÿ���������Ķ��޷��������� **/
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
