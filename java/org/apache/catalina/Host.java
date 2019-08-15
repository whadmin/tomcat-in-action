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
     * 返回配置文件相对路径，配置文件完整路径为$catalinaBase/xmlBase
     */
    public String getXmlBase();

    /**
     * 设置配置文件相对路径
     */
    public void setXmlBase(String xmlBase);

    /**
     * 返回配置文件对象，对应完整路径为$catalinaBase/xmlBase
     */
    public File getConfigBaseFile();

    /**
     * 返回扫描的应用程序相对路径或绝对路径，如果是相对路径则完整路径为$catalinaHome/appBase
     */
    public String getAppBase();

    /**
     * 设置扫描的应用程序相对路径或绝对路径
     */
    public void setAppBase(String appBase);

    /**
     * 返回扫描应用程序目录的文件对象
     */
    public File getAppBaseFile();

    /**
     * 是否开启热部署
     */
    public boolean getAutoDeploy();

    /**
     * 设置是否支持热部署
     */
    public void setAutoDeploy(boolean autoDeploy);

    /**
     * 获取子组件Context配置实现类，默认org.apache.catalina.startup.ContextConfig
     */
    public String getConfigClass();

    /**
     * 设置子组件Context配置实现类
     */
    public void setConfigClass(String configClass);

    /**
     * 返回是否在启动Host组件时是否应自动部署Host组件的Web应用程序
     */
    public boolean getDeployOnStartup();

    /**
     * 设置是否在启动Host组件时自动部署Web应用程序
     */
    public void setDeployOnStartup(boolean deployOnStartup);

    /**
     * 返回正则表达式，用String表示，用来定义自动部署哪些应用程序
     */
    public String getDeployIgnore();

    /**
     * 返回正则表达式，用来定义自动部署哪些应用程序
     */
    public Pattern getDeployIgnorePattern();

    /**
     * 设置正则表达式，用String表示，用来定义自动部署哪些应用程序
     */
    public void setDeployIgnore(String deployIgnore);

    /**
     * 返回处理子容器启动关闭线程池
     */
    public ExecutorService getStartStopExecutor();

    /**
     * 返回是否需要在启动时创建appbase和xmlbase目录
     */
    public boolean getCreateDirs();

    /**
     * 设置是否需要在启动时创建appbase和xmlbase目录
     */
    public void setCreateDirs(boolean createDirs);

    /**
     * 返回是否检查现在可以取消部署的旧版本的应用程序
     */
    public boolean getUndeployOldVersions();

    /**
     * 设置是否检查现在可以取消部署的旧版本的应用程序
     */
    public void setUndeployOldVersions(boolean undeployOldVersions);

    /**
     * 给Host组件添加别名
     */
    public void addAlias(String alias);

    /**
     * 返回Host组件所有别名
     */
    public String[] findAliases();

    /**
     * 给Host组件删除别名
     */
    public void removeAlias(String alias);
}
