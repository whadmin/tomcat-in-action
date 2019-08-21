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

import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.descriptor.web.ErrorPage;
import org.apache.tomcat.util.res.StringManager;

/**
 * Valve that implements the default basic behavior for the
 * <code>StandardHost</code> container implementation.
 * <p>
 * <b>USAGE CONSTRAINT</b>:  This implementation is likely to be useful only
 * when processing HTTP requests.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
final class StandardHostValve extends ValveBase {

    private static final Log log = LogFactory.getLog(StandardHostValve.class);


    private static final ClassLoader MY_CLASSLOADER =
            StandardHostValve.class.getClassLoader();

    static final boolean STRICT_SERVLET_COMPLIANCE;

    static final boolean ACCESS_SESSION;

    static {
        STRICT_SERVLET_COMPLIANCE = Globals.STRICT_SERVLET_COMPLIANCE;

        String accessSession = System.getProperty(
                "org.apache.catalina.core.StandardHostValve.ACCESS_SESSION");
        if (accessSession == null) {
            ACCESS_SESSION = STRICT_SERVLET_COMPLIANCE;
        } else {
            ACCESS_SESSION = Boolean.parseBoolean(accessSession);
        }
    }

    public StandardHostValve() {
        super(true);
    }

    /**
     * 日志格式管理器
     */
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);


    /**
     * 处理请求
     */
    @Override
    public final void invoke(Request request, Response response)
        throws IOException, ServletException {

        // Select the Context to be used for this Request
        Context context = request.getContext();
        if (context == null) {
            return;
        }
        /** 如果当前请求支持异步，则检查当前容器组件Pipeline管道种所有Value阀是否都支持异步，如果不是则重置为false **/
        if (request.isAsyncSupported()) {
            request.setAsyncSupported(context.getPipeline().isAsyncSupported());
        }

        /** servlet是否异步处理**/
        boolean asyncAtStart = request.isAsync();

        try {
            context.bind(Globals.IS_SECURITY_ENABLED, MY_CLASSLOADER);

            /** 如果不是否异步处理，且 context触发ServletRequestEvent事件的监听器没有发生异常直接返回 **/
            if (!asyncAtStart && !context.fireRequestInitEvent(request.getRequest())) {
                return;
            }

            /** 如果响应发生了错误，但还没由报告，则调用context重新处理 **/
            try {
                if (!response.isErrorReportRequired()) {
                    context.getPipeline().getFirst().invoke(request, response);
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                container.getLogger().error("Exception Processing " + request.getRequestURI(), t);
                if (!response.isErrorReportRequired()) {
                    request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, t);
                    throwable(request, response, t);
                }
            }

            /** 设置挂起的标志。 **/
            response.setSuspended(false);

            /** 获取异常 **/
            Throwable t = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

            /** 如果context组件处于非运行状态直接返回 **/
            if (!context.getState().isAvailable()) {
                return;
            }

            /** 如果响应发生了错误，但还没由报告 **/
            if (response.isErrorReportRequired()) {
                /** 获取到异常 **/
                if (t != null) {
                    /** 抛出异常 **/
                    throwable(request, response, t);
                } else {
                    status(request, response);
                }
            }
            /** servlet不是否异步处理**/
            if (!request.isAsync() && !asyncAtStart) {
                /** 触发ServletRequestEvent事件 **/
                context.fireRequestDestroyEvent(request.getRequest());
            }
        } finally {
            if (ACCESS_SESSION) {
                request.getSession(false);
            }
            context.unbind(Globals.IS_SECURITY_ENABLED, MY_CLASSLOADER);
        }
    }


    // -------------------------------------------------------- Private Methods

    /**
     * Handle the HTTP status code (and corresponding message) generated
     * while processing the specified Request to produce the specified
     * Response.  Any exceptions that occur during generation of the error
     * report are logged and swallowed.
     *
     * @param request The request being processed
     * @param response The response being generated
     */
    private void status(Request request, Response response) {

        int statusCode = response.getStatus();

        // Handle a custom error page for this status code
        Context context = request.getContext();
        if (context == null) {
            return;
        }

        /* Only look for error pages when isError() is set.
         * isError() is set when response.sendError() is invoked. This
         * allows custom error pages without relying on default from
         * web.xml.
         */
        if (!response.isError()) {
            return;
        }

        ErrorPage errorPage = context.findErrorPage(statusCode);
        if (errorPage == null) {
            // Look for a default error page
            errorPage = context.findErrorPage(0);
        }
        if (errorPage != null && response.isErrorReportRequired()) {
            response.setAppCommitted(false);
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE,
                              Integer.valueOf(statusCode));

            String message = response.getMessage();
            if (message == null) {
                message = "";
            }
            request.setAttribute(RequestDispatcher.ERROR_MESSAGE, message);
            request.setAttribute(Globals.DISPATCHER_REQUEST_PATH_ATTR,
                    errorPage.getLocation());
            request.setAttribute(Globals.DISPATCHER_TYPE_ATTR,
                    DispatcherType.ERROR);


            Wrapper wrapper = request.getWrapper();
            if (wrapper != null) {
                request.setAttribute(RequestDispatcher.ERROR_SERVLET_NAME,
                                  wrapper.getName());
            }
            request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI,
                                 request.getRequestURI());
            if (custom(request, response, errorPage)) {
                response.setErrorReported();
                try {
                    response.finishResponse();
                } catch (ClientAbortException e) {
                    // Ignore
                } catch (IOException e) {
                    container.getLogger().warn("Exception Processing " + errorPage, e);
                }
            }
        }
    }


    /**
     * Handle the specified Throwable encountered while processing
     * the specified Request to produce the specified Response.  Any
     * exceptions that occur during generation of the exception report are
     * logged and swallowed.
     *
     * @param request The request being processed
     * @param response The response being generated
     * @param throwable The exception that occurred (which possibly wraps
     *  a root cause exception
     */
    protected void throwable(Request request, Response response,
                             Throwable throwable) {
        Context context = request.getContext();
        if (context == null) {
            return;
        }

        Throwable realError = throwable;

        if (realError instanceof ServletException) {
            realError = ((ServletException) realError).getRootCause();
            if (realError == null) {
                realError = throwable;
            }
        }

        // If this is an aborted request from a client just log it and return
        if (realError instanceof ClientAbortException ) {
            if (log.isDebugEnabled()) {
                log.debug
                    (sm.getString("standardHost.clientAbort",
                        realError.getCause().getMessage()));
            }
            return;
        }

        ErrorPage errorPage = context.findErrorPage(throwable);
        if ((errorPage == null) && (realError != throwable)) {
            errorPage = context.findErrorPage(realError);
        }

        if (errorPage != null) {
            if (response.setErrorReported()) {
                response.setAppCommitted(false);
                request.setAttribute(Globals.DISPATCHER_REQUEST_PATH_ATTR,
                        errorPage.getLocation());
                request.setAttribute(Globals.DISPATCHER_TYPE_ATTR,
                        DispatcherType.ERROR);
                request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE,
                        Integer.valueOf(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
                request.setAttribute(RequestDispatcher.ERROR_MESSAGE,
                                  throwable.getMessage());
                request.setAttribute(RequestDispatcher.ERROR_EXCEPTION,
                                  realError);
                Wrapper wrapper = request.getWrapper();
                if (wrapper != null) {
                    request.setAttribute(RequestDispatcher.ERROR_SERVLET_NAME,
                                      wrapper.getName());
                }
                request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI,
                                     request.getRequestURI());
                request.setAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE,
                                  realError.getClass());
                if (custom(request, response, errorPage)) {
                    try {
                        response.finishResponse();
                    } catch (IOException e) {
                        container.getLogger().warn("Exception Processing " + errorPage, e);
                    }
                }
            }
        } else {

            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setError();
            status(request, response);
        }
    }


    /**
     * Handle an HTTP status code or Java exception by forwarding control
     * to the location included in the specified errorPage object.  It is
     * assumed that the caller has already recorded any request attributes
     * that are to be forwarded to this page.  Return <code>true</code> if
     * we successfully utilized the specified error page location, or
     * <code>false</code> if the default error report should be rendered.
     *
     * @param request The request being processed
     * @param response The response being generated
     * @param errorPage The errorPage directive we are obeying
     */
    private boolean custom(Request request, Response response,
                             ErrorPage errorPage) {

        if (container.getLogger().isDebugEnabled()) {
            container.getLogger().debug("Processing " + errorPage);
        }

        try {
            // Forward control to the specified location
            ServletContext servletContext =
                request.getContext().getServletContext();
            RequestDispatcher rd =
                servletContext.getRequestDispatcher(errorPage.getLocation());

            if (rd == null) {
                container.getLogger().error(
                    sm.getString("standardHostValue.customStatusFailed", errorPage.getLocation()));
                return false;
            }

            if (response.isCommitted()) {
                // Response is committed - including the error page is the
                // best we can do
                rd.include(request.getRequest(), response.getResponse());
            } else {
                // Reset the response (keeping the real error code and message)
                response.resetBuffer(true);
                response.setContentLength(-1);

                rd.forward(request.getRequest(), response.getResponse());

                // If we forward, the response is suspended again
                response.setSuspended(false);
            }

            // Indicate that we have successfully processed this custom page
            return true;

        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            // Report our failure to process this custom page
            container.getLogger().error("Exception Processing " + errorPage, t);
            return false;
        }
    }
}
