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


import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;

import org.apache.tomcat.util.ExceptionUtils;



/**
 * 字符映射帮助程序
 * 默认读取/org/apache/catalina/util/CharsetMapperDefault.properties
 */
public class CharsetMapper {

    /**
     * Default properties resource name.
     */
    public static final String DEFAULT_RESOURCE =
      "/org/apache/catalina/util/CharsetMapperDefault.properties";



    /**
     * 实例化CharsetMapper
     */
    public CharsetMapper() {
        this(DEFAULT_RESOURCE);
    }


    /**
     * 实例化CharsetMapper
     */
    public CharsetMapper(String name) {
        try (InputStream stream = this.getClass().getResourceAsStream(name)) {
            map.load(stream);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            throw new IllegalArgumentException(t.toString());
        }
    }


    private Properties map = new Properties();


    public String getCharset(Locale locale) {
        // Match full language_country_variant first, then language_country,
        // then language only
        String charset = map.getProperty(locale.toString());
        if (charset == null) {
            charset = map.getProperty(locale.getLanguage() + "_"
                    + locale.getCountry());
            if (charset == null) {
                charset = map.getProperty(locale.getLanguage());
            }
        }
        return (charset);
    }

    public void addCharsetMappingFromDeploymentDescriptor(String locale, String charset) {
        map.put(locale, charset);
    }


}
