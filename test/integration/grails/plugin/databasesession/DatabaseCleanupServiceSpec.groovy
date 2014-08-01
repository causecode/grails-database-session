package grails.plugin.databasesession

import static org.junit.Assert.*
import grails.test.spock.IntegrationSpec

class DatabaseCleanupServiceSpec extends IntegrationSpec {

	DatabaseCleanupService databaseCleanupService
	PersistentSessionService persistentSessionService
	def grailsApplication
	def sessionFactory

	def setup() {
		def session = new PersistentSession(creationTime: 0, lastAccessedTime: System.currentTimeMillis())
		session.id = 's1'
		session.save(failOnError: true)
		def attr = new PersistentSessionAttribute([sessionId: session.id, name: 'a1',
			serialized: persistentSessionService.serializeAttributeValue('v1')]).save(failOnError: true)

		session = new PersistentSession(creationTime: 0, lastAccessedTime: System.currentTimeMillis())
		session.id = 's2'
		session.save(failOnError: true)
		attr = new PersistentSessionAttribute([sessionId: session.id, name: 'a2',
			serialized: persistentSessionService.serializeAttributeValue('v2')]).save(failOnError: true)

		session = new PersistentSession(creationTime: 0, lastAccessedTime: System.currentTimeMillis())
		session.id = 's3'
		session.save(failOnError: true)

		flushAndClear()
	}

	def cleanup() {
	}

	void testCleanup() {

		expect:
		assertEquals 3, PersistentSession.count()
		assertEquals 2, PersistentSessionAttribute.count()

		when:
		def session = PersistentSession.get('s1')
		session.lastAccessedTime = System.currentTimeMillis() - 20000
		session.save(flush: true)

		databaseCleanupService.cleanup()

		then:
		assertEquals 3, PersistentSession.count()
		assertEquals 2, PersistentSessionAttribute.count()

		when:
		session.lastAccessedTime = System.currentTimeMillis() - 2000000
		session.save(flush: true)

		then:
		databaseCleanupService.cleanup()

		assertEquals 2, PersistentSession.count()
		assertEquals 1, PersistentSessionAttribute.count()
	}

	private void flushAndClear() {
		sessionFactory.currentSession.flush()
		sessionFactory.currentSession.clear()
	}
}
