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
 *  解析标签给栈顶对象添加一个生命周期监听器
 */
public class LifecycleListenerRule extends Rule {

    public LifecycleListenerRule(String listenerClass, String attributeName) {
        this.listenerClass = listenerClass;
        this.attributeName = attributeName;
    }
    /**
     * 标准中指定属性，用来设置监听器处理类
     */
    private final String attributeName;

    /**
     * 默认监听器处理类
     */
    private final String listenerClass;

    @Override
    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {

        /** 获取栈顶原始对象 **/
        Container c = (Container) digester.peek();

        /** 获取次栈顶元素对象 **/
        Container p = null;
        Object obj = digester.peek(1);

        /** 如果栈顶元素对象是容器设置给p **/
        if (obj instanceof Container) {
            p = (Container) obj;
        }

        String className = null;

        /** 获取标签attributeName值赋值给className **/
        if (attributeName != null) {
            String value = attributes.getValue(attributeName);
            if (value != null)
                className = value;
        }

        /** 获取次栈顶对象attributeName属性值赋值给className **/
        if (p != null && className == null) {
            String configClass =
                (String) IntrospectionUtils.getProperty(p, attributeName);
            if (configClass != null && configClass.length() > 0) {
                className = configClass;
            }
        }

        /** 如果className == null使用listenerClass作为className默认值**/
        if (className == null) {
            className = listenerClass;
        }


        /** 实例化className添加栈顶对象生命周期监听器列表中*/
        Class<?> clazz = Class.forName(className);
        LifecycleListener listener = (LifecycleListener) clazz.getConstructor().newInstance();
        c.addLifecycleListener(listener);
    }
}
