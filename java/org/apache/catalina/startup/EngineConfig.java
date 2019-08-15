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


import org.apache.catalina.Engine;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;


/**
 * Startup event listener for a <b>Engine</b> that configures the properties
 * of that Engine, and the associated defined contexts.
 *
 * @author Craig R. McClanahan
 */
public class EngineConfig
    implements LifecycleListener {

    private static final Log log = LogFactory.getLog(EngineConfig.class);

    protected Engine engine = null;

    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


    @Override
    public void lifecycleEvent(LifecycleEvent event) {


        try {
            /** ��ȡ�����¼������engine **/
            engine = (Engine) event.getLifecycle();
        } catch (ClassCastException e) {
            log.error(sm.getString("engineConfig.cce", event.getLifecycle()), e);
            return;
        }
        /** �����ǰ�¼�START_EVENT������start()������ӡ������־ **/
        if (event.getType().equals(Lifecycle.START_EVENT))
            start();

        /** �����ǰ�¼�STOP_EVENT������stop()������ӡֹͣ��־ **/
        else if (event.getType().equals(Lifecycle.STOP_EVENT))
            stop();
    }

    protected void start() {
        if (engine.getLogger().isDebugEnabled())
            engine.getLogger().debug(sm.getString("engineConfig.start"));
    }


    protected void stop() {
        if (engine.getLogger().isDebugEnabled())
            engine.getLogger().debug(sm.getString("engineConfig.stop"));

    }
}
