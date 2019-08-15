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

        //����<Server><Service><Engine>��ǩ
        /** ����<Engine>��ǩʵ����StandardEngine���󣬲�push������ջ�� **/
        digester.addObjectCreate(prefix + "Engine",
                                 "org.apache.catalina.core.StandardEngine",
                                 "className");

        /** ����<Engine>��ǩ����ǩ������ֵӳ�䵽��ʵ����������**/
        digester.addSetProperties(prefix + "Engine");

        /** ����<Engine>��ǩ��ʹ��LifecycleListenerRule���򣬸����ջ���������һ���������ڼ�����. Ĭ��listenerClassΪengineConfigClass��
         * �����ڱ�ǩָ��org.apache.catalina.startup.EngineConfig����ֵ��ΪlistenerClass**/
        digester.addRule(prefix + "Engine",
                         new LifecycleListenerRule
                         ("org.apache.catalina.startup.EngineConfig",
                          "engineConfigClass"));

        /** ����<Engine>��ǩ������ջջ��������Ϊ��ջ������StandardService.setContainer�������õĲ����������õ�StandardServer.container������**/
        digester.addSetNext(prefix + "Engine",
                            "setContainer",
                            "org.apache.catalina.Engine");

        //����<Server><Service><Engine><Cluster>��ǩ
        /** ����<Cluster>��ǩʵ������ǩ��className���Զ���Ķ��󣬲�push������ջ�� **/
        digester.addObjectCreate(prefix + "Engine/Cluster",
                                 null, // MUST be specified in the element
                                 "className");

        /** ����<Cluster>��ǩ����ǩ������ֵӳ�䵽��ʵ����������**/
        digester.addSetProperties(prefix + "Engine/Cluster");

        /** ����<Cluster>��ǩ������ջջ��������Ϊ��ջ������StandardEngine.setCluster�������õĲ����������õ�StandardEngine.cluster������**/
        digester.addSetNext(prefix + "Engine/Cluster",
                            "setCluster",
                            "org.apache.catalina.Cluster");

        //����<Server><Service><Engine><Listener>��ǩ
        /** ����<Listener>��ǩʵ������ǩ��className���Զ���Ķ��󣬲�push������ջ�� **/
        digester.addObjectCreate(prefix + "Engine/Listener",
                                 null, // MUST be specified in the element
                                 "className");

        /** ����<Listener>��ǩ����ǩ������ֵӳ�䵽��ʵ����������**/
        digester.addSetProperties(prefix + "Engine/Listener");

        /** ����<Cluster>��ǩ������ջջ��������Ϊ��ջ������StandardEngine.addLifecycleListener�������õĲ����������õ�StandardEngine�������ڼ�����������**/
        digester.addSetNext(prefix + "Engine/Listener",
                            "addLifecycleListener",
                            "org.apache.catalina.LifecycleListener");

        /** ����<Realm>��ǩʹ���Զ��������RealmRuleSet**/
        digester.addRuleSet(new RealmRuleSet(prefix + "Engine/"));


        //����<Server><Service><Engine><Valve>��ǩ
        /** ����<Valve>��ǩʵ������ǩ��className���Զ���Ķ��󣬲�push������ջ�� **/
        digester.addObjectCreate(prefix + "Engine/Valve",
                                 null, // MUST be specified in the element
                                 "className");

        /** ����<Valve>��ǩ����ǩ������ֵӳ�䵽��ʵ����������**/
        digester.addSetProperties(prefix + "Engine/Valve");


        /** ����<Valve>��ǩ������ջջ��������Ϊ��ջ������StandardEngine.addValve�������õĲ����������õ�StandardEngine��Pipline�����**/
        digester.addSetNext(prefix + "Engine/Valve",
                            "addValve",
                            "org.apache.catalina.Valve");

    }


}
