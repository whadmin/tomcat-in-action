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
package org.apache.catalina;

import java.util.EventObject;

/**
 * 通过监听器发送的生命周期事件
 */
public final class LifecycleEvent extends EventObject {

    private static final long serialVersionUID = 1L;

    public LifecycleEvent(Lifecycle lifecycle, String type, Object data) {
        super(lifecycle);
        this.type = type;
        this.data = data;
    }

    /**
     * 与此事件关联的事件数据。
     */
    private final Object data;


    /**
     *  事件类型
     */
    private final String type;


    /**
     * 返回触发事件数据
     */
    public Object getData() {
        return data;
    }


    /**
     * 返回触发事件的组件
     */
    public Lifecycle getLifecycle() {
        return (Lifecycle) getSource();
    }


    /**
     * 返回触发事件类型
     */
    public String getType() {
        return this.type;
    }
}
