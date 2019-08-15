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
 * Engine definition element.  This <code>RuleSet</code> does NOT include
 * any rules for nested Host elements, which should be added via instances of
 * <code>HostRuleSet</code>.</p>
 *
 * @author Craig R. McClanahan
 */
@SuppressWarnings("deprecation")
public class EngineRuleSet extends RuleSetBase {


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
    public EngineRuleSet() {
        this("");
    }


    /**
     * Construct an instance of this <code>RuleSet</code> with the specified
     * matching pattern prefix.
     *
     * @param prefix Prefix for matching pattern rules (including the
     *  trailing slash character)
     */
    public EngineRuleSet(String prefix) {
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

        //解析<Server><Service><Engine>标签
        /** 解析<Engine>标签实例化StandardEngine对象，并push到操作栈中 **/
        digester.addObjectCreate(prefix + "Engine",
                                 "org.apache.catalina.core.StandardEngine",
                                 "className");

        /** 解析<Engine>标签将标签中属性值映射到其实例化对象中**/
        digester.addSetProperties(prefix + "Engine");

        /** 解析<Engine>标签，使用LifecycleListenerRule规则，负责给栈顶对象添加一个生命周期监听器. 默认listenerClass为engineConfigClass，
         * 或者在标签指定org.apache.catalina.startup.EngineConfig属性值作为listenerClass**/
        digester.addRule(prefix + "Engine",
                         new LifecycleListenerRule
                         ("org.apache.catalina.startup.EngineConfig",
                          "engineConfigClass"));

        /** 解析<Engine>标签将操作栈栈顶对象作为次栈顶对象StandardService.setContainer方法调用的参数，即设置到StandardServer.container属性中**/
        digester.addSetNext(prefix + "Engine",
                            "setContainer",
                            "org.apache.catalina.Engine");

        //解析<Server><Service><Engine><Cluster>标签
        /** 解析<Cluster>标签实例化标签中className属性定义的对象，并push到操作栈中 **/
        digester.addObjectCreate(prefix + "Engine/Cluster",
                                 null, // MUST be specified in the element
                                 "className");

        /** 解析<Cluster>标签将标签中属性值映射到其实例化对象中**/
        digester.addSetProperties(prefix + "Engine/Cluster");

        /** 解析<Cluster>标签将操作栈栈顶对象作为次栈顶对象StandardEngine.setCluster方法调用的参数，即设置到StandardEngine.cluster属性中**/
        digester.addSetNext(prefix + "Engine/Cluster",
                            "setCluster",
                            "org.apache.catalina.Cluster");

        //解析<Server><Service><Engine><Listener>标签
        /** 解析<Listener>标签实例化标签中className属性定义的对象，并push到操作栈中 **/
        digester.addObjectCreate(prefix + "Engine/Listener",
                                 null, // MUST be specified in the element
                                 "className");

        /** 解析<Listener>标签将标签中属性值映射到其实例化对象中**/
        digester.addSetProperties(prefix + "Engine/Listener");

        /** 解析<Cluster>标签将操作栈栈顶对象作为次栈顶对象StandardEngine.addLifecycleListener方法调用的参数，即设置到StandardEngine生命周期监听器数组中**/
        digester.addSetNext(prefix + "Engine/Listener",
                            "addLifecycleListener",
                            "org.apache.catalina.LifecycleListener");

        /** 解析<Realm>标签使用自定义规则组RealmRuleSet**/
        digester.addRuleSet(new RealmRuleSet(prefix + "Engine/"));


        //解析<Server><Service><Engine><Valve>标签
        /** 解析<Valve>标签实例化标签中className属性定义的对象，并push到操作栈中 **/
        digester.addObjectCreate(prefix + "Engine/Valve",
                                 null, // MUST be specified in the element
                                 "className");

        /** 解析<Valve>标签将标签中属性值映射到其实例化对象中**/
        digester.addSetProperties(prefix + "Engine/Valve");


        /** 解析<Valve>标签将操作栈栈顶对象作为次栈顶对象StandardEngine.addValve方法调用的参数，即设置到StandardEngine中Pipline组件中**/
        digester.addSetNext(prefix + "Engine/Valve",
                            "addValve",
                            "org.apache.catalina.Valve");

    }


}
