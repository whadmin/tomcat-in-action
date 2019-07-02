/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.coyote;

import java.util.concurrent.Executor;

import org.apache.tomcat.util.net.SSLHostConfig;

/**
 * Abstract the protocol implementation, including threading, etc.
 * Processor is single threaded and specific to stream-based protocols,
 * will not fit Jk protocols like JNI.
 *
 * This is the main interface to be implemented by a coyote connector.
 * Adapter is the main interface to be implemented by a coyote servlet
 * container.
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 * @see Adapter
 */
public interface ProtocolHandler {

    /**
     * 设置adapter
     */
    public void setAdapter(Adapter adapter);


    /**
     * 返回adapter
     */
    public Adapter getAdapter();


    /**
     * 获取连接池 Executor组件
     */
    public Executor getExecutor();


    /**
     * 初始化
     */
    public void init() throws Exception;


    /**
     * 启动
     */
    public void start() throws Exception;


    /**
     * 暂停
     */
    public void pause() throws Exception;


    /**
     * 恢复
     */
    public void resume() throws Exception;


    /**
     * 停止
     */
    public void stop() throws Exception;


    /**
     * 销毁
     */
    public void destroy() throws Exception;


    /**
     * 关闭服务端连接
     */
    public void closeServerSocketGraceful();


    /**
     * 是否是请求APR请求
     */
    public boolean isAprRequired();


    /**
     * 这个ProtocolHandler是否支持sendfile
     */
    public boolean isSendfileSupported();


    /**
     * 添加SSLHostConfig
     */
    public void addSslHostConfig(SSLHostConfig sslHostConfig);
    public SSLHostConfig[] findSslHostConfigs();


    /**
     * 添加UpgradeProtocol
     */
    public void addUpgradeProtocol(UpgradeProtocol upgradeProtocol);


    /**
     * 添加UpgradeProtocol
     */
    public UpgradeProtocol[] findUpgradeProtocols();
}
