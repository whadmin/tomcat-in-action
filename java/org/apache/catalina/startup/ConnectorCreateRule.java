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


import java.lang.reflect.Method;

import org.apache.catalina.Executor;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.digester.Rule;
import org.apache.tomcat.util.res.StringManager;
import org.xml.sax.Attributes;


/**
 * ����<Connector>��������
 */
public class ConnectorCreateRule extends Rule {

    private static final Log log = LogFactory.getLog(ConnectorCreateRule.class);
    protected static final StringManager sm = StringManager.getManager(ConnectorCreateRule.class);
    // --------------------------------------------------------- Public Methods


    /**
     * ����<Server><Service><Connector>�ص������߼�
     */
    @Override
    public void begin(String namespace, String name, Attributes attributes)
            throws Exception {
        /** ��ȡջ������svc **/
        Service svc = (Service)digester.peek();
        Executor ex = null;

        /** ���<Server><Service><Connector>��ǩ����executor���ԣ����Service�����ȡ������ֵ��Ӧ���ӳ�ex**/
        if ( attributes.getValue("executor")!=null ) {
            ex = svc.getExecutor(attributes.getValue("executor"));
        }
        /** ʵ����Connector��� **/
        Connector con = new Connector(attributes.getValue("protocol"));
        if (ex != null) {
            /** ʹ�÷��似�������ӳ����ø�ProtocolHandler���executor����  **/
            setExecutor(con, ex);
        }

        /** ��ȡ<Server><Service><Connector>��ǩsslImplementationName���� **/
        String sslImplementationName = attributes.getValue("sslImplementationName");
        if (sslImplementationName != null) {
            /** ʹ�÷��似�������ӳ����ø�ProtocolHandler���sslImplementationName����  **/
            setSSLImplementationName(con, sslImplementationName);
        }
        digester.push(con);
    }

    /**
     * ʹ�÷��似�������ӳ����ø�ProtocolHandler���executor����
     */
    private static void setExecutor(Connector con, Executor ex) throws Exception {
        Method m = IntrospectionUtils.findMethod(con.getProtocolHandler().getClass(),"setExecutor",new Class[] {java.util.concurrent.Executor.class});
        if (m!=null) {
            m.invoke(con.getProtocolHandler(), new Object[] {ex});
        }else {
            log.warn(sm.getString("connector.noSetExecutor", con));
        }
    }

    /**
     * ʹ�÷��似�������ӳ����ø�ProtocolHandler���sslImplementationName����
     */
    private static void setSSLImplementationName(Connector con, String sslImplementationName) throws Exception {
        Method m = IntrospectionUtils.findMethod(con.getProtocolHandler().getClass(),"setSslImplementationName",new Class[] {String.class});
        if (m != null) {
            m.invoke(con.getProtocolHandler(), new Object[] {sslImplementationName});
        } else {
            log.warn(sm.getString("connector.noSetSSLImplementationName", con));
        }
    }

    /**
     * ����<Server><Service></Connector>�ص������߼�
     */
    @Override
    public void end(String namespace, String name) throws Exception {
        digester.pop();
    }
}
