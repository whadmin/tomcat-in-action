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


import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.RuleSetBase;


/**
 * <p><strong>RuleSet</strong> for processing the contents of a
 * Host definition element.  This <code>RuleSet</code> does NOT include
 * any rules for nested Context which should be added via instances of
 * <code>ContextRuleSet</code>.</p>
 *
 * @author Craig R. McClanahan
 */
@SuppressWarnings("deprecation")
public class HostRuleSet extends RuleSetBase {


    // ----------------------------------------------------- Instance Variables


    /**
     * The matching pattern prefix to use for recognizing our elements.
     */
    protected final String prefix;


    // ------------------------------------------------------------ Constructor


    /**
     * Construct an instance of this <code>RuleSet</code> with the default
     * matching pattern prefix.
     */
    public HostRuleSet() {
        this("");
    }


    /**
     * Construct an instance of this <code>RuleSet</code> with the specified
     * matching pattern prefix.
     *
     * @param prefix Prefix for matching pattern rules (including the
     *  trailing slash character)
     */
    public HostRuleSet(String prefix) {
        this.prefix = prefix;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * <p>Add the set of Rule instances defined in this RuleSet to the
     * specified <code>Digester</code> instance, associating them with
     * our namespace URI (if any).  This method should only be called
     * by a Digester instance.</p>
     *
     * @param digester Digester instance to which the new Rule instances
     *  should be added.
     */
    @Override
    public void addRuleInstances(Digester digester) {

        //解析<Server><Service><Engine><Host>标签
        /** 解析<Host>标签实例化StandardHost对象，并push到操作栈中 **/
        digester.addObjectCreate(prefix + "Host",
                                 "org.apache.catalina.core.StandardHost",
                                 "className");

        /** 解析<Host>标签将标签中属性值映射到其实例化对象中**/
        digester.addSetProperties(prefix + "Host");


        /** 解析<Host>标签，使用CopyParentClassLoaderRule规则，负责调用次栈顶对象getParentClassLoader获取父类加载，设置到栈顶对象parentClassLoader属性上 **/
        digester.addRule(prefix + "Host",
                         new CopyParentClassLoaderRule());

        /** 解析<Host>标签，使用LifecycleListenerRule规则，负责给栈顶对象添加一个生命周期监听器. 默认为hostConfigClass，或者在标签指定org.apache.catalina.startup.HostConfig属性**/
        digester.addRule(prefix + "Host",
                         new LifecycleListenerRule
                         ("org.apache.catalina.startup.HostConfig",
                          "hostConfigClass"));

        /** 解析<Host>标签将操作栈栈顶对象作为次栈顶对象StandardService.addChild方法调用的参数，即将实例化StandardHost对象添加StandardServer.child子容器列表属性中**/
        digester.addSetNext(prefix + "Host",
                            "addChild",
                            "org.apache.catalina.Container");

        /** 解析<Alias>标签，将标签中数据<Alias>test<Alias>做为参数调用栈顶对象StandardHost.addAlias方法调用的参数，设置到StandardHost属性中 **/
        digester.addCallMethod(prefix + "Host/Alias",
                               "addAlias", 0);


        //解析<Server><Service><Engine><Host><Cluster>标签
        /** 解析<Cluster>标签实例化标签中className属性定义的对象，并push到操作栈中 **/
        digester.addObjectCreate(prefix + "Host/Cluster",
                                 null, // MUST be specified in the element
                                 "className");
        /** 解析<Cluster>标签将标签中属性值映射到其实例化对象中**/
        digester.addSetProperties(prefix + "Host/Cluster");
        /** 解析</Cluster>标签将操作栈栈顶对象作为次栈顶对象StandardHost.addLifecycleListener方法调用的参数，设置到StandardHost属性中**/
        digester.addSetNext(prefix + "Host/Cluster",
                            "setCluster",
                            "org.apache.catalina.Cluster");

        //解析<Server><Service><Engine><Host><Listener>标签
        /** 解析<Listener>标签实例化标签中className属性定义的对象，并push到操作栈中 **/
        digester.addObjectCreate(prefix + "Host/Listener",
                                 null, // MUST be specified in the element
                                 "className");
        /** 解析<Listener>标签将标签中属性值映射到其实例化对象中**/
        digester.addSetProperties(prefix + "Host/Listener");
        /** 解析</Listener>标签将操作栈栈顶对象作为次栈顶对象StandardHost.addLifecycleListener方法调用的参数，设置到StandardHost属性中**/
        digester.addSetNext(prefix + "Host/Listener",
                            "addLifecycleListener",
                            "org.apache.catalina.LifecycleListener");
         //解析<Server><Service><Engine><Host><Realm>标签
        digester.addRuleSet(new RealmRuleSet(prefix + "Host/"));

        //解析<Server><Service><Engine><Host><Valve>标签
        /** 解析<Valve>标签实例化标签中className属性定义的对象，并push到操作栈中 **/
        digester.addObjectCreate(prefix + "Host/Valve",
                                 null,
                                 "className");
        /** 解析<Valve>标签将标签中属性值映射到其实例化对象中**/
        digester.addSetProperties(prefix + "Host/Valve");
        /** 解析</Valve>标签将操作栈栈顶对象作为次栈顶对象StandardHost.addValve方法调用的参数，设置到StandardHost属性中**/
        digester.addSetNext(prefix + "Host/Valve",
                            "addValve",
                            "org.apache.catalina.Valve");

    }


}
