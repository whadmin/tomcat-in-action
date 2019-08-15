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
package org.apache.catalina.core;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Host;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.tomcat.util.res.StringManager;


final class StandardEngineValve extends ValveBase {

    public StandardEngineValve() {
        super(true);
    }

    private static final StringManager sm =
        StringManager.getManager(Constants.Package);


    @Override
    public final void invoke(Request request, Response response)
        throws IOException, ServletException {

        /** ��ȡhost��������� **/
        Host host = request.getHost();
        if (host == null) {
            response.sendError
                (HttpServletResponse.SC_BAD_REQUEST,
                 sm.getString("standardEngine.noHost",
                              request.getServerName()));
            return;
        }

        /** �����ǰ����֧���첽�����鵱ǰ�������Pipeline�ܵ�������Value���Ƿ�֧���첽���������������Ϊfalse **/
        if (request.isAsyncSupported()) {
            /** getPipeline().isAsyncSupported() �����ǰ�������Pipeline�ܵ�������Value����֧���첽�򷵻�true**/
            /** ���õ�ǰ�����Ƿ�֧���첽����Ҫ��ǰ����Pipeline�ܵ�������Value����֧���첽 **/
            request.setAsyncSupported(host.getPipeline().isAsyncSupported());
        }

        /** ����host������Pipeline�ܵ��ĵ�һ��Value��ִ�� **/
        host.getPipeline().getFirst().invoke(request, response);
    }
}
