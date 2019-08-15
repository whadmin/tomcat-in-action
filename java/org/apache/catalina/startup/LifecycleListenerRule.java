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


import org.apache.catalina.Container;
import org.apache.catalina.LifecycleListener;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.digester.Rule;
import org.xml.sax.Attributes;


/**
 *  ������ǩ��ջ���������һ���������ڼ�����
 */
public class LifecycleListenerRule extends Rule {

    public LifecycleListenerRule(String listenerClass, String attributeName) {
        this.listenerClass = listenerClass;
        this.attributeName = attributeName;
    }
    /**
     * ��׼��ָ�����ԣ��������ü�����������
     */
    private final String attributeName;

    /**
     * Ĭ�ϼ�����������
     */
    private final String listenerClass;

    @Override
    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {

        /** ��ȡջ��ԭʼ���� **/
        Container c = (Container) digester.peek();

        /** ��ȡ��ջ��Ԫ�ض��� **/
        Container p = null;
        Object obj = digester.peek(1);

        /** ���ջ��Ԫ�ض������������ø�p **/
        if (obj instanceof Container) {
            p = (Container) obj;
        }

        String className = null;

        /** ��ȡ��ǩattributeNameֵ��ֵ��className **/
        if (attributeName != null) {
            String value = attributes.getValue(attributeName);
            if (value != null)
                className = value;
        }

        /** ��ȡ��ջ������attributeName����ֵ��ֵ��className **/
        if (p != null && className == null) {
            String configClass =
                (String) IntrospectionUtils.getProperty(p, attributeName);
            if (configClass != null && configClass.length() > 0) {
                className = configClass;
            }
        }

        /** ���className == nullʹ��listenerClass��ΪclassNameĬ��ֵ**/
        if (className == null) {
            className = listenerClass;
        }


        /** ʵ����className���ջ�������������ڼ������б���*/
        Class<?> clazz = Class.forName(className);
        LifecycleListener listener = (LifecycleListener) clazz.getConstructor().newInstance();
        c.addLifecycleListener(listener);
    }
}
