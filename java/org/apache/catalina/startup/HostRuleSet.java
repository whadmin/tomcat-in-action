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

        //����<Server><Service><Engine><Host>��ǩ
        /** ����<Host>��ǩʵ����StandardHost���󣬲�push������ջ�� **/
        digester.addObjectCreate(prefix + "Host",
                                 "org.apache.catalina.core.StandardHost",
                                 "className");

        /** ����<Host>��ǩ����ǩ������ֵӳ�䵽��ʵ����������**/
        digester.addSetProperties(prefix + "Host");


        /** ����<Host>��ǩ��ʹ��CopyParentClassLoaderRule���򣬸�����ô�ջ������getParentClassLoader��ȡ������أ����õ�ջ������parentClassLoader������ **/
        digester.addRule(prefix + "Host",
                         new CopyParentClassLoaderRule());

        /** ����<Host>��ǩ��ʹ��LifecycleListenerRule���򣬸����ջ���������һ���������ڼ�����. Ĭ��ΪhostConfigClass�������ڱ�ǩָ��org.apache.catalina.startup.HostConfig����**/
        digester.addRule(prefix + "Host",
                         new LifecycleListenerRule
                         ("org.apache.catalina.startup.HostConfig",
                          "hostConfigClass"));

        /** ����<Host>��ǩ������ջջ��������Ϊ��ջ������StandardService.addChild�������õĲ���������ʵ����StandardHost�������StandardServer.child�������б�������**/
        digester.addSetNext(prefix + "Host",
                            "addChild",
                            "org.apache.catalina.Container");

        /** ����<Alias>��ǩ������ǩ������<Alias>test<Alias>��Ϊ��������ջ������StandardHost.addAlias�������õĲ��������õ�StandardHost������ **/
        digester.addCallMethod(prefix + "Host/Alias",
                               "addAlias", 0);


        //����<Server><Service><Engine><Host><Cluster>��ǩ
        /** ����<Cluster>��ǩʵ������ǩ��className���Զ���Ķ��󣬲�push������ջ�� **/
        digester.addObjectCreate(prefix + "Host/Cluster",
                                 null, // MUST be specified in the element
                                 "className");
        /** ����<Cluster>��ǩ����ǩ������ֵӳ�䵽��ʵ����������**/
        digester.addSetProperties(prefix + "Host/Cluster");
        /** ����</Cluster>��ǩ������ջջ��������Ϊ��ջ������StandardHost.addLifecycleListener�������õĲ��������õ�StandardHost������**/
        digester.addSetNext(prefix + "Host/Cluster",
                            "setCluster",
                            "org.apache.catalina.Cluster");

        //����<Server><Service><Engine><Host><Listener>��ǩ
        /** ����<Listener>��ǩʵ������ǩ��className���Զ���Ķ��󣬲�push������ջ�� **/
        digester.addObjectCreate(prefix + "Host/Listener",
                                 null, // MUST be specified in the element
                                 "className");
        /** ����<Listener>��ǩ����ǩ������ֵӳ�䵽��ʵ����������**/
        digester.addSetProperties(prefix + "Host/Listener");
        /** ����</Listener>��ǩ������ջջ��������Ϊ��ջ������StandardHost.addLifecycleListener�������õĲ��������õ�StandardHost������**/
        digester.addSetNext(prefix + "Host/Listener",
                            "addLifecycleListener",
                            "org.apache.catalina.LifecycleListener");
         //����<Server><Service><Engine><Host><Realm>��ǩ
        digester.addRuleSet(new RealmRuleSet(prefix + "Host/"));

        //����<Server><Service><Engine><Host><Valve>��ǩ
        /** ����<Valve>��ǩʵ������ǩ��className���Զ���Ķ��󣬲�push������ջ�� **/
        digester.addObjectCreate(prefix + "Host/Valve",
                                 null,
                                 "className");
        /** ����<Valve>��ǩ����ǩ������ֵӳ�䵽��ʵ����������**/
        digester.addSetProperties(prefix + "Host/Valve");
        /** ����</Valve>��ǩ������ջջ��������Ϊ��ջ������StandardHost.addValve�������õĲ��������õ�StandardHost������**/
        digester.addSetNext(prefix + "Host/Valve",
                            "addValve",
                            "org.apache.catalina.Valve");

    }


}
