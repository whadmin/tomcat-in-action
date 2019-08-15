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


package org.apache.catalina.startup;


import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.RuleSetBase;


/**
 * 解析Realm标签添加到StandardEngine属性中
 */
@SuppressWarnings("deprecation")
public class RealmRuleSet extends RuleSetBase {

    private static final int MAX_NESTED_REALM_LEVELS = Integer.getInteger(
            "org.apache.catalina.startup.RealmRuleSet.MAX_NESTED_REALM_LEVELS",
            3).intValue();

    protected final String prefix;

    public RealmRuleSet() {
        this("");
    }


    public RealmRuleSet(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public void addRuleInstances(Digester digester) {
        StringBuilder pattern = new StringBuilder(prefix);
        for (int i = 0; i < MAX_NESTED_REALM_LEVELS; i++) {
            if (i > 0) {
                pattern.append('/');
            }
            pattern.append("Realm");
            addRuleInstances(digester, pattern.toString(), i == 0 ? "setRealm" : "addRealm");
        }
    }

    private void addRuleInstances(Digester digester, String pattern, String methodName) {
        digester.addObjectCreate(pattern, null /* MUST be specified in the element */,
                "className");
        digester.addSetProperties(pattern);
        digester.addSetNext(pattern, methodName, "org.apache.catalina.Realm");
        digester.addRuleSet(new CredentialHandlerRuleSet(pattern + "/"));
    }
}
