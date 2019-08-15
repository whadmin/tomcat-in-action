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

        /** 获取host子容器组件 **/
        Host host = request.getHost();
        if (host == null) {
            response.sendError
                (HttpServletResponse.SC_BAD_REQUEST,
                 sm.getString("standardEngine.noHost",
                              request.getServerName()));
            return;
        }

        /** 如果当前请求支持异步，则检查当前容器组件Pipeline管道种所有Value阀是否都支持异步，如果不是则重置为false **/
        if (request.isAsyncSupported()) {
            /** getPipeline().isAsyncSupported() 如果当前容器组件Pipeline管道种所有Value阀都支持异步则返回true**/
            /** 设置当前请求是否支持异步，需要当前容器Pipeline管道种所有Value阀都支持异步 **/
            request.setAsyncSupported(host.getPipeline().isAsyncSupported());
        }

        /** 调用host容器的Pipeline管道的第一个Value阀执行 **/
        host.getPipeline().getFirst().invoke(request, response);
    }
}
