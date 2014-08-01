package grails.plugin.databasesession

import grails.util.GrailsUtil
import grails.validation.ValidationException

import javax.annotation.PostConstruct

import org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes
import org.springframework.util.Assert

/**
 * @author Burt Beckwith
 */
class GormPersisterService implements Persister {

	static transactional = false

	def grailsApplication
	def persistentSessionService

	def maxInactiveInterval
	boolean useMongo = false

	@PostConstruct
	void postConstruct() {
		useMongo = grailsApplication.config.grails.plugin.databasesession.persistence.provider == "mongodb"

		maxInactiveInterval = grailsApplication.config.webxml.sessionConfig.sessionTimeout
		if (!(maxInactiveInterval instanceof Integer)) {
			maxInactiveInterval = 30
		}
	}

	void create(String sessionId) {
		try {
			if (PersistentSession.exists(sessionId)) {
				return
			}

			Long creationTime = System.currentTimeMillis()

			if (useMongo) {
				Map data = [creationTime: creationTime, _id: sessionId, lastAccessedTime: creationTime,
					maxInactiveInterval: maxInactiveInterval, invalidated: false]

				PersistentSession.collection.insert(data)
			} else {
				PersistentSession sessionInstance = new PersistentSession()
				sessionInstance.maxInactiveInterval = maxInactiveInterval
				sessionInstance.id = sessionId
				sessionInstance.save(flush: true, failOnError: true)
			}
		} catch (e) {
			handleException e
		}
	}

	Object getAttribute(String sessionId, String name) throws InvalidatedSessionException {
		if (name == null) return null

		if (GrailsApplicationAttributes.FLASH_SCOPE == name) {
			// special case; use request scope since a new deserialized instance is created each time it's retrieved from the session
			def fs = SessionProxyFilter.request.getAttribute(GrailsApplicationAttributes.FLASH_SCOPE)
			if (fs != null) {
				return fs
			}
		}

		try {
			PersistentSession session = PersistentSession.get(sessionId)
			checkInvalidated session
			session.lastAccessedTime = System.currentTimeMillis()

			def attribute = persistentSessionService.deserializeAttributeValue(
					persistentSessionService.findValueBySessionAndAttributeName(session, name)?.serialized)

			if (attribute != null && GrailsApplicationAttributes.FLASH_SCOPE == name) {
				SessionProxyFilter.request.setAttribute(GrailsApplicationAttributes.FLASH_SCOPE, attribute)
			}

			return attribute
		}
		catch (e) {
			handleException e
		}
	}

	void setAttribute(String sessionId, String name, value) throws InvalidatedSessionException {

		Assert.notNull name, 'name parameter cannot be null'

		if (value == null) {
			removeAttribute sessionId, name
			return
		}

		// special case; use request scope and don't store in session, the filter will set it in the session at the end of the request
		if (value != null && GrailsApplicationAttributes.FLASH_SCOPE == name) {
			if (value != GrailsApplicationAttributes.FLASH_SCOPE) {
				SessionProxyFilter.request.setAttribute(GrailsApplicationAttributes.FLASH_SCOPE, value)
				return
			}

			// the filter set the value as the key, so retrieve it from the request
			value = SessionProxyFilter.request.getAttribute(GrailsApplicationAttributes.FLASH_SCOPE)
		}

		try {
			PersistentSession session = PersistentSession.get(sessionId)
			checkInvalidated session
			session.lastAccessedTime = System.currentTimeMillis()

			PersistentSessionAttribute sessionAttributeInstance =
					PersistentSessionAttribute.findOrCreateBySessionIdAndName(sessionId, name)

			sessionAttributeInstance.serialized = persistentSessionService.serializeAttributeValue(value)
			sessionAttributeInstance.save(failOnError: true, flush: true)
		} catch (e) {
			handleException e
		}
	}

	void removeAttribute(String sessionId, String name) throws InvalidatedSessionException {
		if (!name) return;

		try {
			PersistentSession session = PersistentSession.get(sessionId)
			checkInvalidated session
			session.lastAccessedTime = System.currentTimeMillis()

			persistentSessionService.removeAttribute sessionId, name
		}
		catch (e) {
			handleException e
		}
	}

	List<String> getAttributeNames(String sessionId) throws InvalidatedSessionException {
		try {
			persistentSessionService.findAllAttributeNames sessionId
		}
		catch (e) {
			handleException e
		}
	}

	void invalidate(String sessionId) {
		try {
			persistentSessionService.deleteAttributesBySessionId sessionId

			PersistentSession session = PersistentSession.get(sessionId)

			def conf = grailsApplication.config.grails.plugin.databasesession
			def deleteInvalidSessions = conf.deleteInvalidSessions ?: false
			if (deleteInvalidSessions) {
				session?.delete()
			}
			else {
				session?.invalidated = true
			}
		}
		catch (e) {
			handleException e
		}
	}

	long getLastAccessedTime(String sessionId) throws InvalidatedSessionException {
		PersistentSession ps = PersistentSession.get(sessionId)
		checkInvalidated ps
		ps.lastAccessedTime
	}

	void setMaxInactiveInterval(String sessionId, int interval) throws InvalidatedSessionException {
		PersistentSession ps = PersistentSession.get(sessionId)
		checkInvalidated ps

		ps.maxInactiveInterval = interval
		if (interval == 0) {
			invalidate sessionId
		}
	}

	int getMaxInactiveInterval(String sessionId) throws InvalidatedSessionException {
		PersistentSession ps = PersistentSession.get(sessionId)
		checkInvalidated ps
		ps.maxInactiveInterval
	}

	boolean isValid(String sessionId) {
		PersistentSession session = PersistentSession.get(sessionId)
		session && session.isValid()
	}

	protected void handleException(e) {
		if (e instanceof InvalidatedSessionException || e instanceof ValidationException) {
			throw e
		}
		GrailsUtil.deepSanitize e
		log.error e.message, e
	}

	protected void checkInvalidated(PersistentSession ps) {
		if (!ps || ps.invalidated) {
			throw new InvalidatedSessionException()
		}
	}

	protected void checkInvalidated(String sessionId) {
		Boolean invalidated = persistentSessionService.isSessionInvalidated(sessionId)
		if (invalidated == null || invalidated) {
			throw new InvalidatedSessionException()
		}
	}
}