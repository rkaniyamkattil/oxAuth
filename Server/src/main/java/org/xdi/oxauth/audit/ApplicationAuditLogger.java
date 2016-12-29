/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.audit;

import com.google.common.base.Objects;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.pool.PooledConnectionFactory;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.BooleanUtils;
import org.jboss.seam.Component;
import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.*;
import org.jboss.seam.annotations.async.Asynchronous;
import org.jboss.seam.log.Log;
import org.xdi.oxauth.model.audit.OAuth2AuditLog;
import org.xdi.oxauth.model.config.ConfigurationFactory;
import org.xdi.oxauth.model.config.StaticConf;
import org.xdi.oxauth.model.configuration.Configuration;
import org.xdi.oxauth.util.ServerUtil;

import javax.jms.*;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @version December 28, 2016
 */
@Name("applicationAuditLogger")
@Scope(ScopeType.APPLICATION)
@Startup
public class ApplicationAuditLogger {

    private static final String APPLICATION_AUDIT_LOGGER_REFRESH_TIMER = "applicationAuditLoggerRefreshTimer";

    private final String BROKER_URL_PREFIX = "failover:(";
    private final String BROKER_URL_SUFFIX = ")?timeout=5000&jms.useAsyncSend=true";
    private final int ACK_MODE = Session.AUTO_ACKNOWLEDGE;
    private final String CLIENT_QUEUE_NAME = "oauth2.audit.logging";
    private final boolean transacted = false;

    private volatile PooledConnectionFactory pooledConnectionFactory;

    private Set<String> jmsBrokerURISet;
    private String jmsUserName;
    private String jmsPassword;

    @Logger
    private Log logger;

    @In
    private ConfigurationFactory configurationFactory;

    private Configuration configuration;

    private final ReentrantLock lock = new ReentrantLock();

    @Create
    public void init() {
        tryToEstablishJMSConnection();
    }

    @Observer(ConfigurationFactory.CONFIGURATION_UPDATE_EVENT)
    public void updateConfiguration(Configuration configuration, StaticConf staticConfiguration) {
        this.configuration = configuration;
    }

    @Asynchronous
    public void sendMessage(OAuth2AuditLog oAuth2AuditLog) {
        if (BooleanUtils.isNotTrue(isEnabledOAuthAuditnLogging())) {
            return;
        }

        if ((this.pooledConnectionFactory == null) || isJmsConfigChanged()) {
            if (tryToEstablishJMSConnection())
                loggingThroughJMS(oAuth2AuditLog);
            else
                loggingThroughFile(oAuth2AuditLog);
        } else {
            loggingThroughJMS(oAuth2AuditLog);
        }
    }

    @Destroy
    public void destroy() {
        if (this.pooledConnectionFactory == null)
            return;
        this.pooledConnectionFactory.clear();
        this.pooledConnectionFactory = null;
    }

    private boolean tryToEstablishJMSConnection() {
        lock.lock();
        try {
            // Check if another thread init JMS pool already
            if ((this.pooledConnectionFactory != null) && !isJmsConfigChanged()) {
                return tryToEstablishJMSConnectionImpl();
            }

            return true;
        } finally {
            lock.unlock();
        }
    }

    private boolean tryToEstablishJMSConnectionImpl() {
        destroy();

        Set<String> jmsBrokerURISet = getJmsBrokerURISet();
        if (BooleanUtils.isNotTrue(isEnabledOAuthAuditnLogging()) || CollectionUtils.isEmpty(jmsBrokerURISet))
            return false;

        this.jmsBrokerURISet = new HashSet<String>(jmsBrokerURISet);
        this.jmsUserName = getJmsUserName();
        this.jmsPassword = getJmsPassword();

        Iterator<String> jmsBrokerURIIterator = jmsBrokerURISet.iterator();

        StringBuilder uriBuilder = new StringBuilder();
        while (jmsBrokerURIIterator.hasNext()) {
            String jmsBrokerURI = jmsBrokerURIIterator.next();
            uriBuilder.append("tcp://");
            uriBuilder.append(jmsBrokerURI);
            if (jmsBrokerURIIterator.hasNext())
                uriBuilder.append(",");
        }

        String brokerUrl = BROKER_URL_PREFIX + uriBuilder + BROKER_URL_SUFFIX;

        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(this.jmsUserName, this.jmsPassword,
                brokerUrl);
        this.pooledConnectionFactory = new PooledConnectionFactory(connectionFactory);

        pooledConnectionFactory.setIdleTimeout(5000);
        pooledConnectionFactory.setMaxConnections(10);
        pooledConnectionFactory.start();

        return true;
    }

    private void loggingThroughJMS(OAuth2AuditLog oAuth2AuditLog) {
        QueueConnection connection = null;
        try {
            connection = pooledConnectionFactory.createQueueConnection();
            connection.start();

            QueueSession session = connection.createQueueSession(transacted, ACK_MODE);
            MessageProducer producer = session.createProducer(session.createQueue(CLIENT_QUEUE_NAME));

            TextMessage txtMessage = session.createTextMessage();
            txtMessage.setText(ServerUtil.asPrettyJson(oAuth2AuditLog));
            producer.send(txtMessage);
        } catch (JMSException e) {
            logger.error("Can't send message", e);
        } catch (IOException e) {
            logger.error("Can't serialize the audit log", e);
        } catch (Exception e) {
            logger.error("Can't send message, please check your activeMQ configuration.", e);
        } finally {
            if (connection == null)
                return;
            try {
                connection.close();
            } catch (JMSException e) {
                logger.error("Can't close connection.");
            }
        }
    }

    private void loggingThroughFile(OAuth2AuditLog oAuth2AuditLog) {
        try {
            logger.info(ServerUtil.asPrettyJson(oAuth2AuditLog));
        } catch (IOException e) {
            logger.error("Can't serialize the audit log", e);
        }
    }

    private boolean isJmsConfigChanged() {
        return !Objects.equal(this.jmsUserName, getJmsUserName()) || !Objects.equal(this.jmsPassword, getJmsPassword())
                || !Objects.equal(this.jmsBrokerURISet, getJmsBrokerURISet());
    }

    private Boolean isEnabledOAuthAuditnLogging() {
        return getConfiguration().getEnabledOAuthAuditLogging();
    }

    private Set<String> getJmsBrokerURISet() {
        return getConfiguration().getJmsBrokerURISet();
    }

    private String getJmsUserName() {
        return getConfiguration().getJmsUserName();
    }

    private String getJmsPassword() {
        return getConfiguration().getJmsPassword();
    }

    private Configuration getConfiguration() {
        // Workaround for tomcat
        if (configuration == null) {
            configuration = (Configuration) Component.getInstance("configuration", true);
        }

        return configuration;
    }
}