package grails.plugin.databasesession

class DatabaseCleanupServiceTests extends GroovyTestCase {

	DatabaseCleanupService databaseCleanupService
	PersistentSessionService persistentSessionService
	def grailsApplication
	def sessionFactory

	protected void setUp() {
		super.setUp()

		def session = new PersistentSession(creationTime: 0, lastAccessedTime: System.currentTimeMillis())
		session.id = 's1'
		session.save(failOnError: true)
		def attr = new PersistentSessionAttribute([sessionId: session.id, name: 'a1',
			serialized: persistentSessionService.serializeAttributeValue('v1')]).save(failOnError: true)

		session = new PersistentSession(creationTime: 0, lastAccessedTime: System.currentTimeMillis())
		session.id = 's2'
		session.save(failOnError: true)
		attr = new PersistentSessionAttribute([session: session, name: 'a2',
			serialized: persistentSessionService.serializeAttributeValue('v2')]).save(failOnError: true)

		session = new PersistentSession(creationTime: 0, lastAccessedTime: System.currentTimeMillis())
		session.id = 's3'
		session.save(failOnError: true)

		flushAndClear()
	}

	void testCleanup() {

		assertEquals 3, PersistentSession.count()
		assertEquals 2, PersistentSessionAttribute.count()

		def session = PersistentSession.get('s1')
		session.lastAccessedTime = System.currentTimeMillis() - 20000
		session.save(flush: true)

		databaseCleanupService.cleanup()

		assertEquals 3, PersistentSession.count()
		assertEquals 2, PersistentSessionAttribute.count()

		session.lastAccessedTime = System.currentTimeMillis() - 2000000
		session.save(flush: true)

		databaseCleanupService.cleanup()

		assertEquals 2, PersistentSession.count()
		assertEquals 1, PersistentSessionAttribute.count()
	}

	private void flushAndClear() {
		sessionFactory.currentSession.flush()
		sessionFactory.currentSession.clear()
	}
}
