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


package org.apache.tomcat.util.descriptor.web;

import java.io.Serializable;


/**
 * ����Servlet�涨��web.xml����Ӧ�ó��������
 *  <context-param>
 *             <param-name>url</param-name>
 *             <param-value>jdbc:mysql://localhost:3306/test</param-value>
 * </context-param>
 */
public class ApplicationParameter implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * context��������
     */
    private String name = null;

    /**
     * context����ֵ
     */
    private String value = null;

    /**
     * context ������
     */
    private String description = null;

    /**
     * �Ƿ�����������Ϣ
     */
    private boolean override = true;

    public String getDescription() {
        return (this.description);
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getName() {
        return (this.name);
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean getOverride() {
        return (this.override);
    }

    public void setOverride(boolean override) {
        this.override = override;
    }

    public String getValue() {
        return (this.value);
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("ApplicationParameter[");
        sb.append("name=");
        sb.append(name);
        if (description != null) {
            sb.append(", description=");
            sb.append(description);
        }
        sb.append(", value=");
        sb.append(value);
        sb.append(", override=");
        sb.append(override);
        sb.append("]");
        return (sb.toString());

    }


}
