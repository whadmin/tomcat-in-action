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

import java.beans.PropertyChangeListener;

/**
 * Loader������ʾһ���������ʵ��
 * Loader��Context����������Ƽ������ļ����ڹ����Ĵ洢���У�
 */
public interface Loader {


    /**
     * ִ�ж���������Ҫ�������¼������ļ���
     */
    public void backgroundProcess();


    /**
     * ��������������Ҫʹ�õ�Java���������
     */
    public ClassLoader getClassLoader();


    /**
     * ���ص�ǰ���Context�����
     */
    public Context getContext();


    /**
     * ���õ�ǰ���Context�����
     */
    public void setContext(Context context);


    /**
     * �Ƿ�����ʹ�ô��������������Ĭ�����������
     */
    public boolean getDelegate();


    /**
     * �����Ƿ�����ʹ�ô��������������Ĭ�����������
     */
    public void setDelegate(boolean delegate);


    /**
     * �����Ƿ����ȼ��ػ���
     * Loader�����ʱ���񴥷�����modified����������Ѿ����ص���Դ�Ƿ����޸�/����/ɾ��,���������Դ�ɱ������StandardContext���reload;
     */
    public boolean getReloadable();


    /**
     * �����Ƿ����ȼ��ػ���
     * Loader�����ʱ���񴥷�����modified����������Ѿ����ص���Դ�Ƿ����޸�/����/ɾ��,���������Դ�ɱ������StandardContext���reload;
     */
    public void setReloadable(boolean reloadable);


    /**
     * ����Ѿ����ص���Դ�Ƿ����޸�/����/ɾ��,
     * Loaderʵ���������ʱ���񴥷����ã����������Դ�ɱ������StandardContext���reload;
     */
    public boolean modified();


    /**
     * ����ǰ�������ɾ�����Ա��������
     */
    public void removePropertyChangeListener(PropertyChangeListener listener);


    /**
     * ����ǰ�������������Ա��������
     */
    public void addPropertyChangeListener(PropertyChangeListener listener);
}
