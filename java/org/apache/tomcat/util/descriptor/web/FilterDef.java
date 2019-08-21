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
import java.util.HashMap;
import java.util.Map;

import javax.servlet.Filter;

import org.apache.tomcat.util.res.StringManager;


/**
 * ����Servlet�淶��Filter
 */
public class FilterDef implements Serializable {

    private static final long serialVersionUID = 1L;

    /** ��־�������� **/
    private static final StringManager sm =
        StringManager.getManager(Constants.PACKAGE_NAME);

    /**
     *  Filter����<filter-name></filter-name>
     */
    private String filterName = null;

    /**
     * Filter��������  <display-name></display-name>
     */
    private String displayName = null;

    /**
     * Filter������ <description></description>
     */
    private String description = null;

    /**
     * servelt�ж���Filterʵ������
     */
    private transient Filter filter = null;

    /**
     * ʵ��FilterJava�����ȫ�޶���
     */
    private String filterClass = null;

    /**
     * Filter��ʼ������
     * <init-param></init-param>
     * <init-value></init-value>
     */
    private final Map<String, String> parameters = new HashMap<>();

    /**
     * Filter�����Ĵ�ͼ��
     */
    private String largeIcon = null;

    /**
     * Filter������Сͼ��
     */
    private String smallIcon = null;

    public String getDescription() {
        return (this.description);
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDisplayName() {
        return (this.displayName);
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Filter getFilter() {
        return filter;
    }

    public void setFilter(Filter filter) {
        this.filter = filter;
    }

    public String getFilterClass() {
        return (this.filterClass);
    }

    public void setFilterClass(String filterClass) {
        this.filterClass = filterClass;
    }

    public String getFilterName() {
        return (this.filterName);
    }

    public void setFilterName(String filterName) {
        if (filterName == null || filterName.equals("")) {
            throw new IllegalArgumentException(
                    sm.getString("filterDef.invalidFilterName", filterName));
        }
        this.filterName = filterName;
    }

    public String getLargeIcon() {
        return (this.largeIcon);
    }

    public void setLargeIcon(String largeIcon) {
        this.largeIcon = largeIcon;
    }

    public Map<String, String> getParameterMap() {
        return (this.parameters);
    }

    public String getSmallIcon() {
        return (this.smallIcon);
    }

    public void setSmallIcon(String smallIcon) {
        this.smallIcon = smallIcon;
    }

    private String asyncSupported = null;

    public String getAsyncSupported() {
        return asyncSupported;
    }

    public void setAsyncSupported(String asyncSupported) {
        this.asyncSupported = asyncSupported;
    }

    /**
     * Filter��ӳ�ʼ������
     * @param name ����
     * @param value ֵ
     */
    public void addInitParameter(String name, String value) {
        if (parameters.containsKey(name)) {
            return;
        }
        parameters.put(name, value);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("FilterDef[");
        sb.append("filterName=");
        sb.append(this.filterName);
        sb.append(", filterClass=");
        sb.append(this.filterClass);
        sb.append("]");
        return (sb.toString());
    }
}
