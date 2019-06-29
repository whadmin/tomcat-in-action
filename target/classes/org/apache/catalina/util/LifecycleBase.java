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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.LifecycleState;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;


/**
 * 组件生命周期基类，用来处理生命周期装备变更流程和监听器管理。、
 * 状态发送变更，做状态检验，变更，通知监听器触发事件。
 */
public abstract class LifecycleBase implements Lifecycle {

    private static final Log log = LogFactory.getLog(LifecycleBase.class);


    /** 管理打印日志 **/
    private static final StringManager sm = StringManager.getManager(LifecycleBase.class);


    /**
     * 管理当前组件生命周期监听器列表
     */
    private final List<LifecycleListener> lifecycleListeners = new CopyOnWriteArrayList<>();


    /**
     * 实力化组件默认的状态
     */
    private volatile LifecycleState state = LifecycleState.NEW;


    /**
     * 给当前组件添加一个生命周期监听器
     */
    @Override
    public void addLifecycleListener(LifecycleListener listener) {
        lifecycleListeners.add(listener);
    }


    /**
     * 获取当前组件所有生命周期监听器
     */
    @Override
    public LifecycleListener[] findLifecycleListeners() {
        return lifecycleListeners.toArray(new LifecycleListener[0]);
    }


    /**
     * 移除当前组件一个生命周期监听器
     */
    @Override
    public void removeLifecycleListener(LifecycleListener listener) {
        lifecycleListeners.remove(listener);
    }


    /**
     * 触发一个生命周期事件，通知所有生命周期监听器触发
     *
     * @param type  Event type
     * @param data  Data associated with event.
     */
    protected void fireLifecycleEvent(String type, Object data) {
        LifecycleEvent event = new LifecycleEvent(this, type, data);
        for (LifecycleListener listener : lifecycleListeners) {
            listener.lifecycleEvent(event);
        }
    }


    /**
     *  组件初始化动作，所有组件通用操作
     *   1 检查校验当前组件状态是否能够初始化
     *   2 修改当前的状态从 NEW-->INITIALIZING
     *   3 调用每个组件模板方法实现完成初始化动作
     *   4 修改当前的状态从 INITIALIZING-->INITIALIZED
     */
    @Override
    public final synchronized void init() throws LifecycleException {
        /** 非NEW状态，不允许调用init()方法 **/
        if (!state.equals(LifecycleState.NEW)) {
            /** 从sm获取"lifecycleBase.invalidTransition"属性对应日志格式，抛出LifecycleException异常 **/
            invalidTransition(Lifecycle.BEFORE_INIT_EVENT);
        }

        try {
            /** 初始化逻辑之前，先将状态变更为`INITIALIZING` **/
            setStateInternal(LifecycleState.INITIALIZING, null, false);
            /** 初始化组件，该方法为一个abstract模板方法，需要组件自行实现  **/
            initInternal();
            /** 初始化完成之后，状态变更为`INITIALIZED`  **/
            setStateInternal(LifecycleState.INITIALIZED, null, false);
        }
        /** 初始化的过程中，可能会有异常抛出，这时需要捕获异常，并将状态变更为`FAILED` **/
        catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            setStateInternal(LifecycleState.FAILED, null, false);
            throw new LifecycleException(
                    sm.getString("lifecycleBase.initFail",toString()), t);
        }
    }


    /**
     * 初始化模板方法
     */
    protected abstract void initInternal() throws LifecycleException;

    /**
     * 组件初始化动作，所有组件通用操作
     */
    @Override
    public final synchronized void start() throws LifecycleException {
        /** 组件状态为`STARTING_PREP`、`STARTING`和`STARTED时，将忽略start()逻辑 **/
        if (LifecycleState.STARTING_PREP.equals(state) || LifecycleState.STARTING.equals(state) ||
                LifecycleState.STARTED.equals(state)) {

            if (log.isDebugEnabled()) {
                Exception e = new LifecycleException();
                log.debug(sm.getString("lifecycleBase.alreadyStarted", toString()), e);
            } else if (log.isInfoEnabled()) {
                log.info(sm.getString("lifecycleBase.alreadyStarted", toString()));
            }

            return;
        }
        /** 组件当前状态为`NEW`时，先执行init()方法 **/
        if (state.equals(LifecycleState.NEW)) {
            init();
        }
        /** 组件当前状态为`FAILED`时，执行stop()方法 **/
        else if (state.equals(LifecycleState.FAILED)) {
            stop();
        }
        /** 组件当前状态不是`INITIALIZED`和`STOPPED`时，则说明是非法的操作，抛出异常**/
        else if (!state.equals(LifecycleState.INITIALIZED) &&
                !state.equals(LifecycleState.STOPPED)) {
            /** 从sm获取"lifecycleBase.invalidTransition"属性对应日志格式，抛出LifecycleException异常 **/
            invalidTransition(Lifecycle.BEFORE_START_EVENT);
        }

        try {
            /** 启动逻辑之前，先将状态变更为`STARTING_PREP` **/
            setStateInternal(LifecycleState.STARTING_PREP, null, false);
            /** 启动组件，该方法为一个abstract模板方法，需要组件自行实现   **/
            startInternal();
            /** 如果启动组件发送异常状态被更新为'FAILED',调用stop() **/
            if (state.equals(LifecycleState.FAILED)) {
                // This is a 'controlled' failure. The component put itself into the
                // FAILED state so call stop() to complete the clean-up.
                stop();
            }
            /** 如果启动组件后状态未被更新为STARTING，抛出异常 **/
            else if (!state.equals(LifecycleState.STARTING)) {
                /** 从sm获取"lifecycleBase.invalidTransition"属性对应日志格式，抛出LifecycleException异常 **/
                invalidTransition(Lifecycle.AFTER_START_EVENT);
            }
            /** 启动完成，将状态变更为`STARTED` **/
            else {
                setStateInternal(LifecycleState.STARTED, null, false);
            }
        }
        /** 初始化的过程中，可能会有异常抛出，这时需要捕获异常，并将状态变更为`FAILED` **/
        catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            setStateInternal(LifecycleState.FAILED, null, false);
            throw new LifecycleException(sm.getString("lifecycleBase.startFail", toString()), t);
        }
    }


    /**
     * 启动模板方法
     */
    protected abstract void startInternal() throws LifecycleException;


    /**
     * 组件初始化动作，所有组件通用操作
     */
    @Override
    public final synchronized void stop() throws LifecycleException {

        if (LifecycleState.STOPPING_PREP.equals(state) || LifecycleState.STOPPING.equals(state) ||
                LifecycleState.STOPPED.equals(state)) {

            if (log.isDebugEnabled()) {
                Exception e = new LifecycleException();
                log.debug(sm.getString("lifecycleBase.alreadyStopped", toString()), e);
            } else if (log.isInfoEnabled()) {
                log.info(sm.getString("lifecycleBase.alreadyStopped", toString()));
            }

            return;
        }

        if (state.equals(LifecycleState.NEW)) {
            state = LifecycleState.STOPPED;
            return;
        }

        if (!state.equals(LifecycleState.STARTED) && !state.equals(LifecycleState.FAILED)) {
            invalidTransition(Lifecycle.BEFORE_STOP_EVENT);
        }

        try {
            if (state.equals(LifecycleState.FAILED)) {
                // Don't transition to STOPPING_PREP as that would briefly mark the
                // component as available but do ensure the BEFORE_STOP_EVENT is
                // fired
                fireLifecycleEvent(BEFORE_STOP_EVENT, null);
            } else {
                setStateInternal(LifecycleState.STOPPING_PREP, null, false);
            }

            stopInternal();

            // Shouldn't be necessary but acts as a check that sub-classes are
            // doing what they are supposed to.
            if (!state.equals(LifecycleState.STOPPING) && !state.equals(LifecycleState.FAILED)) {
                invalidTransition(Lifecycle.AFTER_STOP_EVENT);
            }

            setStateInternal(LifecycleState.STOPPED, null, false);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            setStateInternal(LifecycleState.FAILED, null, false);
            throw new LifecycleException(sm.getString("lifecycleBase.stopFail",toString()), t);
        } finally {
            if (this instanceof Lifecycle.SingleUse) {
                // Complete stop process first
                setStateInternal(LifecycleState.STOPPED, null, false);
                destroy();
            }
        }
    }


    /**
     * Sub-classes must ensure that the state is changed to
     * {@link LifecycleState#STOPPING} during the execution of this method.
     * Changing state will trigger the {@link Lifecycle#STOP_EVENT} event.
     *
     * @throws LifecycleException Stop error occurred
     */
    protected abstract void stopInternal() throws LifecycleException;


    @Override
    public final synchronized void destroy() throws LifecycleException {
        if (LifecycleState.FAILED.equals(state)) {
            try {
                // Triggers clean-up
                stop();
            } catch (LifecycleException e) {
                // Just log. Still want to destroy.
                log.warn(sm.getString(
                        "lifecycleBase.destroyStopFail", toString()), e);
            }
        }

        if (LifecycleState.DESTROYING.equals(state) ||
                LifecycleState.DESTROYED.equals(state)) {

            if (log.isDebugEnabled()) {
                Exception e = new LifecycleException();
                log.debug(sm.getString("lifecycleBase.alreadyDestroyed", toString()), e);
            } else if (log.isInfoEnabled() && !(this instanceof Lifecycle.SingleUse)) {
                // Rather than have every component that might need to call
                // destroy() check for SingleUse, don't log an info message if
                // multiple calls are made to destroy()
                log.info(sm.getString("lifecycleBase.alreadyDestroyed", toString()));
            }

            return;
        }

        if (!state.equals(LifecycleState.STOPPED) &&
                !state.equals(LifecycleState.FAILED) &&
                !state.equals(LifecycleState.NEW) &&
                !state.equals(LifecycleState.INITIALIZED)) {
            invalidTransition(Lifecycle.BEFORE_DESTROY_EVENT);
        }

        try {
            setStateInternal(LifecycleState.DESTROYING, null, false);
            destroyInternal();
            setStateInternal(LifecycleState.DESTROYED, null, false);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            setStateInternal(LifecycleState.FAILED, null, false);
            throw new LifecycleException(
                    sm.getString("lifecycleBase.destroyFail",toString()), t);
        }
    }


    protected abstract void destroyInternal() throws LifecycleException;

    /**
     * 获取当前组件状态
     */
    @Override
    public LifecycleState getState() {
        return state;
    }


    /**
     * 获取当前组件状态名称
     */
    @Override
    public String getStateName() {
        return getState().toString();
    }


    /**
     * 更新当前组件的状态
     */
    protected synchronized void setState(LifecycleState state)
            throws LifecycleException {
        setStateInternal(state, null, true);
    }


    /**
     * 更新当前组件的状态
     */
    protected synchronized void setState(LifecycleState state, Object data)
            throws LifecycleException {
        setStateInternal(state, data, true);
    }

    /**
     * 更新当前组件的状态
     * @param state  更新的状态
     * @param data   触发事件数据
     * @param check  是否对组件状态更新做检查
     * @throws LifecycleException
     */
    private synchronized void setStateInternal(LifecycleState state,
            Object data, boolean check) throws LifecycleException {

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("lifecycleBase.setState", this, state));
        }

        if (check) {

            /** 检查当前状态是否为nul **/
            if (state == null) {
                /** 从sm获取当前日志格式，抛出LifecycleException异常 **/
                invalidTransition("null");
                return;
            }

            /** 检查状态变更逻辑是否错误 **/
            if (!(state == LifecycleState.FAILED ||
                    (this.state == LifecycleState.STARTING_PREP &&
                            state == LifecycleState.STARTING) ||
                    (this.state == LifecycleState.STOPPING_PREP &&
                            state == LifecycleState.STOPPING) ||
                    (this.state == LifecycleState.FAILED &&
                            state == LifecycleState.STOPPING))) {
                /** 从sm获取当前日志格式，抛出LifecycleException异常 **/
                invalidTransition(state.name());
            }
        }

        /** 更新当前组件状态 **/
        this.state = state;
        String lifecycleEvent = state.getLifecycleEvent();
        /** 获取更新状态需要触发的事件 **/
        if (lifecycleEvent != null) {
            fireLifecycleEvent(lifecycleEvent, data);
        }
    }

    /**
     * 从sm获取当前日志格式，抛出LifecycleException异常
     */
    private void invalidTransition(String type) throws LifecycleException {
        String msg = sm.getString("lifecycleBase.invalidTransition", type,
                toString(), state);
        throw new LifecycleException(msg);
    }
}
