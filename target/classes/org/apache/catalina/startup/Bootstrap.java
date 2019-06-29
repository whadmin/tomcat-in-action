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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.catalina.Globals;
import org.apache.catalina.security.SecurityClassLoad;
import org.apache.catalina.startup.ClassLoaderFactory.Repository;
import org.apache.catalina.startup.ClassLoaderFactory.RepositoryType;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


/**
 * Bootstrap loader for Catalina.  This application constructs a class loader
 * for use in loading the Catalina internal classes (by accumulating all of the
 * JAR files found in the "server" directory under "catalina.home"), and
 * starts the regular execution of the container.  The purpose of this
 * roundabout approach is to keep the Catalina internal classes (and any
 * other classes they depend on, such as an XML parser) out of the system
 * class path and therefore not visible to application level classes.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
public final class Bootstrap {

    private static final Log log = LogFactory.getLog(Bootstrap.class);

    /**
     * Bootstrap实例的引用
     */
    private static Bootstrap daemon = null;

    /**
     * CATALINA_BASE表示tomcat工作目录(默认情况下等于按照目录)
     */
    private static final File catalinaBaseFile;

    /**
     *CATALINA_HOME表示tomcat安装目录
     */
    private static final File catalinaHomeFile;


    private static final Pattern PATH_PATTERN = Pattern.compile("(\".*?\")|(([^,])*)");


    /**
     *
     */
    static {
        // 获取当前用户目录
        // C:\work\project\tomcat8\apache-tomcat-8.5.39-src
        String userDir = System.getProperty("user.dir");

        // 第一步，从JAVA系统环境变量中获取catalina.home【tomcat安装目录路径】对应属性值
        // 如果存在则,以获取值构造临时homeFile文件对象
        String home = System.getProperty(Globals.CATALINA_HOME_PROP);
        File homeFile = null;

        if (home != null) {
            File f = new File(home);
            try {
                //使用当前文件绝对路径构造homeFile
                homeFile = f.getCanonicalFile();
            } catch (IOException ioe) {
                homeFile = f.getAbsoluteFile();
            }
        }

        //第二步，在第一步没获取的时候，在userDir目录下获取bootstrap.jar文件，
        //如果存在,则以userDir上级目录路径构造临时homeFile文件对象
        if (homeFile == null) {
            File bootstrapJar = new File(userDir, "bootstrap.jar");

            if (bootstrapJar.exists()) {
                File f = new File(userDir, "..");
                try {
                    //使用当前文件绝对路径构造homeFile
                    homeFile = f.getCanonicalFile();
                } catch (IOException ioe) {
                    homeFile = f.getAbsoluteFile();
                }
            }
        }

        //第三步，如果第一 二步都无法获取，这时我们直接将userDir路径构造临时homeFile文件对象
        if (homeFile == null) {
            // Second fall-back. Use current directory
            File f = new File(userDir);
            try {
                //使用当前文件绝对路径构造homeFile
                homeFile = f.getCanonicalFile();
            } catch (IOException ioe) {
                homeFile = f.getAbsoluteFile();
            }
        }

        //初始化catalinaHomeFile
        catalinaHomeFile = homeFile;

        // 重新设置catalina.home到Java环境系统属性
        System.setProperty(
                Globals.CATALINA_HOME_PROP, catalinaHomeFile.getPath());


        // 从JAVA系统环境变量中获取catalina.base【tomcat工作目录】属性值
        // 如果存在则以此路径构造临时文件对象baseFile，并初始化catalinaBaseFile=baseFile
        // 如果不存在则初始化初始化catalinaBaseFile=catalinaHomeFile
        String base = System.getProperty(Globals.CATALINA_BASE_PROP);
        if (base == null) {
            catalinaBaseFile = catalinaHomeFile;
        } else {
            File baseFile = new File(base);
            try {
                baseFile = baseFile.getCanonicalFile();
            } catch (IOException ioe) {
                baseFile = baseFile.getAbsoluteFile();
            }
            catalinaBaseFile = baseFile;
        }

        // 重新设置catalina.base到Java环境系统属性
        System.setProperty(
                Globals.CATALINA_BASE_PROP, catalinaBaseFile.getPath());
    }

    // -------------------------------------------------------------- Variables


    /**
     * catalina实例
     */
    private Object catalinaDaemon = null;

    /**
     * Common类加载器，负责加载Tomcat和Web应用都复用的类
     */
    ClassLoader commonLoader = null;

    /**
     * Catalina类加载器，负责加载Tomcat专用的类，而这些被加载的类在Web应用中将不可见
     */
    ClassLoader catalinaLoader = null;

    /**
     * Shared类加载器，负责加载Tomcat下所有的Web应用程序都复用的类，而这些被加载的类在Tomcat中将不可见
     */
    ClassLoader sharedLoader = null;


    /**
     * 初始化tomcat类加载器
     */
    private void initClassLoaders() {
        try {
            // 构造commonLoader，如果未创建成果的话，则使用应用程序类加载器作为commonLoader
            commonLoader = createClassLoader("common", null);
            if( commonLoader == null ) {
                commonLoader=this.getClass().getClassLoader();
            }
            // 构造catalinaLoader，其父加载器为commonLoader
            catalinaLoader = createClassLoader("server", commonLoader);
            // 构造sharedLoader，其父加载器为commonLoader
            sharedLoader = createClassLoader("shared", commonLoader);
        } catch (Throwable t) {
            handleThrowable(t);
            log.error("Class loader creation threw exception", t);
            System.exit(1);
        }
    }


    /**
     * 按照名称创建不同tomcat 类加载器
     * @param name
     * @param parent
     * @return
     * @throws Exception
     */
    private ClassLoader createClassLoader(String name, ClassLoader parent)
        throws Exception {
        //根据不同的类加载器从CatalinaProperties【引导Catalina配置】读取不同类加载加载class文件资源路径，多个路径,分隔
        //默认情况下引导Catalina配置放在tomcat工作目录/conf/catalina.properties
        //默认配置如下
        //common.loader="${catalina.base}/lib","${catalina.base}/lib/*.jar","${catalina.home}/lib","${catalina.home}/lib/*.jar"
        //server.loader=
        //shared.loader=
        String value = CatalinaProperties.getProperty(name + ".loader");
        if ((value == null) || (value.equals("")))
            return parent;

        //  替换资源路径字符串中属性遍历占位符，比如：${catalina.base}、${catalina.home}
        value = replace(value);

        // 定义一个列表，存放Repository
        // Repository 用来封装不同类型class资源
        List<Repository> repositories = new ArrayList<>();

        // 将多个路径,分隔字符转换为数组
        String[] repositoryPaths = getPaths(value);

        // 遍历repositoryPaths
        for (String repository : repositoryPaths) {
            // 检查资源路径是否为URL
            try {
                @SuppressWarnings("unused")
                URL url = new URL(repository);
                //创建一个Repository，类型为URL添加到repositories
                repositories.add(
                        new Repository(repository, RepositoryType.URL));
                continue;
            } catch (MalformedURLException e) {
                // Ignore
            }

            // 判断资源是否为某个目录下所有*.jar文件
            if (repository.endsWith("*.jar")) {
                repository = repository.substring
                    (0, repository.length() - "*.jar".length());

                //创建一个Repository，类型为GLOB添加到repositories
                repositories.add(
                        new Repository(repository, RepositoryType.GLOB));
            }
            // 判断资源是否为某个目录下.jar文件
            else if (repository.endsWith(".jar")) {
                //创建一个Repository，类型为JAR添加到repositories
                repositories.add(
                        new Repository(repository, RepositoryType.JAR));
            }
            // 判断资源是否目录
            else {
                //创建一个Repository，类型为目录添加到repositories
                repositories.add(
                        new Repository(repository, RepositoryType.DIR));
            }
        }

        //创建一个ClassLoader
        return ClassLoaderFactory.createClassLoader(repositories, parent);
    }


    /**
     * 替换字符串中属性遍历占位符
     *
     * @param str The original string
     * @return the modified string
     */
    protected String replace(String str) {
        // Implementation is copied from ClassLoaderLogManager.replace(),
        // but added special processing for catalina.home and catalina.base.
        String result = str;
        int pos_start = str.indexOf("${");
        if (pos_start >= 0) {
            StringBuilder builder = new StringBuilder();
            int pos_end = -1;
            while (pos_start >= 0) {
                builder.append(str, pos_end + 1, pos_start);
                pos_end = str.indexOf('}', pos_start + 2);
                if (pos_end < 0) {
                    pos_end = pos_start - 1;
                    break;
                }
                String propName = str.substring(pos_start + 2, pos_end);
                String replacement;
                if (propName.length() == 0) {
                    replacement = null;
                } else if (Globals.CATALINA_HOME_PROP.equals(propName)) {
                    replacement = getCatalinaHome();
                } else if (Globals.CATALINA_BASE_PROP.equals(propName)) {
                    replacement = getCatalinaBase();
                } else {
                    replacement = System.getProperty(propName);
                }
                if (replacement != null) {
                    builder.append(replacement);
                } else {
                    builder.append(str, pos_start, pos_end + 1);
                }
                pos_start = str.indexOf("${", pos_end + 1);
            }
            builder.append(str, pos_end + 1, str.length());
            result = builder.toString();
        }
        return result;
    }


    /**
     * 初始化bootstrap
     */
    public void init() throws Exception {
        // 非常关键的地方，初始化类加载器
        initClassLoaders();

        // 设置上下文类加载器为catalinaLoader，这个类加载器负责加载Tomcat专用的类
        Thread.currentThread().setContextClassLoader(catalinaLoader);

        // 使用catalinaLoader加载tomcat源代码里面的各个专用类
        SecurityClassLoad.securityClassLoad(catalinaLoader);

        if (log.isDebugEnabled())
            log.debug("Loading startup class");

        //使用catalinaLoader类，通过反射实例化Catalina
        Class<?> startupClass = catalinaLoader.loadClass("org.apache.catalina.startup.Catalina");
        Object startupInstance = startupClass.getConstructor().newInstance();

        // 反射调用setParentClassLoader方法，设置其parentClassLoader为sharedLoader
        if (log.isDebugEnabled())
            log.debug("Setting startup class properties");
        String methodName = "setParentClassLoader";
        Class<?> paramTypes[] = new Class[1];
        paramTypes[0] = Class.forName("java.lang.ClassLoader");
        Object paramValues[] = new Object[1];
        paramValues[0] = sharedLoader;
        Method method =
            startupInstance.getClass().getMethod(methodName, paramTypes);
        method.invoke(startupInstance, paramValues);

        //初始化catalinaDaemon，引用创建的Catalina实例
        catalinaDaemon = startupInstance;

    }


    /**
     * 加载Tomcat
     * 内部反射调用catalina.load方法
     */
    private void load(String[] arguments)
        throws Exception {

        // Call the load() method
        String methodName = "load";
        Object param[];
        Class<?> paramTypes[];
        if (arguments==null || arguments.length==0) {
            paramTypes = null;
            param = null;
        } else {
            paramTypes = new Class[1];
            paramTypes[0] = arguments.getClass();
            param = new Object[1];
            param[0] = arguments;
        }
        Method method =
            catalinaDaemon.getClass().getMethod(methodName, paramTypes);
        if (log.isDebugEnabled())
            log.debug("Calling startup class " + method);
        method.invoke(catalinaDaemon, param);

    }


    /**
     * 获取Tomcat server组件
     * 内部反射调用catalina实例getServer()方法
     */
    private Object getServer() throws Exception {

        String methodName = "getServer";
        Method method =
            catalinaDaemon.getClass().getMethod(methodName);
        return method.invoke(catalinaDaemon);

    }


    // ----------------------------------------------------------- Main Program


    /**
     * 初始化，并加载 tomcat
     */
    public void init(String[] arguments)
        throws Exception {

        init();
        load(arguments);

    }


    /**
     * 启动tomcat
     * 内部调用catalina.start()
     */
    public void start()
        throws Exception {
        if( catalinaDaemon==null ) init();

        Method method = catalinaDaemon.getClass().getMethod("start", (Class [] )null);
        method.invoke(catalinaDaemon, (Object [])null);

    }


    /**
     * 停止 tomcat
     * 内部调用catalina.stop()
     */
    public void stop()
        throws Exception {

        Method method = catalinaDaemon.getClass().getMethod("stop", (Class [] ) null);
        method.invoke(catalinaDaemon, (Object [] ) null);

    }


    /**
     * 触发 tomcat server组件触发停止动作
     * @throws Exception Fatal stop error
     */
    public void stopServer()
        throws Exception {

        Method method =
            catalinaDaemon.getClass().getMethod("stopServer", (Class []) null);
        method.invoke(catalinaDaemon, (Object []) null);

    }


   /**
     * Stop the standalone server.
     * @param arguments Command line arguments
     * @throws Exception Fatal stop error
     */
    public void stopServer(String[] arguments)
        throws Exception {

        Object param[];
        Class<?> paramTypes[];
        if (arguments==null || arguments.length==0) {
            paramTypes = null;
            param = null;
        } else {
            paramTypes = new Class[1];
            paramTypes[0] = arguments.getClass();
            param = new Object[1];
            param[0] = arguments;
        }
        Method method =
            catalinaDaemon.getClass().getMethod("stopServer", paramTypes);
        method.invoke(catalinaDaemon, param);

    }


    /**
     * Set flag.
     * @param await <code>true</code> if the daemon should block
     * @throws Exception Reflection error
     */
    public void setAwait(boolean await)
        throws Exception {

        Class<?> paramTypes[] = new Class[1];
        paramTypes[0] = Boolean.TYPE;
        Object paramValues[] = new Object[1];
        paramValues[0] = Boolean.valueOf(await);
        Method method =
            catalinaDaemon.getClass().getMethod("setAwait", paramTypes);
        method.invoke(catalinaDaemon, paramValues);

    }

    public boolean getAwait()
        throws Exception
    {
        Class<?> paramTypes[] = new Class[0];
        Object paramValues[] = new Object[0];
        Method method =
            catalinaDaemon.getClass().getMethod("getAwait", paramTypes);
        Boolean b=(Boolean)method.invoke(catalinaDaemon, paramValues);
        return b.booleanValue();
    }


    /**
     * Destroy the Catalina Daemon.
     */
    public void destroy() {

        // FIXME

    }


    /**
     * tomcat 启动，停止，加载配置文件入口方法。
     * 由外部脚本调用，方法内部通过判断参数，执行不同处理。
     *
     * @param args Command line arguments to be processed
     */
    public static void main(String args[]) {

        // 当执行一个启动操作时，daemon肯定为null，
        if (daemon == null) {
            //直接new了一个Bootstrap对象，
            Bootstrap bootstrap = new Bootstrap();
            try {
                //初始化bootstrap
                bootstrap.init();
            } catch (Throwable t) {
                handleThrowable(t);
                t.printStackTrace();
                return;
            }
            // daemon守护对象设置为bootstrap
            daemon = bootstrap;
        } else {
            // 启动的tomcat 正在运行，将catalinaLoader加入当前线程上下文ClassLoader中
            Thread.currentThread().setContextClassLoader(daemon.catalinaLoader);
        }

        try {
            //默认执行的指令为"start"
            String command = "start";

            // 获取最后一个参数作为执行指令
            if (args.length > 0) {
                command = args[args.length - 1];
            }

            //根据不同的指令完成不同的操作
            if (command.equals("startd")) {
                args[args.length - 1] = "start";
                daemon.load(args);
                daemon.start();
            } else if (command.equals("stopd")) {
                args[args.length - 1] = "stop";
                daemon.stop();
            } else if (command.equals("start")) {
                daemon.setAwait(true);
                daemon.load(args);
                daemon.start();
                if (null == daemon.getServer()) {
                    //非正常退出程序
                    System.exit(1);
                }
            } else if (command.equals("stop")) {
                daemon.stopServer(args);
            } else if (command.equals("configtest")) {
                daemon.load(args);
                if (null == daemon.getServer()) {
                    //非正常退出程序
                    System.exit(1);
                }
                //正常退出程序
                System.exit(0);
            } else {
                log.warn("Bootstrap: command \"" + command + "\" does not exist.");
            }
        } catch (Throwable t) {
            if (t instanceof InvocationTargetException &&
                    t.getCause() != null) {
                t = t.getCause();
            }
            //处理Throwable,
            handleThrowable(t);
            //打印异常
            t.printStackTrace();
            //非正常退出程序
            System.exit(1);
        }

    }



    public static String getCatalinaHome() {
        return catalinaHomeFile.getPath();
    }

    public static String getCatalinaBase() {
        return catalinaBaseFile.getPath();
    }

    public static File getCatalinaHomeFile() {
        return catalinaHomeFile;
    }

    public static File getCatalinaBaseFile() {
        return catalinaBaseFile;
    }


    /**
     * 处理Throwable,只针对ThreadDeath，VirtualMachineError响应抛出异常
     * 其他异常忽略
     */
    private static void handleThrowable(Throwable t) {
        if (t instanceof ThreadDeath) {
            throw (ThreadDeath) t;
        }
        if (t instanceof VirtualMachineError) {
            throw (VirtualMachineError) t;
        }
        // 所有其他Throwable实例都会被默默吞噬
    }


    // Protected for unit testing
    protected static String[] getPaths(String value) {

        List<String> result = new ArrayList<>();
        Matcher matcher = PATH_PATTERN.matcher(value);

        while (matcher.find()) {
            String path = value.substring(matcher.start(), matcher.end());

            path = path.trim();
            if (path.length() == 0) {
                continue;
            }

            char first = path.charAt(0);
            char last = path.charAt(path.length() - 1);

            if (first == '"' && last == '"' && path.length() > 1) {
                path = path.substring(1, path.length() - 1);
                path = path.trim();
                if (path.length() == 0) {
                    continue;
                }
            } else if (path.contains("\"")) {
                // Unbalanced quotes
                // Too early to use standard i18n support. The class path hasn't
                // been configured.
                throw new IllegalArgumentException(
                        "The double quote [\"] character only be used to quote paths. It must " +
                        "not appear in a path. This loader path is not valid: [" + value + "]");
            } else {
                // Not quoted - NO-OP
            }

            result.add(path);
        }
        return result.toArray(new String[result.size()]);
    }
}
