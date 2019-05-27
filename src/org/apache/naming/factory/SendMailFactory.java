package org.apache.naming.factory;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Enumeration;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimePart;
import javax.mail.internet.MimePartDataSource;
import javax.naming.Name;
import javax.naming.Context;
import javax.naming.Reference;
import javax.naming.RefAddr;
import javax.naming.spi.ObjectFactory;

/**
 * 工厂类, 创建一个JNDI命名JavaMail MimePartDataSource对象, 可使用SMTP发送邮件.
 * <p>
 * 可以在server.xml配置文件中在 DefaultContext 或 Context范围内配置.
 * <p>
 * Example:
 * <p>
 * <pre>
 * &lt;Resource name="mail/send" auth="CONTAINER"
 *           type="javax.mail.internet.MimePartDataSource"/>
 * &lt;ResourceParams name="mail/send">
 *   &lt;parameter>&lt;name>factory&lt;/name>
 *     &lt;value>org.apache.naming.factory.SendMailFactory&lt;/value>
 *   &lt;/parameter>
 *   &lt;parameter>&lt;name>mail.smtp.host&lt;/name>
 *     &lt;value>your.smtp.host&lt;/value>
 *   &lt;/parameter>
 *   &lt;parameter>&lt;name>mail.smtp.user&lt;/name>
 *     &lt;value>someuser&lt;/value>
 *   &lt;/parameter>
 *   &lt;parameter>&lt;name>mail.from&lt;/name>
 *     &lt;value>someuser@some.host&lt;/value>
 *   &lt;/parameter>
 *   &lt;parameter>&lt;name>mail.smtp.sendpartial&lt;/name>
 *     &lt;value>true&lt;/value>
 *   &lt;/parameter>
 *  &lt;parameter>&lt;name>mail.smtp.dsn.notify&lt;/name>
 *     &lt;value>FAILURE&lt;/value>
 *   &lt;/parameter>
 *   &lt;parameter>&lt;name>mail.smtp.dsn.ret&lt;/name>
 *     &lt;value>FULL&lt;/value>
 *   &lt;/parameter>
 * &lt;/ResourceParams>
 * </pre>
 */
public class SendMailFactory implements ObjectFactory {
    // javamail MimeMessageDataSource的类名
    protected final String DataSourceClassName = "javax.mail.internet.MimePartDataSource";

    public Object getObjectInstance(Object RefObj, Name Nm, Context Ctx, Hashtable Env) throws Exception {
	final Reference Ref = (Reference)RefObj;

	// DataSource的创建是包装进一个 doPrivileged中的, 因此javamail 可以读取默认属性, 而不抛出 Security Exceptions
	if (Ref.getClassName().equals(DataSourceClassName)) {
	    return AccessController.doPrivileged( new PrivilegedAction()
	    {
		public Object run() {
        	    //设置将发送消息的SMTP会话
	            Properties props = new Properties();
		    // enumeration of all refaddr
		    Enumeration list = Ref.getAll();
		    // current refaddr to be set
		    RefAddr refaddr;
	            // set transport to smtp
	            props.put("mail.transport.protocol", "smtp");

		    while (list.hasMoreElements()) {
			refaddr = (RefAddr)list.nextElement();

			// set property
			props.put(refaddr.getType(), (String)refaddr.getContent());
		    }
		    MimeMessage message = new MimeMessage(
			Session.getInstance(props));
		    try {
			String from = (String)Ref.get("mail.from").getContent();
		        message.setFrom(new InternetAddress(from));
		        message.setSubject("");
		    } catch (Exception e) {}
		    MimePartDataSource mds = new MimePartDataSource(
			(MimePart)message);
		    return mds;
		}
	    } );
	}
	else { // 不能创建一个DataSource实例
	    return null;
	}
    }
}
