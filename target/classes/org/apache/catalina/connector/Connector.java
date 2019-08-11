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
package org.apache.catalina.connector;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

import javax.management.ObjectName;

import org.apache.catalina.Globals;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Service;
import org.apache.catalina.core.AprLifecycleListener;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.coyote.Adapter;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.UpgradeProtocol;
import org.apache.coyote.http11.AbstractHttp11JsseProtocol;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.openssl.OpenSSLImplementation;
import org.apache.tomcat.util.res.StringManager;


/**
 * Implementation of a Coyote connector.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
public class Connector extends LifecycleMBeanBase  {

    private static final Log log = LogFactory.getLog(Connector.class);


    /**
     * Alternate flag to enable recycling of facades.
     */
    public static final boolean RECYCLE_FACADES =
        Boolean.parseBoolean(System.getProperty("org.apache.catalina.connector.RECYCLE_FACADES", "false"));


    // ------------------------------------------------------------ Constructor

    public Connector() {
        this(null);
    }

    /**
     * 实例化Connector
     * @param protocol
     */
    public Connector(String protocol) {
        setProtocol(protocol);
        /**  使用反射实例化ProtocolHandler **/
        ProtocolHandler p = null;
        try {
            Class<?> clazz = Class.forName(protocolHandlerClassName);
            p = (ProtocolHandler) clazz.getConstructor().newInstance();
        } catch (Exception e) {
            log.error(sm.getString(
                    "coyoteConnector.protocolHandlerInstantiationFailed"), e);
        } finally {
            this.protocolHandler = p;
        }

        if (Globals.STRICT_SERVLET_COMPLIANCE) {
            uriCharset = StandardCharsets.ISO_8859_1;
        } else {
            uriCharset = StandardCharsets.UTF_8;
        }
    }

    /**
     * 外部容器Service组件
     */
    protected Service service = null;


    /**
     * 启用或禁用TRACE HTTP方法。如果未指定，则此属性设置为false
     */
    protected boolean allowTrace = false;


    /**
     * 异步请求的默认超时（以毫秒为单位）。如果未指定，则将此属性设置为Servlet规范默认值30000（30秒）
     */
    protected long asyncTimeout = 30000;


    /**
     * 是否使用DNS查找远程主机IP，默认被禁用。
     * request.getRemoteHost() 用到
     */
    protected boolean enableLookups = false;


    /*
     * 是否启用了X-Powered-By响应头的生成 ？？？
     */
    protected boolean xpoweredBy = false;


    /**
     * 创建服务器监听连接的TCP端口号
     */
    protected int port = -1;


    /**
     * 当Tomcat在代理服务器上运行时用到的属性，
     * 给调用request.getServerName()Web应用程序设置代理服务器名称
     */
    protected String proxyName = null;


    /**
     * 当Tomcat在代理服务器上运行时用到的属性，
     * 给调用request.getServerPort()Web应用程序设置代理服务器监听端口
     */
    protected int proxyPort = 0;


    /**
     * 如果此连接器支持非SSL请求，并且收到匹配SSL传输的请求，则会将请求转发给指定端口
     */
    protected int redirectPort = 443;


    /**
     * 设置为您希望给调用request.getScheme()返回的协议的名称
     */
    protected String scheme = "http";


    /**
     * 设置为您希望给调用request.isSecure()返回表示请求是否使用安全协议（如HTTPS） ？
     */
    protected boolean secure = false;


    /**
     * 错误日志管理器
     */
    protected static final StringManager sm = StringManager.getManager(Connector.class);


    /**
     * 请求允许的最大Cookie数。值小于零表示没有限制。如果未指定，将使用默认值200。
     */
    private int maxCookieCount = 200;

    /**
     * 容器将自动解析的参数和值对的最大数量（GET加POST）。超出此限制的参数和值对将被忽略。值小于0表示没有限制。如果未指定，则使用默认值10000
     */
    protected int maxParameterCount = 10000;

    /**
     * POST的最大大小（以字节为单位）
     */
    protected int maxPostSize = 2 * 1024 * 1024;


    /**
     * 在FORM或CLIENT-CERT身份验证期间，容器将保存/缓冲的POST的最大字节数（以字节为单位）
     */
    protected int maxSavePostSize = 4 * 1024;

    /**
     * 解析Body的HTTP方法列表(以逗号分隔)，
     */
    protected String parseBodyMethods = "POST";

    /**
     *由{@link #parseBodyMethods}确定解析Body的HTTP方法集合
     */
    protected HashSet<String> parseBodyMethodsSet;


    /**
     * 属性设置true为使Tomcat使用收到请求的IP地址来确定要将请求发送到的主机。默认值为false
     */
    protected boolean useIPVHosts = false;


    /**
     * protocolHandler 默认处理类名称
     */
    protected String protocolHandlerClassName =
        "org.apache.coyote.http11.Http11NioProtocol";


    /**
     * protocolHandler 组件
     */
    protected final ProtocolHandler protocolHandler;


    /**
     * adapter组件（用于将Connector和Container适配起来的组件）
     */
    protected Adapter adapter = null;


    /**
     * 解码URI字节的字符编码。如果未指定，将使用UTF-8
     * @deprecated 这将在9.0.x之后删除
     */
    @Deprecated
    protected String URIEncoding = null;



    @Deprecated
    protected String URIEncodingLower = null;



    private Charset uriCharset = StandardCharsets.UTF_8;


    /**
     * contentType中指定的编码是否应该用于URI查询参数，而不是使用URIEncoding，默认为false
     */
    protected boolean useBodyEncodingForURI = false;


    /**
     *  用于替换设置到protocolHandler的属性名称
     */
    protected static final HashMap<String,String> replacements = new HashMap<>();
    static {
        replacements.put("acceptCount", "backlog");
        replacements.put("connectionLinger", "soLinger");
        replacements.put("connectionTimeout", "soTimeout");
        replacements.put("rootFile", "rootfile");
    }


    // ------------------------------------------------------------- Properties

    /**
     * 从protocolHandler返回一个属性值。
     */
    public Object getProperty(String name) {
        String repl = name;
        if (replacements.get(name) != null) {
            repl = replacements.get(name);
        }
        return IntrospectionUtils.getProperty(protocolHandler, repl);
    }


    /**
     * 设置protocolHandler一个属性的值
     */
    public boolean setProperty(String name, String value) {
        String repl = name;
        if (replacements.get(name) != null) {
            repl = replacements.get(name);
        }
        return IntrospectionUtils.setProperty(protocolHandler, repl, value);
    }


    /**
     * 从protocolHandler返回一个属性值。
     */
    public Object getAttribute(String name) {
        return getProperty(name);
    }


    /**
     * 设置protocolHandler一个属性的值
     */
    public void setAttribute(String name, Object value) {
        setProperty(name, String.valueOf(value));
    }



    public Service getService() {
        return this.service;
    }


    public void setService(Service service) {
        this.service = service;
    }


    public boolean getAllowTrace() {
        return this.allowTrace;
    }


    public void setAllowTrace(boolean allowTrace) {
        this.allowTrace = allowTrace;
        setProperty("allowTrace", String.valueOf(allowTrace));
    }


    public long getAsyncTimeout() {
        return asyncTimeout;
    }


    public void setAsyncTimeout(long asyncTimeout) {
        this.asyncTimeout= asyncTimeout;
        setProperty("asyncTimeout", String.valueOf(asyncTimeout));
    }


    public boolean getEnableLookups() {
        return this.enableLookups;
    }


    public void setEnableLookups(boolean enableLookups) {
        this.enableLookups = enableLookups;
        setProperty("enableLookups", String.valueOf(enableLookups));
    }


    public int getMaxCookieCount() {
        return maxCookieCount;
    }


    public void setMaxCookieCount(int maxCookieCount) {
        this.maxCookieCount = maxCookieCount;
    }


    public int getMaxParameterCount() {
        return maxParameterCount;
    }


    public void setMaxParameterCount(int maxParameterCount) {
        this.maxParameterCount = maxParameterCount;
        setProperty("maxParameterCount", String.valueOf(maxParameterCount));
    }


    public int getMaxPostSize() {
        return maxPostSize;
    }


    public void setMaxPostSize(int maxPostSize) {
        this.maxPostSize = maxPostSize;
        setProperty("maxPostSize", String.valueOf(maxPostSize));
    }


    public int getMaxSavePostSize() {
        return maxSavePostSize;
    }


    public void setMaxSavePostSize(int maxSavePostSize) {
        this.maxSavePostSize = maxSavePostSize;
        setProperty("maxSavePostSize", String.valueOf(maxSavePostSize));
    }


    public String getParseBodyMethods() {
        return this.parseBodyMethods;
    }


    public void setParseBodyMethods(String methods) {

        HashSet<String> methodSet = new HashSet<>();

        if (null != methods) {
            methodSet.addAll(Arrays.asList(methods.split("\\s*,\\s*")));
        }

        if (methodSet.contains("TRACE")) {
            throw new IllegalArgumentException(sm.getString("coyoteConnector.parseBodyMethodNoTrace"));
        }

        this.parseBodyMethods = methods;
        this.parseBodyMethodsSet = methodSet;
        setProperty("parseBodyMethods", methods);
    }


    protected boolean isParseBodyMethod(String method) {
        return parseBodyMethodsSet.contains(method);
    }


    public int getPort() {
        return this.port;
    }


    public void setPort(int port) {
        this.port = port;
        setProperty("port", String.valueOf(port));
    }


    public int getLocalPort() {
        return ((Integer) getProperty("localPort")).intValue();
    }


    /**
     * 返回Connection 内部处理协议名称
     */
    public String getProtocol() {
        if (("org.apache.coyote.http11.Http11NioProtocol".equals(getProtocolHandlerClassName()) &&
                    (!AprLifecycleListener.isAprAvailable() || !AprLifecycleListener.getUseAprConnector())) ||
                "org.apache.coyote.http11.Http11AprProtocol".equals(getProtocolHandlerClassName()) &&
                    AprLifecycleListener.getUseAprConnector()) {
            return "HTTP/1.1";
        } else if (("org.apache.coyote.ajp.AjpNioProtocol".equals(getProtocolHandlerClassName()) &&
                    (!AprLifecycleListener.isAprAvailable() || !AprLifecycleListener.getUseAprConnector())) ||
                "org.apache.coyote.ajp.AjpAprProtocol".equals(getProtocolHandlerClassName()) &&
                    AprLifecycleListener.getUseAprConnector()) {
            return "AJP/1.3";
        }
        return getProtocolHandlerClassName();
    }


    /**
     * 设置协议的处理类
     * @param protocol 协议名称
     */
    @Deprecated
    public void setProtocol(String protocol) {

        boolean aprConnector = AprLifecycleListener.isAprAvailable() &&
                AprLifecycleListener.getUseAprConnector();

        if ("HTTP/1.1".equals(protocol) || protocol == null) {
            if (aprConnector) {
                setProtocolHandlerClassName("org.apache.coyote.http11.Http11AprProtocol");
            } else {
                setProtocolHandlerClassName("org.apache.coyote.http11.Http11NioProtocol");
            }
        } else if ("AJP/1.3".equals(protocol)) {
            if (aprConnector) {
                setProtocolHandlerClassName("org.apache.coyote.ajp.AjpAprProtocol");
            } else {
                setProtocolHandlerClassName("org.apache.coyote.ajp.AjpNioProtocol");
            }
        } else {
            setProtocolHandlerClassName(protocol);
        }
    }


    public String getProtocolHandlerClassName() {
        return this.protocolHandlerClassName;
    }


    @Deprecated
    public void setProtocolHandlerClassName(String protocolHandlerClassName) {
        this.protocolHandlerClassName = protocolHandlerClassName;
    }



    public ProtocolHandler getProtocolHandler() {
        return this.protocolHandler;
    }


    public String getProxyName() {
        return this.proxyName;
    }


    public void setProxyName(String proxyName) {

        if(proxyName != null && proxyName.length() > 0) {
            this.proxyName = proxyName;
        } else {
            this.proxyName = null;
        }
        setProperty("proxyName", this.proxyName);
    }


    public int getProxyPort() {
        return this.proxyPort;
    }


    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
        setProperty("proxyPort", String.valueOf(proxyPort));
    }



    public int getRedirectPort() {
        return this.redirectPort;
    }



    public void setRedirectPort(int redirectPort) {
        this.redirectPort = redirectPort;
        setProperty("redirectPort", String.valueOf(redirectPort));
    }



    public String getScheme() {
        return this.scheme;
    }



    public void setScheme(String scheme) {
        this.scheme = scheme;
    }



    public boolean getSecure() {
        return this.secure;
    }



    public void setSecure(boolean secure) {
        this.secure = secure;
        setProperty("secure", Boolean.toString(secure));
    }



    public String getURIEncoding() {
        return uriCharset.name();
    }


    @Deprecated
    public String getURIEncodingLower() {
        return uriCharset.name().toLowerCase(Locale.ENGLISH);
    }


    public Charset getURICharset() {
        return uriCharset;
    }


    public void setURIEncoding(String URIEncoding) {
        try {
            uriCharset = B2CConverter.getCharset(URIEncoding);
        } catch (UnsupportedEncodingException e) {
            log.warn(sm.getString("coyoteConnector.invalidEncoding",
                    URIEncoding, uriCharset.name()), e);
        }
        setProperty("uRIEncoding", URIEncoding);
    }


    public boolean getUseBodyEncodingForURI() {
        return this.useBodyEncodingForURI;
    }


    public void setUseBodyEncodingForURI(boolean useBodyEncodingForURI) {
        this.useBodyEncodingForURI = useBodyEncodingForURI;
        setProperty("useBodyEncodingForURI", String.valueOf(useBodyEncodingForURI));
    }

    public boolean getXpoweredBy() {
        return xpoweredBy;
    }


    public void setXpoweredBy(boolean xpoweredBy) {
        this.xpoweredBy = xpoweredBy;
        setProperty("xpoweredBy", String.valueOf(xpoweredBy));
    }


    public void setUseIPVHosts(boolean useIPVHosts) {
        this.useIPVHosts = useIPVHosts;
        setProperty("useIPVHosts", String.valueOf(useIPVHosts));
    }


    public boolean getUseIPVHosts() {
        return useIPVHosts;
    }


    public String getExecutorName() {
        Object obj = protocolHandler.getExecutor();
        if (obj instanceof org.apache.catalina.Executor) {
            return ((org.apache.catalina.Executor) obj).getName();
        }
        return "Internal";
    }


    /**
     * 给protocolHandler添加一个SSL配置
     */
    public void addSslHostConfig(SSLHostConfig sslHostConfig) {
        protocolHandler.addSslHostConfig(sslHostConfig);
    }


    /**
     * 查找protocolHandler所有SSL配置
     */
    public SSLHostConfig[] findSslHostConfigs() {
        return protocolHandler.findSslHostConfigs();
    }


    /**
     * 添加一个协议升级
     */
    public void addUpgradeProtocol(UpgradeProtocol upgradeProtocol) {
        protocolHandler.addUpgradeProtocol(upgradeProtocol);
    }


    public UpgradeProtocol[] findUpgradeProtocols() {
        return protocolHandler.findUpgradeProtocols();
    }


    // --------------------------------------------------------- Public Methods

    /**
     * 创建一个Tomcat Request对象
     */
    public Request createRequest() {

        Request request = new Request();
        request.setConnector(this);
        return (request);

    }


    /**
     * 创建一个Tomcat Response对象
     */
    public Response createResponse() {

        Response response = new Response();
        response.setConnector(this);
        return (response);

    }


    /**
     * 创建当前主机JMX Bean ObjectName中属性列表
     */
    protected String createObjectNameKeyProperties(String type) {

        Object addressObj = getProperty("address");

        StringBuilder sb = new StringBuilder("type=");
        sb.append(type);
        sb.append(",port=");
        int port = getPort();
        if (port > 0) {
            sb.append(port);
        } else {
            sb.append("auto-");
            sb.append(getProperty("nameIndex"));
        }
        String address = "";
        if (addressObj instanceof InetAddress) {
            address = ((InetAddress) addressObj).getHostAddress();
        } else if (addressObj != null) {
            address = addressObj.toString();
        }
        if (address.length() > 0) {
            sb.append(",address=");
            sb.append(ObjectName.quote(address));
        }
        return sb.toString();
    }


    /**
     * 暂停连接器。
     */
    public void pause() {
        try {
            protocolHandler.pause();
        } catch (Exception e) {
            log.error(sm.getString("coyoteConnector.protocolHandlerPauseFailed"), e);
        }
    }


    /**
     * 恢复连接器。
     */
    public void resume() {
        try {
            protocolHandler.resume();
        } catch (Exception e) {
            log.error(sm.getString("coyoteConnector.protocolHandlerResumeFailed"), e);
        }
    }


    /**
     * 组件初始化模板方法实现
     */
    @Override
    protected void initInternal() throws LifecycleException {

        super.initInternal();

        /** 实例化adapter设置给protocolHandler组件 **/
        adapter = new CoyoteAdapter(this);
        protocolHandler.setAdapter(adapter);

        /** 解析设置parseBodyMethods到parseBodyMethodsSet集合  **/
        if (null == parseBodyMethodsSet) {
            setParseBodyMethods(getParseBodyMethods());
        }

        if (protocolHandler.isAprRequired() && !AprLifecycleListener.isAprAvailable()) {
            throw new LifecycleException(sm.getString("coyoteConnector.protocolHandlerNoApr",
                    getProtocolHandlerClassName()));
        }
        if (AprLifecycleListener.isAprAvailable() && AprLifecycleListener.getUseOpenSSL() &&
                protocolHandler instanceof AbstractHttp11JsseProtocol) {
            AbstractHttp11JsseProtocol<?> jsseProtocolHandler =
                    (AbstractHttp11JsseProtocol<?>) protocolHandler;
            if (jsseProtocolHandler.isSSLEnabled() &&
                    jsseProtocolHandler.getSslImplementationName() == null) {
                // OpenSSL is compatible with the JSSE configuration, so use it if APR is available
                jsseProtocolHandler.setSslImplementationName(OpenSSLImplementation.class.getName());
            }
        }

        /** 初始化protocolHandler **/
        try {
            protocolHandler.init();
        } catch (Exception e) {
            throw new LifecycleException(
                    sm.getString("coyoteConnector.protocolHandlerInitializationFailed"), e);
        }
    }


    /**
     * 组件启动模板方法实现
     */
    @Override
    protected void startInternal() throws LifecycleException {

        // Validate settings before starting
        if (getPort() < 0) {
            throw new LifecycleException(sm.getString(
                    "coyoteConnector.invalidPort", Integer.valueOf(getPort())));
        }

        /** 设置当前组件状态为STARTING **/
        setState(LifecycleState.STARTING);

        /** 启动protocolHandler **/
        try {
            protocolHandler.start();
        } catch (Exception e) {
            throw new LifecycleException(
                    sm.getString("coyoteConnector.protocolHandlerStartFailed"), e);
        }
    }


    /**
     * 组件停止模板方法实现
     */
    @Override
    protected void stopInternal() throws LifecycleException {
        /** 设置当前组件状态为STOPPING **/
        setState(LifecycleState.STOPPING);

        /** 停止protocolHandler **/
        try {
            protocolHandler.stop();
        } catch (Exception e) {
            throw new LifecycleException(
                    sm.getString("coyoteConnector.protocolHandlerStopFailed"), e);
        }
    }

    /**
     * 组件销毁模板方法实现
     */
    @Override
    protected void destroyInternal() throws LifecycleException {
        /** 销毁protocolHandler **/
        try {
            protocolHandler.destroy();
        } catch (Exception e) {
            throw new LifecycleException(
                    sm.getString("coyoteConnector.protocolHandlerDestroyFailed"), e);
        }

        /** 从Service中删除当前Connector组件 **/
        if (getService() != null) {
            getService().removeConnector(this);
        }

        super.destroyInternal();
    }



    @Override
    public String toString() {
        // Not worth caching this right now
        StringBuilder sb = new StringBuilder("Connector[");
        sb.append(getProtocol());
        sb.append('-');
        int port = getPort();
        if (port > 0) {
            sb.append(port);
        } else {
            sb.append("auto-");
            sb.append(getProperty("nameIndex"));
        }
        sb.append(']');
        return sb.toString();
    }


    // -------------------- JMX registration  --------------------
    /**
     * ObjectName 表示注册到JMX中Bean所对应的对象名称
     *
     * StringBuilder name = new StringBuilder(getDomain());
     * name.append(':');
     * name.append(objectNameKeyProperties);
     * ObjectName on = new ObjectName(name.toString());
     *
     * ObjectName名称组成由
     * 域名空间：对象属性组成
     * getDomain():getObjectNameKeyProperties()
     *
     * 当前方法是getDomain()方法扩展子类实现，该方法父类LifecycleMBeanBase模板方法实现，返回域名空间
     * 获取子组件Service 组件域名空间作为自己域名空间
     */
    @Override
    protected String getDomainInternal() {
        Service s = getService();
        if (s == null) {
            return null;
        } else {
            return service.getDomain();
        }
    }

    /**
     * ObjectName 表示注册到JMX中Bean所对应的对象名称
     *
     * StringBuilder name = new StringBuilder(getDomain());
     * name.append(':');
     * name.append(objectNameKeyProperties);
     * ObjectName on = new ObjectName(name.toString());
     *
     * ObjectName名称组成由
     * 域名空间：对象属性集合
     * getDomain():getObjectNameKeyProperties()
     * 该方法父类LifecycleMBeanBase模板方法实现，返回对象属性集合
     */
    @Override
    protected String getObjectNameKeyProperties() {
        return createObjectNameKeyProperties("Connector");
    }

}
