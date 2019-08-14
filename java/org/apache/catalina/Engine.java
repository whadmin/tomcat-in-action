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


public interface Engine extends Container {

    //获取默认host组件名称
    public String getDefaultHost();

    //设置默认host组件名称
    public void setDefaultHost(String defaultHost);

    //获取Tomcat实例的JVM路由ID。所有路由ID必须唯一
    public String getJvmRoute();

    //设置Tomcat实例的JVM路由ID。所有路由ID必须唯一
    public void setJvmRoute(String jvmRouteId);

    //返回关联上层组件Service
    public Service getService();

    //设置关联上层组件Service
    public void setService(Service service);
}
