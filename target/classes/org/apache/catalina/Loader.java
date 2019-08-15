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

import java.beans.PropertyChangeListener;

/**
 * Loader用来表示一个类加载器实现
 * Loader由Context容器组件控制加载类文件（在关联的存储库中）
 */
public interface Loader {


    /**
     * 执行定期任务，主要用于重新加载类文件。
     */
    public void backgroundProcess();


    /**
     * 返回这个容器组件要使用的Java类加载器。
     */
    public ClassLoader getClassLoader();


    /**
     * 返回当前组件Context子组件
     */
    public Context getContext();


    /**
     * 设置当前组件Context子组件
     */
    public void setContext(Context context);


    /**
     * 是否允许使用代理类加载器（非默认类加载器）
     */
    public boolean getDelegate();


    /**
     * 设置是否允许使用代理类加载器（非默认类加载器）
     */
    public void setDelegate(boolean delegate);


    /**
     * 返回是否开启热加载机制
     * Loader组件定时任务触发调用modified方法检查检查已经加载的资源是否有修改/增加/删减,如果发现资源由变更触发StandardContext组件reload;
     */
    public boolean getReloadable();


    /**
     * 设置是否开启热加载机制
     * Loader组件定时任务触发调用modified方法检查检查已经加载的资源是否有修改/增加/删减,如果发现资源由变更触发StandardContext组件reload;
     */
    public void setReloadable(boolean reloadable);


    /**
     * 检查已经加载的资源是否有修改/增加/删减,
     * Loader实现类组件定时任务触发调用，如果发现资源由变更触发StandardContext组件reload;
     */
    public boolean modified();


    /**
     * 给当前容器组件删除属性变更监听器
     */
    public void removePropertyChangeListener(PropertyChangeListener listener);


    /**
     * 给当前容器组件添加属性变更监听器
     */
    public void addPropertyChangeListener(PropertyChangeListener listener);
}
