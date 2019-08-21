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
package org.apache.catalina.util;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.tomcat.util.descriptor.web.ErrorPage;

/**
 * 支持Servelt规范中ErrorPage帮助程序
 *
 *   <error-page>
 *         <error-code>404</error-code>
 *         <location>/WEB-INF/404.html</location>
 *     </error-page>
 *
 *    <error-page>
 *         <error-code>java.lang.NullPointException</error-code>
 *         <location>/WEB-INF/NullPointException.html</location>
 *     </error-page>
 */
public class ErrorPageSupport {

    /** 存储异常类型对应ErrorPage **/
    private ConcurrentMap<String, ErrorPage> exceptionPages = new ConcurrentHashMap<>();

    /** 存储Http状态对应ErrorPage **/
    private ConcurrentMap<Integer, ErrorPage> statusPages = new ConcurrentHashMap<>();


    public void add(ErrorPage errorPage) {
        String exceptionType = errorPage.getExceptionType();
        if (exceptionType == null) {
            statusPages.put(Integer.valueOf(errorPage.getErrorCode()), errorPage);
        } else {
            exceptionPages.put(exceptionType, errorPage);
        }
    }


    public void remove(ErrorPage errorPage) {
        String exceptionType = errorPage.getExceptionType();
        if (exceptionType == null) {
            statusPages.remove(Integer.valueOf(errorPage.getErrorCode()), errorPage);
        } else {
            exceptionPages.remove(exceptionType, errorPage);
        }
    }


    public ErrorPage find(int statusCode) {
        return statusPages.get(Integer.valueOf(statusCode));
    }

    @Deprecated
    public ErrorPage find(String exceptionType) {
        return exceptionPages.get(exceptionType);
    }


    public ErrorPage find(Throwable exceptionType) {
        if (exceptionType == null) {
            return null;
        }
        Class<?> clazz = exceptionType.getClass();
        String name = clazz.getName();
        while (!Object.class.equals(clazz)) {
            ErrorPage errorPage = exceptionPages.get(name);
            if (errorPage != null) {
                return errorPage;
            }
            clazz = clazz.getSuperclass();
            if (clazz == null) {
                break;
            }
            name = clazz.getName();
        }
        return null;
    }


    public ErrorPage[] findAll() {
        Set<ErrorPage> errorPages = new HashSet<>();
        errorPages.addAll(exceptionPages.values());
        errorPages.addAll(statusPages.values());
        return errorPages.toArray(new ErrorPage[errorPages.size()]);
    }
}
