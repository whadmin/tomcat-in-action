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
package org.apache.catalina;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;



public interface Host extends Container {


    public static final String ADD_ALIAS_EVENT = "addAlias";


    public static final String REMOVE_ALIAS_EVENT = "removeAlias";


    /**
     * ���������ļ����·���������ļ�����·��Ϊ$catalinaBase/xmlBase
     */
    public String getXmlBase();

    /**
     * ���������ļ����·��
     */
    public void setXmlBase(String xmlBase);

    /**
     * ���������ļ����󣬶�Ӧ����·��Ϊ$catalinaBase/xmlBase
     */
    public File getConfigBaseFile();

    /**
     * ����ɨ���Ӧ�ó������·�������·������������·��������·��Ϊ$catalinaHome/appBase
     */
    public String getAppBase();

    /**
     * ����ɨ���Ӧ�ó������·�������·��
     */
    public void setAppBase(String appBase);

    /**
     * ����ɨ��Ӧ�ó���Ŀ¼���ļ�����
     */
    public File getAppBaseFile();

    /**
     * �Ƿ����Ȳ���
     */
    public boolean getAutoDeploy();

    /**
     * �����Ƿ�֧���Ȳ���
     */
    public void setAutoDeploy(boolean autoDeploy);

    /**
     * ��ȡ�����Context����ʵ���࣬Ĭ��org.apache.catalina.startup.ContextConfig
     */
    public String getConfigClass();

    /**
     * ���������Context����ʵ����
     */
    public void setConfigClass(String configClass);

    /**
     * �����Ƿ�������Host���ʱ�Ƿ�Ӧ�Զ�����Host�����WebӦ�ó���
     */
    public boolean getDeployOnStartup();

    /**
     * �����Ƿ�������Host���ʱ�Զ�����WebӦ�ó���
     */
    public void setDeployOnStartup(boolean deployOnStartup);

    /**
     * ����������ʽ����String��ʾ�����������Զ�������ЩӦ�ó���
     */
    public String getDeployIgnore();

    /**
     * ����������ʽ�����������Զ�������ЩӦ�ó���
     */
    public Pattern getDeployIgnorePattern();

    /**
     * ����������ʽ����String��ʾ�����������Զ�������ЩӦ�ó���
     */
    public void setDeployIgnore(String deployIgnore);

    /**
     * ���ش��������������ر��̳߳�
     */
    public ExecutorService getStartStopExecutor();

    /**
     * �����Ƿ���Ҫ������ʱ����appbase��xmlbaseĿ¼
     */
    public boolean getCreateDirs();

    /**
     * �����Ƿ���Ҫ������ʱ����appbase��xmlbaseĿ¼
     */
    public void setCreateDirs(boolean createDirs);

    /**
     * �����Ƿ������ڿ���ȡ������ľɰ汾��Ӧ�ó���
     */
    public boolean getUndeployOldVersions();

    /**
     * �����Ƿ������ڿ���ȡ������ľɰ汾��Ӧ�ó���
     */
    public void setUndeployOldVersions(boolean undeployOldVersions);

    /**
     * ��Host�����ӱ���
     */
    public void addAlias(String alias);

    /**
     * ����Host������б���
     */
    public String[] findAliases();

    /**
     * ��Host���ɾ������
     */
    public void removeAlias(String alias);
}
