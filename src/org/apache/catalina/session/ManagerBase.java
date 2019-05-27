package org.apache.catalina.session;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Engine;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.util.StringManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.modeler.Registry;


/**
 * <b>Manager</b>接口的抽象实现类， 不支持会话持久性或分派的能力. 这个类可以子类化来创建更复杂的管理器的实现.
 */
public abstract class ManagerBase implements Manager, MBeanRegistration {
    protected Log log = LogFactory.getLog(ManagerBase.class);

    // ----------------------------------------------------- Instance Variables

    protected DataInputStream randomIS=null;
    protected String devRandomSource="/dev/urandom";

    /**
     * 如果不能使用请求的，则使用默认的消息摘要算法.
     */
    protected static final String DEFAULT_ALGORITHM = "MD5";


    /**
     * 生成会话标识符时要使用的消息摘要算法. 
     * 这一定是一个<code>java.security.MessageDigest</code>支持的算法.
     */
    protected String algorithm = DEFAULT_ALGORITHM;


    /**
     * 关联的Container.
     */
    protected Container container;


    /**
     * 返回创建会话标识符使用的MessageDigest实现类.
     */
    protected MessageDigest digest = null;


    /**
     * 通过这个Manager创建的会话的分配标志.
     * 如果被设置为<code>true</code>, 所有添加到Manager控制的会话中的用户属性必须是可序列化的Serializable.
     */
    protected boolean distributable;


    /**
     * 一个字符串初始化参数，用于增加随机数生成器初始化的熵.
     */
    protected String entropy = null;


    /**
     * 这个实现类的描述信息.
     */
    private static final String info = "ManagerBase/1.0";


    /**
     * 这个Manager创建的会话的默认最大非活动间隔.
     */
    protected int maxInactiveInterval = 60;


    /**
     * 这个Manager创建的会话的会话ID长度.
     */
    protected int sessionIdLength = 16;


    /**
     * 这个 Manager实现类的描述信息(用于记录日志).
     */
    protected static String name = "ManagerBase";


    /**
     * 在生成会话标识符时，使用的随机数生成器.
     */
    protected Random random = null;


    /**
     * 随机数生成器的java类的名称，在生成会话标识符时.
     */
    protected String randomClass = "java.security.SecureRandom";


    /**
     * 过期会话存活的最长时间（秒）.
     */
    protected int sessionMaxAliveTime;


    /**
     * 过期会话的平均时间（秒）.
     */
    protected int sessionAverageAliveTime;


    /**
     * 已过期的会话数.
     */
    protected int expiredSessions = 0;


    /**
     * 当前活动会话集合, 会话标识符作为key.
     */
    protected HashMap sessions = new HashMap();

    // 此管理器创建的会话数
    protected int sessionCounter=0;

    protected int maxActive=0;

    // 重复会话ID号 - 大于0 意味着错误
    protected int duplicates=0;

    protected boolean initialized=false;
    
    /**
     * 会话过期期间的处理时间.
     */
    protected long processingTime = 0;

    /**
     * 后台处理的迭代计数.
     */
    private int count = 0;


    /**
     * 会话过期频率, 及相关管理操作.
     * Manager 操作将完成一次指定数量的backgrondProces的调用 (ie, 数额越低, 最常见的检查将发生).
     */
    protected int processExpiresFrequency = 6;

    /**
     * The string manager for this package.
     */
    protected static StringManager sm = StringManager.getManager(Constants.Package);

    /**
     * 属性修改支持.
     */
    protected PropertyChangeSupport support = new PropertyChangeSupport(this);
    
    
    // ------------------------------------------------------------- Security classes


    private class PrivilegedSetRandomFile implements PrivilegedAction{
        
        public Object run(){               
            try {
                File f=new File( devRandomSource );
                if( ! f.exists() ) return null;
                randomIS= new DataInputStream( new FileInputStream(f));
                randomIS.readLong();
                if( log.isDebugEnabled() )
                    log.debug( "Opening " + devRandomSource );
                return randomIS;
            } catch (IOException ex){
                return null;
            }
        }
    }


    // ------------------------------------------------------------- Properties

    /**
     * 返回消息摘要算法.
     */
    public String getAlgorithm() {
        return (this.algorithm);
    }


    /**
     * 设置消息摘要算法.
     *
     * @param algorithm The new message digest algorithm
     */
    public void setAlgorithm(String algorithm) {
        String oldAlgorithm = this.algorithm;
        this.algorithm = algorithm;
        support.firePropertyChange("algorithm", oldAlgorithm, this.algorithm);
    }


    /**
     * 返回关联的Container.
     */
    public Container getContainer() {
        return (this.container);
    }


    /**
     * 设置关联的Container.
     *
     * @param container The newly associated Container
     */
    public void setContainer(Container container) {
        Container oldContainer = this.container;
        this.container = container;
        support.firePropertyChange("container", oldContainer, this.container);
    }


    /** 返回实现类的名称
     */
    public String getClassName() {
        return this.getClass().getName();
    }


    /**
     * 返回MessageDigest对象，用于计算会话标识符.
     * 如果还没有创建, 在第一次调用此方法时初始化一个.
     */
    public synchronized MessageDigest getDigest() {

        if (this.digest == null) {
            long t1=System.currentTimeMillis();
            if (log.isDebugEnabled())
                log.debug(sm.getString("managerBase.getting", algorithm));
            try {
                this.digest = MessageDigest.getInstance(algorithm);
            } catch (NoSuchAlgorithmException e) {
                log.error(sm.getString("managerBase.digest", algorithm), e);
                try {
                    this.digest = MessageDigest.getInstance(DEFAULT_ALGORITHM);
                } catch (NoSuchAlgorithmException f) {
                    log.error(sm.getString("managerBase.digest",
                                     DEFAULT_ALGORITHM), e);
                    this.digest = null;
                }
            }
            if (log.isDebugEnabled())
                log.debug(sm.getString("managerBase.gotten"));
            long t2=System.currentTimeMillis();
            if( log.isDebugEnabled() )
                log.debug("getDigest() " + (t2-t1));
        }
        return (this.digest);
    }


    /**
     * 返回这个Manager支持的会话分配的标记.
     */
    public boolean getDistributable() {
        return (this.distributable);
    }


    /**
     * 设置这个Manager支持的会话分配的标记. 
     * 如果这个标记被设置, 添加到会话中的所有用户数据对象必须实现Serializable.
     *
     * @param distributable The new distributable flag
     */
    public void setDistributable(boolean distributable) {

        boolean oldDistributable = this.distributable;
        this.distributable = distributable;
        support.firePropertyChange("distributable",
                                   new Boolean(oldDistributable),
                                   new Boolean(this.distributable));

    }


    /**
     * 返回值的熵增加, 或者如果这个字符串还没有被设置，计算一个半有效的值.
     */
    public String getEntropy() {

        // 如果没有设置，则计算一个半有效值
        if (this.entropy == null) {
            // Use APR to get a crypto secure entropy value
            byte[] result = new byte[32];
            boolean apr = false;
            try {
                String methodName = "random";
                Class paramTypes[] = new Class[2];
                paramTypes[0] = result.getClass();
                paramTypes[1] = int.class;
                Object paramValues[] = new Object[2];
                paramValues[0] = result;
                paramValues[1] = new Integer(32);
                Method method = Class.forName("org.apache.tomcat.jni.OS")
                    .getMethod(methodName, paramTypes);
                method.invoke(null, paramValues);
                apr = true;
            } catch (Throwable t) {
                // Ignore
            }
            if (apr) {
                setEntropy(new String(result));
            } else {
                setEntropy(this.toString());
            }
        }
        return (this.entropy);
    }


    /**
     * 设置值的熵增加
     *
     * @param entropy The new entropy increaser value
     */
    public void setEntropy(String entropy) {
        String oldEntropy = entropy;
        this.entropy = entropy;
        support.firePropertyChange("entropy", oldEntropy, this.entropy);
    }


    /**
     * 返回描述信息和版本号, 格式为
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {
        return (info);
    }


    /**
     * 返回会话默认的最大非活动间隔 (in seconds).
     */
    public int getMaxInactiveInterval() {
        return (this.maxInactiveInterval);
    }


    /**
     * 设置会话默认的最大非活动间隔 (in seconds).
     *
     * @param interval The new default value
     */
    public void setMaxInactiveInterval(int interval) {
        int oldMaxInactiveInterval = this.maxInactiveInterval;
        this.maxInactiveInterval = interval;
        support.firePropertyChange("maxInactiveInterval",
                                   new Integer(oldMaxInactiveInterval),
                                   new Integer(this.maxInactiveInterval));
    }


    /**
     * 返回这个Manager创建的会话的会话ID长度(in bytes).
     *
     * @return The session id length
     */
    public int getSessionIdLength() {
        return (this.sessionIdLength);
    }


    /**
     * 设置这个Manager创建的会话的会话ID长度(in bytes).
     *
     * @param idLength The session id length
     */
    public void setSessionIdLength(int idLength) {
        int oldSessionIdLength = this.sessionIdLength;
        this.sessionIdLength = idLength;
        support.firePropertyChange("sessionIdLength",
                                   new Integer(oldSessionIdLength),
                                   new Integer(this.sessionIdLength));
    }


    /**
     * 返回这个Manager实现类的描述信息.
     */
    public String getName() {
        return (name);
    }

    /** 
     * 使用 /dev/random-type 特殊设计. 这是新代码, 但可以减少产生随机性的大延迟.
     *
     *  必须指定一个随机生成器文件的路径. 在Linux（或类似）系统中使用 /dev/urandom.
     *  为最大的安全性使用 /dev/random (如果没有足够的"random"存在，它可能会阻塞). 还可以使用生成随机数的管道.
     *
     *  代码将检查文件是否存在, 默认 java Random. 有显著的性能差异, 在第一次调用getSession时非常明显 (就像在第一个JSP中 )
     *  - 如果可以的话使用它.
     */
    public void setRandomFile( String s ) {
        // 作为一个后门, 可以使用静态文件 - 并产生相同的会话ID (适合奇怪的调试)
        if (System.getSecurityManager() != null){
            randomIS = (DataInputStream)AccessController.doPrivileged(new PrivilegedSetRandomFile());          
        } else {
            try{
                devRandomSource=s;
                File f=new File( devRandomSource );
                if( ! f.exists() ) return;
                randomIS= new DataInputStream( new FileInputStream(f));
                randomIS.readLong();
                if( log.isDebugEnabled() )
                    log.debug( "Opening " + devRandomSource );
            } catch( IOException ex ) {
                try {
                    randomIS.close();
                } catch (Exception e) {
                    log.warn("Failed to close randomIS.");
                }
                
                randomIS=null;
            }
        }
    }

    public String getRandomFile() {
        return devRandomSource;
    }


    /**
     * 返回用于生成会话标识符的随机数生成器实例. 
     * 如果没有当前定义的生成器, 创建一个.
     */
    public Random getRandom() {
        if (this.random == null) {
            // Calculate the new random number generator seed
            long seed = System.currentTimeMillis();
            long t1 = seed;
            char entropy[] = getEntropy().toCharArray();
            for (int i = 0; i < entropy.length; i++) {
                long update = ((byte) entropy[i]) << ((i % 8) * 8);
                seed ^= update;
            }
            try {
                // Construct and seed a new random number generator
                Class clazz = Class.forName(randomClass);
                this.random = (Random) clazz.newInstance();
                this.random.setSeed(seed);
            } catch (Exception e) {
                // Fall back to the simple case
                log.error(sm.getString("managerBase.random", randomClass),
                        e);
                this.random = new java.util.Random();
                this.random.setSeed(seed);
            }
            if(log.isDebugEnabled()) {
                long t2=System.currentTimeMillis();
                if( (t2-t1) > 100 )
                    log.debug(sm.getString("managerBase.seeding", randomClass) + " " + (t2-t1));
            }
        }
        return (this.random);
    }


    /**
     * 返回随机数发生器类的名称.
     */
    public String getRandomClass() {
        return (this.randomClass);
    }


    /**
     * 设置随机数发生器类的名称.
     *
     * @param randomClass The new random number generator class name
     */
    public void setRandomClass(String randomClass) {
        String oldRandomClass = this.randomClass;
        this.randomClass = randomClass;
        support.firePropertyChange("randomClass", oldRandomClass,
                                   this.randomClass);
    }


    /**
     * 获取已过期的会话的数目.
     *
     * @return Number of sessions that have expired
     */
    public int getExpiredSessions() {
        return expiredSessions;
    }


    /**
     * 设置已过期的会话的数目.
     *
     * @param expiredSessions Number of sessions that have expired
     */
    public void setExpiredSessions(int expiredSessions) {
        this.expiredSessions = expiredSessions;
    }

    public long getProcessingTime() {
        return processingTime;
    }


    public void setProcessingTime(long processingTime) {
        this.processingTime = processingTime;
    }
    
    /**
     * 返回检查的频率.
     */
    public int getProcessExpiresFrequency() {
        return (this.processExpiresFrequency);
    }

    /**
     * 设置检查的频率.
     *
     * @param processExpiresFrequency the new manager checks frequency
     */
    public void setProcessExpiresFrequency(int processExpiresFrequency) {

        if (processExpiresFrequency <= 0) {
            return;
        }

        int oldProcessExpiresFrequency = this.processExpiresFrequency;
        this.processExpiresFrequency = processExpiresFrequency;
        support.firePropertyChange("processExpiresFrequency",
                                   new Integer(oldProcessExpiresFrequency),
                                   new Integer(this.processExpiresFrequency));

    }

    // --------------------------------------------------------- Public Methods


    /**
     * 实现Manager接口, 直接调用processExpires
     */
    public void backgroundProcess() {
        count = (count + 1) % processExpiresFrequency;
        if (count == 0)
            processExpires();
    }

    /**
     * 使已过期的所有会话无效.
     */
    public void processExpires() {

        long timeNow = System.currentTimeMillis();
        Session sessions[] = findSessions();
        int expireHere = 0 ;
        
        if(log.isDebugEnabled())
            log.debug("Start expire sessions " + getName() + " at " + timeNow + " sessioncount " + sessions.length);
        for (int i = 0; i < sessions.length; i++) {
            if (!sessions[i].isValid()) {
                expiredSessions++;
                expireHere++;
            }
        }
        long timeEnd = System.currentTimeMillis();
        if(log.isDebugEnabled())
             log.debug("End expire sessions " + getName() + " processingTime " + (timeEnd - timeNow) + " expired sessions: " + expireHere);
        processingTime += ( timeEnd - timeNow );

    }

    public void destroy() {
        if( oname != null )
            Registry.getRegistry(null, null).unregisterComponent(oname);
        initialized=false;
        oname = null;
    }
    
    public void init() {
        if( initialized ) return;
        initialized=true;        
        
        if( oname==null ) {
            try {
                StandardContext ctx=(StandardContext)this.getContainer();
                Engine eng=(Engine)ctx.getParent().getParent();
                domain=ctx.getEngineName();
                distributable = ctx.getDistributable();
                StandardHost hst=(StandardHost)ctx.getParent();
                String path = ctx.getPath();
                if (path.equals("")) {
                    path = "/";
                }   
                oname=new ObjectName(domain + ":type=Manager,path="
                + path + ",host=" + hst.getName());
                Registry.getRegistry(null, null).registerComponent(this, oname, null );
            } catch (Exception e) {
                log.error("Error registering ",e);
            }
        }
        
        // 初始化随机数生成
        getRandomBytes(new byte[16]);
        
        if(log.isDebugEnabled())
            log.debug("Registering " + oname );
    }

    /**
     * 添加这个Session到活动的Session集合.
     *
     * @param session Session to be added
     */
    public void add(Session session) {

        synchronized (sessions) {
            sessions.put(session.getIdInternal(), session);
            if( sessions.size() > maxActive ) {
                maxActive=sessions.size();
            }
        }
    }


    /**
     * 添加一个属性修改监听器.
     *
     * @param listener The listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }


    /**
     * 构建并返回一个新会话对象, 基于此Manager属性指定的默认设置.
     * 此方法将分配会话id, 并使返回的会话的 getId()方法可用.
     * 如果不能创建新会话, 返回<code>null</code>.
     * 
     * @exception IllegalStateException if a new session cannot be
     *  instantiated for any reason
     * @deprecated
     */
    public Session createSession() {
        return createSession(null);
    }
    
    
    /**
     * 构建并返回一个新会话对象, 基于此Manager属性指定的默认设置.
     * 将使用指定的会话ID作为实际session的ID.
     * 如果不能创建新会话, 返回<code>null</code>.
     * 
     * @param sessionId 用于创建新会话的会话ID; 如果为<code>null</code>, 将生成一个新会话ID
     * @exception IllegalStateException 如果会话不能创建
     */
    public Session createSession(String sessionId) {
        
        // 回收或创建会话实例
        Session session = createEmptySession();

        // 初始化新会话的属性并返回它
        session.setNew(true);
        session.setValid(true);
        session.setCreationTime(System.currentTimeMillis());
        session.setMaxInactiveInterval(this.maxInactiveInterval);
        if (sessionId == null) {
            sessionId = generateSessionId();
        // FIXME WHy we need no duplication check?
        /*         
             synchronized (sessions) {
                while (sessions.get(sessionId) != null) { // Guarantee
                    // uniqueness
                    duplicates++;
                    sessionId = generateSessionId();
                }
            }
        */
            
            // FIXME: Code to be used in case route replacement is needed
            /*
        } else {
            String jvmRoute = getJvmRoute();
            if (getJvmRoute() != null) {
                String requestJvmRoute = null;
                int index = sessionId.indexOf(".");
                if (index > 0) {
                    requestJvmRoute = sessionId
                            .substring(index + 1, sessionId.length());
                }
                if (requestJvmRoute != null && !requestJvmRoute.equals(jvmRoute)) {
                    sessionId = sessionId.substring(0, index) + "." + jvmRoute;
                }
            }
            */
        }
        session.setId(sessionId);
        sessionCounter++;
        return (session);
    }
    
    
    /**
     * 从回收的循环中获取一个会话，或者创建一个空的会话.
     * PersistentManager不需要创建会话数据，因为它从Store中读取.
     */
    public Session createEmptySession() {
        return (getNewSession());
    }


    /**
     * 返回活动的Session, 使用指定的会话ID; 或者<code>null</code>.
     *
     * @param id The session id for the session to be returned
     *
     * @exception IllegalStateException 如果新的会话不能被创建
     * @exception IOException 处理这个请求期间发生的错误
     */
    public Session findSession(String id) throws IOException {
        if (id == null)
            return (null);
        synchronized (sessions) {
            Session session = (Session) sessions.get(id);
            return (session);
        }
    }


    /**
     * 返回活动会话的的集合.
     * 如果这个Manager没有活动的Sessions, 返回零长度数组.
     */
    public Session[] findSessions() {
        Session results[] = null;
        synchronized (sessions) {
            results = new Session[sessions.size()];
            results = (Session[]) sessions.values().toArray(results);
        }
        return (results);
    }


    /**
     * 从活动会话集合中移除这个Session.
     *
     * @param session Session to be removed
     */
    public void remove(Session session) {
        synchronized (sessions) {
            sessions.remove(session.getIdInternal());
        }
    }


    /**
     * 移除一个属性修改监听器
     *
     * @param listener The listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 获取在doLoad()方法中使用的新会话类.
     */
    protected StandardSession getNewSession() {
        return new StandardSession(this);
    }


    protected void getRandomBytes(byte bytes[]) {
        // 生成包含会话标识符的字节数组
        if (devRandomSource != null && randomIS == null) {
            setRandomFile(devRandomSource);
        }
        if (randomIS != null) {
            try {
                int len = randomIS.read(bytes);
                if (len == bytes.length) {
                    return;
                }
                if(log.isDebugEnabled())
                    log.debug("Got " + len + " " + bytes.length );
            } catch (Exception ex) {
                // Ignore
            }
            devRandomSource = null;
            
            try {
                randomIS.close();
            } catch (Exception e) {
                log.warn("Failed to close randomIS.");
            }
            
            randomIS = null;
        }
        getRandom().nextBytes(bytes);
    }


    /**
     * 生成并返回一个新会话标识符.
     */
    protected synchronized String generateSessionId() {

        byte random[] = new byte[16];
        String jvmRoute = getJvmRoute();
        String result = null;

        // 将结果呈现为十六进制数字的字符串
        StringBuffer buffer = new StringBuffer();
        do {
            int resultLenBytes = 0;
            if (result != null) {
                buffer = new StringBuffer();
                duplicates++;
            }

            while (resultLenBytes < this.sessionIdLength) {
                getRandomBytes(random);
                random = getDigest().digest(random);
                for (int j = 0;
                j < random.length && resultLenBytes < this.sessionIdLength;
                j++) {
                    byte b1 = (byte) ((random[j] & 0xf0) >> 4);
                    byte b2 = (byte) (random[j] & 0x0f);
                    if (b1 < 10)
                        buffer.append((char) ('0' + b1));
                    else
                        buffer.append((char) ('A' + (b1 - 10)));
                    if (b2 < 10)
                        buffer.append((char) ('0' + b2));
                    else
                        buffer.append((char) ('A' + (b2 - 10)));
                    resultLenBytes++;
                }
            }
            if (jvmRoute != null) {
                buffer.append('.').append(jvmRoute);
            }
            result = buffer.toString();
        } while (sessions.get(result) != null);
        return (result);
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 检索封闭的Engine.
     *
     * @return an Engine object (or null).
     */
    public Engine getEngine() {
        Engine e = null;
        for (Container c = getContainer(); e == null && c != null ; c = c.getParent()) {
            if (c != null && c instanceof Engine) {
                e = (Engine)c;
            }
        }
        return e;
    }


    /**
     * 检索封闭的Engine的JvmRoute.
     * @return the JvmRoute or null.
     */
    public String getJvmRoute() {
        Engine e = getEngine();
        return e == null ? null : e.getJvmRoute();
    }


    // -------------------------------------------------------- Package Methods


    public void setSessionCounter(int sessionCounter) {
        this.sessionCounter = sessionCounter;
    }


    /** 
     * 会话总数.
     *
     * @return sessions created
     */
    public int getSessionCounter() {
        return sessionCounter;
    }


    /** 
     * 随机源生成的重复会话ID数. 大于0意味着有问题.
     *
     * @return The count of duplicates
     */
    public int getDuplicates() {
        return duplicates;
    }


    public void setDuplicates(int duplicates) {
        this.duplicates = duplicates;
    }


    /** 
     * 返回活动会话的数目
     *
     * @return number of sessions active
     */
    public int getActiveSessions() {
        return sessions.size();
    }


    /**
     * 最大并行活动会话数
     */
    public int getMaxActive() {
        return maxActive;
    }


    public void setMaxActive(int maxActive) {
        this.maxActive = maxActive;
    }


    /**
     * 过期会话存活的最长时间（秒）.
     */
    public int getSessionMaxAliveTime() {
        return sessionMaxAliveTime;
    }


    /**
     * 过期会话存活的最长时间（秒）.
     *
     * @param sessionMaxAliveTime Longest time (in seconds) that an expired
     * session had been alive.
     */
    public void setSessionMaxAliveTime(int sessionMaxAliveTime) {
        this.sessionMaxAliveTime = sessionMaxAliveTime;
    }


    /**
     * 过期会话的平均时间（秒）.
     */
    public int getSessionAverageAliveTime() {
        return sessionAverageAliveTime;
    }


    /**
     * 过期会话的平均时间（秒）.
     *
     * @param sessionAverageAliveTime Average time (in seconds) that expired
     * sessions had been alive.
     */
    public void setSessionAverageAliveTime(int sessionAverageAliveTime) {
        this.sessionAverageAliveTime = sessionAverageAliveTime;
    }


    /** 
     * 调试: 返回当前激活的所有会话ID的列表
     */
    public String listSessionIds() {
        StringBuffer sb=new StringBuffer();
        Iterator keys=sessions.keySet().iterator();
        while( keys.hasNext() ) {
            sb.append(keys.next()).append(" ");
        }
        return sb.toString();
    }


    /** 
     * 调试: 获取会话属性
     *
     * @param sessionId
     * @param key
     * @return The attribute value, if found, null otherwise
     */
    public String getSessionAttribute( String sessionId, String key ) {
        Session s=(Session)sessions.get(sessionId);
        if( s==null ) {
            if(log.isInfoEnabled())
                log.info("Session not found " + sessionId);
            return null;
        }
        Object o=s.getSession().getAttribute(key);
        if( o==null ) return null;
        return o.toString();
    }


    /**
     * 返回与给定会话ID相关的会话信息.
     * 
     * <p>会话信息被组织为 HashMap, 将会话属性名称映射到其值的String.
     *
     * @param sessionId Session id
     * 
     * @return HashMap mapping session attribute names to the String
     * representation of their values, or null if no session with the
     * specified id exists, or if the session does not have any attributes
     */
    public HashMap getSession(String sessionId) {
        Session s = (Session) sessions.get(sessionId);
        if (s == null) {
            if (log.isInfoEnabled()) {
                log.info("Session not found " + sessionId);
            }
            return null;
        }

        Enumeration ee = s.getSession().getAttributeNames();
        if (ee == null || !ee.hasMoreElements()) {
            return null;
        }

        HashMap map = new HashMap();
        while (ee.hasMoreElements()) {
            String attrName = (String) ee.nextElement();
            map.put(attrName, getSessionAttribute(sessionId, attrName));
        }

        return map;
    }


    public void expireSession( String sessionId ) {
        Session s=(Session)sessions.get(sessionId);
        if( s==null ) {
            if(log.isInfoEnabled())
                log.info("Session not found " + sessionId);
            return;
        }
        s.expire();
    }


    public String getLastAccessedTime( String sessionId ) {
        Session s=(Session)sessions.get(sessionId);
        if( s==null ) {
            log.info("Session not found " + sessionId);
            return "";
        }
        return new Date(s.getLastAccessedTime()).toString();
    }


    // -------------------- JMX and Registration  --------------------
    protected String domain;
    protected ObjectName oname;
    protected MBeanServer mserver;

    public ObjectName getObjectName() {
        return oname;
    }

    public String getDomain() {
        return domain;
    }

    public ObjectName preRegister(MBeanServer server,
                                  ObjectName name) throws Exception {
        oname=name;
        mserver=server;
        domain=name.getDomain();
        return name;
    }

    public void postRegister(Boolean registrationDone) {
    }

    public void preDeregister() throws Exception {
    }

    public void postDeregister() {
    }
}
