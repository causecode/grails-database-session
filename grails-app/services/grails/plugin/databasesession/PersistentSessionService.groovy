package grails.plugin.databasesession

import javax.annotation.PostConstruct;

import org.springframework.transaction.annotation.Transactional
import org.springframework.util.Assert

/**
 * @author Burt Beckwith
 */
class PersistentSessionService {

	static transactional = false

	def grailsApplication

	boolean useMongo

	@PostConstruct
	void postConstruct() {
		useMongo = grailsApplication.config.grails.plugin.databasesession.persistence.provider == "mongodb"
	}

	def deserializeAttributeValue(byte[] serialized) {
		if (!serialized) {
			return null
		}

		// might throw IOException - let the caller handle it
		new ObjectInputStream(new ByteArrayInputStream(serialized)) {
			@Override
			protected Class<?> resolveClass(ObjectStreamClass objectStreamClass) throws IOException, ClassNotFoundException {
				Class.forName objectStreamClass.name, true, Thread.currentThread().contextClassLoader
			}
		}.readObject()
	}

	byte[] serializeAttributeValue(value) {
		if (value == null) {
			return null
		}

		ByteArrayOutputStream baos = new ByteArrayOutputStream()
		// might throw IOException - let the caller handle it
		new ObjectOutputStream(baos).writeObject value
		baos.toByteArray()
	}

	PersistentSessionAttribute findValueBySessionAndAttributeName(PersistentSession session, String name) {
		findValueBySessionIdAndAttributeName(session.id, name)
	}

	PersistentSessionAttribute findValueBySessionIdAndAttributeName(String sessionId, String name) {
        return PersistentSessionAttribute.findBySessionIdAndName(sessionId, name)
	}

	List<PersistentSessionAttribute> findValuesBySession(String sessionId) {
		Assert.hasLength sessionId
        PersistentSessionAttribute.findAllBySessionId(sessionId, [sort: "id"])
	}

	void deleteAttributesBySessionId(String sessionId) {
		if (useMongo) {
			PersistentSessionAttribute.collection.remove([sessionId: sessionId])
		} else {
			PersistentSessionAttribute.executeQuery("DELETE FROM PersistentSessionAttribute a WHERE a.sessionId = ?", [sessionId])
		}
	}

	void deleteAttributesBySessionIds(sessionIds) {
		if (!sessionIds) {
			return
		}

		if (useMongo) {
			PersistentSessionAttribute.collection.remove([sessionId: [$in: sessionIds]])
		} else {
			PersistentSessionAttribute.executeQuery("DELETE FROM PersistentSessionAttribute a WHERE a.sessionId IN ?", [sessionIds])
		}
	}

	void removeAttribute(String sessionId, String name) {
		PersistentSessionAttribute.findBySessionIdAndName(sessionId, name)?.delete(flush: true)
	}

	List<String> findAllAttributeNames(String sessionId) {
		PersistentSessionAttribute.findAllBySessionId(sessionId)*.name
	}

	List<String> findAllSessionIdsByLastAccessedOlderThan(long age) {
		PersistentSession.withCriteria {
			projections {
				if (useMongo) {
					id()
				} else {
					property("id")
				}
			}
			lt("lastAccessedTime", age)
		}
	}

	void deleteSessionsByIds(ids) {
		if (!ids) {
			return
		}

		if (useMongo) {
			PersistentSession.collection.remove([_id: [$in: ids]])
		} else {
			PersistentSession.executeQuery("DELETE FROM PersistentSession s WHERE s.id IN ?", [ids])
		}

		deleteAttributesBySessionIds(ids)
	}

	Boolean isSessionInvalidated(String sessionId) {
		PersistentSession.withCriteria {
			projections {
				property("invalidated")
			}
			eq("id", sessionId)
		}
	}
}