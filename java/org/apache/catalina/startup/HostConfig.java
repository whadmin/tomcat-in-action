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
     * �����ӡ��־ģ�����
     */
    protected static final StringManager sm = StringManager.getManager(HostConfig.class);

    /**
     * �ļ��޸�ʱ��ķֱ���
     */
    protected static final long FILE_MODIFICATION_RESOLUTION_MS = 1000;


    /**
     * ���Contextʵ����
     */
    protected String contextClass = "org.apache.catalina.core.StandardContext";


    /**
     * ��������host���
     */
    protected Host host = null;


    /**
     * ��ǰ����HostConfig ע�ᵽJMX ObjectName
     */
    protected ObjectName oname = null;


    /**
     * �Ƿ�Ҫ��ֹӦ�ó����ж���/META-INF/context.xml
     */
    protected boolean deployXML = false;


    /**
     * �����Ӧ�ó����ж�����/META-INF/context.xml���Ƿ�Ҫ������$catalinaBase/xmlBaseĿ¼��
     */
    protected boolean copyXML = false;


    /**
     * �Ƿ��ѹwar����Ӧ�ó�����ִ�У�Ĭ��Ϊtrue
     */
    protected boolean unpackWARs = false;


    /**
     * deployed Map�洢�Ѿ�����context�����ġ�key�д洢context�����ƣ�value�д洢DeployedApplication�����
     * DeployedApplication��������������context�����Ϣ
     */
    protected final Map<String, DeployedApplication> deployed =
            new ConcurrentHashMap<>();


    /**
     * �洢���������ҽ�ֹ������/ж��/���²��𣩵�Context�б�
     */
    protected final ArrayList<String> serviced = new ArrayList<>();


    /**
     * digester���󣬸���������context��ǩ����
     */
    protected Digester digester = createDigester(contextClass);


    /**
     * digesterʹ��ʱͬ��������
     */
    private final Object digesterLock = new Object();

    /**
     * �洢�ú��Բ���Ӧ�ó���war�ļ�
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
                /** ���´���digester **/
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
     * ����host�����������ʱ��
     */
    @Override
    public void lifecycleEvent(LifecycleEvent event) {

        /**  ��ȡhost���������������HostConfig��Ӧ������  **/
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

        /** ������Ӧ��host������������¼� **/
        if (event.getType().equals(Lifecycle.PERIODIC_EVENT)) {
            /**
             * ����host������������񴥷��¼���
             * ��ȡhost��������в���context��������ÿһ��context�������Ҫ������ԴĿ¼�Ƿ��������
             * �����������򴥷���context���¼��ػ�����
             **/
            check();
        } else if (event.getType().equals(Lifecycle.BEFORE_START_EVENT)) {
            /**
             * ����host�������ǰ�����¼���
             * ����host�������appbase��xmlbase·����Ӧ��Ŀ¼�ļ�
             **/
            beforeStart();
        } else if (event.getType().equals(Lifecycle.START_EVENT)) {
            /**
             * ����host��������¼�������hostConfig���.
             * 1 ��HostConfig����ע�ᵽJmx bean��
             * 2 ���Host���������������ʱ�Զ�����appBase��xmlBaseĿ¼��Ӧ�ó����̬��Դ
             * ��ɨ��appBase��xmlBaseĿ¼��Ӧ�ó����̬��Դ������context�������ӵ�host�����������*/
            start();
        } else if (event.getType().equals(Lifecycle.STOP_EVENT)) {
            /**
             * ����host���ֹͣ�¼���ֹͣhostConfig���.
             * ��HostConfig�����Jmx bean��ע��
             */
            stop();
        }
    }


    /**
     * ��server�б������context����
     */
    public synchronized void addServiced(String name) {
        serviced.add(name);
    }


    /**
     * server�б����Ƿ����ָ��context����
     */
    public synchronized boolean isServiced(String name) {
        return (serviced.contains(name));
    }


    /**
     * ��server�б���ɾ��ָ��context����
     */
    public synchronized void removeServiced(String name) {
        serviced.remove(name);
    }


    /**
     * ��ȡָ��context�����ʱ��
     */
    public long getDeploymentTime(String name) {
        DeployedApplication app = deployed.get(name);
        if (app == null) {
            return 0L;
        }
        return app.timestamp;
    }


    /**
     * ָ��context�Ƿ��Ѳ���
     */
    public boolean isDeployed(String name) {
        return deployed.containsKey(name);
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * ����Digester���󣬸���������context��ǩ����
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
     * ��ȡָ��·���ļ�����new File(CatalinaBase/path)
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
     * ��ȡ�����ļ�����·��
     */
    public String getConfigBaseName() {
        return host.getConfigBaseFile().getAbsolutePath();
    }


    /**
     * ɨ��appBase��xmlBaseĿ¼��Ӧ�ó����̬��Դ������context�������ӵ�host�����������
     */
    protected void deployApps() {

        /** ��ȡhost�������webӦ�ó���,��̬��Դ�ļ��ĸ���Ŀ¼�ļ�  **/
        File appBase = host.getAppBaseFile();
        /** ��ȡhost���ø���Ŀ¼�ļ� **/
        File configBase = host.getConfigBaseFile();
        /** ��ȡhost�������deployIgnore������ʽ����appBase���ļ�**/
        String[] filteredAppPaths = filterAppPaths(appBase.list());

        /** ��xmlBaseĿ¼xml�����ļ���ʾ��̬��Դ�ļ�����Ϊcontext�������,��ӣ����𣩵�host�����������������**/
        deployDescriptors(configBase, configBase.list());
        /** ��appBaseĿ¼webӦ�ó���war������Ϊcontext�������,��ӣ����𣩵�host����������������� **/
        deployWARs(appBase, filteredAppPaths);
        /** ��appBaseĿ¼��̬��Դ�ļ�����Ϊcontext�������,��ӣ����𣩵�host����������������� **/
        deployDirectories(appBase, filteredAppPaths);
    }

    /**
     * ����$catalinaBase/xmlBaseĿ¼��xml�����ļ�����ÿ��xml�����ļ���Ӧ��̬�ļ���������
     * ��װΪһ���������DeployDescriptor�������̳߳ش���
     */
    protected void deployDescriptors(File configBase, String[] files) {

        if (files == null)
            return;

        /** ��ȡ�̳߳� **/
        ExecutorService es = host.getStartStopExecutor();
        List<Future<?>> results = new ArrayList<>();

        /**
         * ����$catalinaBase/xmlBaseĿ¼��xml�ļ�����ÿ��xml�ļ���������
         * ��װΪһ���������DeployDescriptor�������̳߳ش���
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

        /** �ȴ��̳߳��첽������ **/
        for (Future<?> result : results) {
            try {
                /** �����ȴ��첽������ **/
                result.get();
            } catch (Exception e) {
                log.error(sm.getString(
                        "hostConfig.deployDescriptor.threaded.error"), e);
            }
        }
    }


    /**
     * ����$appBaseĿ¼��Ӧ�ó���war���ļ�����ÿ��Ӧ�ó���war���ļ���������
     * ��װΪһ���������DeployWar�������̳߳ش���
     */
    protected void deployWARs(File appBase, String[] files) {

        if (files == null)
            return;

        /** ��ȡ�̳߳� **/
        ExecutorService es = host.getStartStopExecutor();
        List<Future<?>> results = new ArrayList<>();

        /** ����appBaseĿ¼���ļ� **/
        for (int i = 0; i < files.length; i++) {

            /**  ����META-INF�ļ�  **/
            if (files[i].equalsIgnoreCase("META-INF"))
                continue;

            /**  ����WEB-INF�ļ�  **/
            if (files[i].equalsIgnoreCase("WEB-INF"))
                continue;

            /** ʵ����appBaseĿ¼��war�ļ�����**/
            File war = new File(appBase, files[i]);

            /**
             * ���war�ļ����ڣ�����չ����Ϊ.war���Ҳ�������invalidWars��
             * invalidWars�洢�ú��Բ���Ӧ�ó���war�ļ�
             * **/
            if (files[i].toLowerCase(Locale.ENGLISH).endsWith(".war") &&
                    war.isFile() && !invalidWars.contains(files[i]) ) {

                /**
                 * ��war��������Ϊcontext��������ʵ����ContextName����
                 * ContextName������ʾcontext���ƣ�
                 *
                 * name ��ʾ��������,������appBaseĿ¼��Ӧ�ó���name=war���ļ���=path##version
                 * baseName,��ʾ����name���������ַ����������
                 * path ��ʾcontext�����·������name�н�����ȡ
                 * version ͬһ��war��������Բ������汾��host,ֻ��Ҫ�޸�����Ϊtest##3.war ##��3��ʾ�汾��
                 *
                 * **/
                ContextName cn = new ContextName(files[i], true);

                /**
                 * ���˵�������serviced�б���war��Ӧ�ó���
                 * serviced�б��д洢���������ҽ�ֹ������/ж��/���²��𣩵�Context
                 * **/
                if (isServiced(cn.getName())) {
                    continue;
                }
                /**
                 * ���˵��Ѿ�����host��war��Ӧ�ó����������������
                 * ������war��Ӧ�ó����ӦDeployedApplication����loggedDirWarning����
                 *
                 * loggedDirWarning���Ա�ʾ�Ƿ񲻽�ѹwar��ֱ������war���г���
                 * **/
                if (deploymentExists(cn.getName())) {

                    DeployedApplication app = deployed.get(cn.getName());

                    /** ��ȡ�Ƿ�ֱ������war���г��� **/
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
                    /** ���� **/
                    continue;
                }

                /** ���˵������Ϲ���context �������� **/
                if (!validateContextPath(appBase, cn.getBaseName())) {
                    log.error(sm.getString(
                            "hostConfig.illegalWarName", files[i]));
                    invalidWars.add(files[i]);
                    continue;
                }

                /** ����$appBaseĿ¼��Ӧ�ó���war���ļ�����ÿ��Ӧ�ó���war���ļ���������
                 * ��װΪһ���������DeployWar�������̳߳ش��� **/
                results.add(es.submit(new DeployWar(this, cn, war)));
            }
        }
        /** �ȴ��̳߳��첽������ **/
        for (Future<?> result : results) {
            try {
                /** �����ȴ��첽������ **/
                result.get();
            } catch (Exception e) {
                log.error(sm.getString(
                        "hostConfig.deployWar.threaded.error"), e);
            }
        }
    }


    /**
     * ����$appBaseĿ¼�¾�̬��Դ�ļ�������̬��Դ�ļ���������
     * ��װΪһ���������DeployDirectory�������̳߳ش���
     */
    protected void deployDirectories(File appBase, String[] files) {

        if (files == null)
            return;

        ExecutorService es = host.getStartStopExecutor();
        List<Future<?>> results = new ArrayList<>();
        /** ����$appBaseĿ¼�¾�̬��Դ�ļ� **/
        for (int i = 0; i < files.length; i++) {
            /**  ����META-INF�ļ�  **/
            if (files[i].equalsIgnoreCase("META-INF"))
                continue;

            /**  ����WEB-INF�ļ�  **/
            if (files[i].equalsIgnoreCase("WEB-INF"))
                continue;

            /** ʵ����appBaseĿ¼�¾�̬��ԴĿ¼�ļ�**/
            File dir = new File(appBase, files[i]);
            /** �ж��Ƿ���Ŀ¼ **/
            if (dir.isDirectory()) {
                ContextName cn = new ContextName(files[i], false);

                /**
                 * ���˵�������serviced�б��о�̬��Դ�ļ�
                 * ���˵��Ѿ�����host�о�̬��Դ�ļ�
                 * **/
                if (isServiced(cn.getName()) || deploymentExists(cn.getName()))
                    continue;

                /** ����̬��Դ�ļ���������
                 * ��װΪһ���������DeployDirectory�������̳߳ش��� **/
                results.add(es.submit(new DeployDirectory(this, cn, dir)));
            }
        }
         /** �ȴ��̳߳��첽������ **/
        for (Future<?> result : results) {
            try {
                /** �����ȴ��첽������ **/
                result.get();
            } catch (Exception e) {
                log.error(sm.getString(
                        "hostConfig.deployDir.threaded.error"), e);
            }
        }
    }


    /**
     * ��ָ��Ӧ�ó����̬��Դ����Host
     */
    protected void deployApps(String name) {

        /** ��ȡhost�������webӦ�ó���,��̬��Դ�ļ��ĸ���Ŀ¼�ļ�  **/
        File appBase = host.getAppBaseFile();
        /** ��ȡhost���ø���Ŀ¼�ļ� **/
        File configBase = host.getConfigBaseFile();
        /** ʵ����ContextName���� **/
        ContextName cn = new ContextName(name, false);
        /** ��ȡcontext ��������**/
        String baseName = cn.getBaseName();

        /** ���Ӧ�ó����̬��Դ�Ѿ�����host�з��� **/
        if (deploymentExists(cn.getName())) {
            return;
        }

        /** �ж�$catalinaBase/xmlBaseĿ¼�Ƿ����baseName + ".xml"�ļ� **/
        File xml = new File(configBase, baseName + ".xml");
        if (xml.exists()) {
            /** ��$catalinaBase/xmlBase/baseName.xml�ļ���ʾ�ľ�̬��Դ����host **/
            deployDescriptor(cn, xml);
            return;
        }
        /** �ж�appBaseĿ¼�Ƿ����baseName + ".war"�ļ� **/
        File war = new File(appBase, baseName + ".war");
        if (war.exists()) {
            /** ��appBase/baseName.war�ļ���ʾ��Ӧ�ó�����host **/
            deployWAR(cn, war);
            return;
        }
        /** �ж�appBaseĿ¼�Ƿ����appBase + baseNameĿ¼�ļ� **/
        File dir = new File(appBase, baseName);
        if (dir.exists())
            /** ��appBase/baseName�ļ�Ŀ¼ʾ�ľ�̬��Դ����host **/
            deployDirectory(cn, dir);
    }

    /**
     * ��$catalinaBase/xmlBaseĿ¼��xml�����ļ���ʾ�ľ�̬��Դ����host
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
        /** �Ƿ�����ⲿwar�� **/
        boolean isExternalWar = false;
        /** �Ƿ�����ⲿ��Դ **/
        boolean isExternal = false;
        /** �����ĵ���·��**/
        File expandedDocBase = null;


        try (FileInputStream fis = new FileInputStream(contextXml)) {

            /** ʹ��digester����xmlʵ��context��� **/
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

            /** ʵ����contextConfig������ӵ�context����������ڼ������б��� **/
            Class<?> clazz = Class.forName(host.getConfigClass());
            LifecycleListener listener = (LifecycleListener) clazz.getConstructor().newInstance();
            context.addLifecycleListener(listener);

            /** ����context�����ļ�·�� **/
            context.setConfigFile(contextXml.toURI().toURL());
            /** ����contextȫ���� **/
            context.setName(cn.getName());
            /** ����context��·�� **/
            context.setPath(cn.getPath());
            /** ����context�汾�� **/
            context.setWebappVersion(cn.getVersion());
            /** ��ȡcontext�ĵ��ľ���·���������·�������ж��Ƿ���� **/
            if (context.getDocBase() != null) {
                /** ��ȡcontext�ĵ��ļ����� **/
                File docBase = new File(context.getDocBase());
                if (!docBase.isAbsolute()) {
                    docBase = new File(host.getAppBaseFile(), context.getDocBase());
                }
                /** �ж�context�ĵ������Ƿ񱣴���appBaseĿ¼�� **/
                if (!docBase.getCanonicalPath().startsWith(
                        host.getAppBaseFile().getAbsolutePath() + File.separator)) {
                    isExternal = true;
                    /**
                     * ��context.xml�ļ���ӵ�deployedApp
                     * deployedApp.redeployResources��������Ͳ���context������ⲿ��Դ������Ϊ�ⲿ��Դ����Ϊ��Դ���������AppBaseĿ¼��
                     * **/
                    deployedApp.redeployResources.put(
                            contextXml.getAbsolutePath(),
                            Long.valueOf(contextXml.lastModified()));
                    /**
                     * ��context�ĵ�������ӵ�deployedApp
                     * deployedApp.redeployResources��������Ͳ���context������ⲿ��Դ������Ϊ�ⲿ��Դ����Ϊ��Դ���������AppBaseĿ¼��
                     * **/
                    deployedApp.redeployResources.put(docBase.getAbsolutePath(),
                            Long.valueOf(docBase.lastModified()));

                    /** �ж��ĵ��ļ���չ�����Ƿ�Ϊwar **/
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
            /** ����ʵ����context�����ӵ�host,��ӹ����л�����context **/
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
     * ��appBaseĿ¼��war��Ӧ�ó�����host
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
     * ��appBaseĿ¼�¾�̬��Դ�ļ�����host
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
     * ���contextName�Ƿ��Ѿ�����host��
     */
    protected boolean deploymentExists(String contextName) {
        return (deployed.containsKey(contextName) ||
                (host.findChild(contextName) != null));
    }


    /**
     * ��Context������Դ��ӵ���ӦDeployedApplication����reloadResources������
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
     * ��CATALINA_BASE/context.xml.default��CATALINA_BASE/conf/context.xml�ļ���Ϊ�ⲿ��Դ��ӵ�
     * context�������DeployedApplication.redeployResources������
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
     * ���context����Դ�Ƿ�����������������������²�������¼���context��
     */
    protected synchronized void checkResources(DeployedApplication app,
            boolean skipFileModificationResolutionCheck) {

        /** ��ȡcontext�ⲿ��Դ·�� **/
        String[] resources =
            app.redeployResources.keySet().toArray(new String[0]);

        long currentTimeWithResolutionOffset =
                System.currentTimeMillis() - FILE_MODIFICATION_RESOLUTION_MS;

        /** ����context�ⲿ��Դ·��������ⲿ��Դ�ļ���������ж�ص�ǰcontext**/
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

        /** ��ȡcontext��Ҫ���ӵ���Դ���� **/
        resources = app.reloadResources.keySet().toArray(new String[0]);
        boolean update = false;

        /** ����context��Ҫ���ӵ���Դ���� **/
        for (int i = 0; i < resources.length; i++) {
            File resource = new File(resources[i]);
            if (log.isDebugEnabled()) {
                log.debug("Checking context[" + app.name + "] reload resource " + resource);
            }
            long lastModified = app.reloadResources.get(resources[i]).longValue();
            //�μ�bug 57765
            /** ����Ƿ�����Դ�Ƿ��и��� **/
            if ((resource.lastModified() != lastModified &&
                    (!host.getAutoDeploy() ||
                            resource.lastModified() < currentTimeWithResolutionOffset ||
                            skipFileModificationResolutionCheck)) ||
                    update) {
                if (!update) {
                    /**���¼���context **/
                    reload(app, null, null);
                    update = true;
                }
                /**  �������¼��ص�context�����¶�ӦDeployedApplication����reloadResources���ԣ�����context�����¼� **/
                app.reloadResources.put(resources[i],
                        Long.valueOf(resource.lastModified()));
            }
            app.timestamp = System.currentTimeMillis();
        }
    }


    /**
     * ���¼���ָ��context
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
     * ж��ָ��context
     */
    private void undeploy(DeployedApplication app) {
        if (log.isInfoEnabled())
            log.info(sm.getString("hostConfig.undeploy", app.name));

        /** ��context��������ɾ�� **/
        Container context = host.findChild(app.name);
        try {
            host.removeChild(context);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.warn(sm.getString
                     ("hostConfig.context.remove", app.name), t);
        }
        /** ���Ѿ�����context������Map��ɾ��  **/
        deployed.remove(app.name);
    }


    /**
     * ɾ��context�������ⲿ��Դ���ͼ�����Դ
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
     * �жϵ�ǰcontext��ָ������Դ�ܹ�ɾ��
     *  -λ��AppBase�е��κ���Դ
     *  -λ���������ݿ��µ��κβ���������
     *  -������һ���AppBase��ConfigBase�еķ�������
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
     * ����host�������appbase��xmlbase·����Ӧ��Ŀ¼�ļ�
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
     * ����hostConfig���.
     *
     * ���Host���������������ʱ�Զ�����appBase��xmlBaseĿ¼��Ӧ�ó����̬��Դ
     * ��ɨ��appBase��xmlBaseĿ¼��Ӧ�ó����̬��Դ������context�������ӵ�host�����������
     */
    public void start() {

        if (log.isDebugEnabled())
            log.debug(sm.getString("hostConfig.start"));

        try {
            /** ��HostConfig����ע�ᵽJMX Bean�� **/
            ObjectName hostON = host.getObjectName();
            oname = new ObjectName
                (hostON.getDomain() + ":type=Deployer,host=" + host.getName());
            Registry.getRegistry(null, null).registerComponent
                (this, oname, this.getClass().getName());
        } catch (Exception e) {
            log.error(sm.getString("hostConfig.jmx.register", oname), e);
        }

        /**
         * ���Host���appBase·���µ��ļ�����Ŀ¼s
         * ��ر�����Host���ʱ�Զ�����WebӦ�ó���ͬʱ�ر��Ȳ���
         * **/
        if (!host.getAppBaseFile().isDirectory()) {
            log.error(sm.getString("hostConfig.appBase", host.getName(),
                    host.getAppBaseFile().getPath()));
            host.setDeployOnStartup(false);
            host.setAutoDeploy(false);
        }

        /**
         * ���Host���������������ʱ�Զ�����appBase��xmlBaseĿ¼��Ӧ�ó����̬��Դ
         * �����deployApps()ɨ��appBase��xmlBaseĿ¼��Ӧ�ó����̬��Դ������context�������ӵ�host�����������
         **/
        if (host.getDeployOnStartup())
            deployApps();

    }


    /**
     * ��HostConfig�����Jmx bean��ע��
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
     * ��ȡhost��������в���context��������ÿһ��context�������Ҫ������ԴĿ¼�Ƿ��������
     * �����������򴥷���context���¼��ػ�����
     */
    protected void check() {

        if (host.getAutoDeploy()) {
            /**
             * ��ȡ����host������context��ӦDeployedApplication���󼯺�
             * DeployedApplication������������context������Ϣ
             **/
            DeployedApplication[] apps =
                deployed.values().toArray(new DeployedApplication[0]);
            for (int i = 0; i < apps.length; i++) {
                /** ���˵���ֹ����context **/
                if (!isServiced(apps[i].name))
                    /** ���context����Դ�Ƿ�����������������������²�������¼���context�� **/
                    checkResources(apps[i], false);
            }

            /** ���context���ڶ���汾�Ƿ�Ҫж�ؾɰ汾context **/
            if (host.getUndeployOldVersions()) {
                checkUndeploy();
            }

            /** ɨ��appBase��xmlBaseĿ¼��Ӧ�ó����̬��Դ������context�������ӵ�host����������� **/
            deployApps();
        }
    }


    /**
     * ���ָ��context�������Ҫ������ԴĿ¼�Ƿ��������
     * �����������򴥷���context���¼��ػ�����
     */
    public void check(String name) {
        DeployedApplication app = deployed.get(name);
        if (app != null) {
            /** ���context����Դ�Ƿ�����������������������²�������¼���context�� **/
            checkResources(app, true);
        }
        /** ɨ��appBase��xmlBaseĿ¼��Ӧ�ó����̬��Դ������context�������ӵ�host����������� **/
        deployApps(name);
    }

    /**
     * ���ʹ�ò��в���ľɰ汾��Ӧ�ó��򣬽���ж��
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
     * ���context
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
     * ɾ��context
     */
    public void unmanageApp(String contextName) {
        if(isServiced(contextName)) {
            deployed.remove(contextName);
            host.removeChild(host.findChild(contextName));
        }
    }

    /**
     * У��contextPath�Ƿ���Ϲ���
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
     * ʹ��host�������deployIgnore������ʽ����tomcat���webӦ�ó����Ŀ¼���ļ�
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
     * ��������context������Ϣ
     */
    protected static class DeployedApplication {
        public DeployedApplication(String name, boolean hasDescriptor) {
            this.name = name;
            this.hasDescriptor = hasDescriptor;
        }

        /**
         * context��·��
         */
        public final String name;

        /**
         * context��ΪӦ�ó����Ƿ�����/META-INF/context.xml
         */
        public final boolean hasDescriptor;

        /**
         * �洢�Ѳ���context�ⲿ��Դ���ⲿ��Դ�Ǵ��ڷ���AppBaseĿ¼֮�����Դ
         * ����keyΪ�ⲿ��Դ�ľ���·��,valueΪ��Դ����ʱ������޸�ʱ��
         */
        public final LinkedHashMap<String, Long> redeployResources =
                new LinkedHashMap<>();

        /**
         * �洢�Ѳ���context��Ҫ��������Դ��һ����Դ���������tomcat�Ȳ�����ƻ����¼���context
         */
        public final HashMap<String, Long> reloadResources = new HashMap<>();

        /**
         * context���һ�β���ʱ��
         */
        public long timestamp = System.currentTimeMillis();

        /**
         * context��ΪӦ�ó����Ƿ񲻽�ѹwar����ֱ������wai������
         */
        public boolean loggedDirWarning = false;
    }


    /**
     * ����xml�ļ������߳�
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
     * ����war�ļ������߳�
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
     * ����Ŀ¼�ļ������߳�
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
