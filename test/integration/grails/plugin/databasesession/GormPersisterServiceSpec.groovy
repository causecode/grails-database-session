package grails.plugin.databasesession

import static org.junit.Assert.*
import static groovy.test.GroovyAssert.*
import grails.test.spock.IntegrationSpec

class GormPersisterServiceSpec extends IntegrationSpec {

	GormPersisterService gormPersisterService
	def sessionFactory
	def grailsApplication

	private String id = 'abc'

	def setup() {
		grailsApplication.config.grails.plugin.databasesession.deleteInvalidSessions = false
	}

	void testCreateNew() {

		expect:
		assertNull PersistentSession.get(id)

		when:
		long count = PersistentSession.count()

		gormPersisterService.create id

		then:
		assertEquals count + 1, PersistentSession.count()

		assertNotNull PersistentSession.get(id)
	}

	void testCreateExisting() {

		long count = PersistentSession.count()

		when:
		gormPersisterService.create 'abc'

		then:
		assertEquals count + 1, PersistentSession.count()

		gormPersisterService.create 'abc'

		assertEquals count + 1, PersistentSession.count()
	}

	void testGetAttributeNullName() {
		when:
		gormPersisterService.create id
		then:
		assertNull gormPersisterService.getAttribute('abc', null)
	}

	void testGetAttributeNotFound() {
		when:
		gormPersisterService.create id
		then:
		assertNull gormPersisterService.getAttribute('abc', 'foo')
	}

	void testGetAttributeInvalidated() {
		when:
		gormPersisterService.create id

		PersistentSession.get(id).invalidated = true

		flushAndClear()
		then:
		shouldFail(InvalidatedSessionException) {
			gormPersisterService.getAttribute id, 'foo'
		}
	}

	void testGetAttributeOk() {
		String name = 'foo'
		def value = 42

		when:
		gormPersisterService.create id
		gormPersisterService.setAttribute id, name, value
		then:
		assertEquals value, gormPersisterService.getAttribute(id, name)
	}

	void testSetAttributeNullName() {
		when:
		gormPersisterService.create id
		then:
		shouldFail(IllegalArgumentException) {
			gormPersisterService.setAttribute 'abc', null, 42
		}
	}

	void testSetAttributeNullValue() {
		when:
		gormPersisterService.create id
		gormPersisterService.setAttribute id, 'foo', 42
		then:
		assertEquals 42, gormPersisterService.getAttribute(id, 'foo')

		gormPersisterService.setAttribute id, 'foo', null

		assertNull gormPersisterService.getAttribute(id, 'foo')
	}

	void testSetAttributeOk() {
		when:
		gormPersisterService.create id
		gormPersisterService.setAttribute id, 'foo', 42
		then:
		assertEquals 42, gormPersisterService.getAttribute(id, 'foo')
	}

	void testRemoveAttributeNullName() {
		when:
		int count = PersistentSessionAttribute.count()

		gormPersisterService.create id
		gormPersisterService.setAttribute id, 'foo', 42
		then:
		assertEquals count + 1, PersistentSessionAttribute.count()

		gormPersisterService.removeAttribute id, null

		assertEquals count + 1, PersistentSessionAttribute.count()
	}

	void testRemoveAttributeInvalidated() {
		when:
		int count = PersistentSessionAttribute.count()

		gormPersisterService.create id
		gormPersisterService.setAttribute id, 'foo', 42

		PersistentSession.get(id).invalidated = true

		flushAndClear()
		then:
		shouldFail(InvalidatedSessionException) {
			gormPersisterService.removeAttribute id, 'foo'
		}
	}

	void testRemoveAttributeOk() {
		when:
		int count = PersistentSessionAttribute.count()

		gormPersisterService.create id
		gormPersisterService.setAttribute id, 'foo', 42
		then:
		assertEquals count + 1, PersistentSessionAttribute.count()

		gormPersisterService.removeAttribute id, 'foo'

		assertEquals count, PersistentSessionAttribute.count()
	}

	void testGetAttributeNames() {
		when:
		gormPersisterService.create id
		gormPersisterService.setAttribute id, 'foo', 42
		gormPersisterService.setAttribute id, 'bar', 'wahoo'
		gormPersisterService.setAttribute id, 'baz', 'other'
		then:
		assertEquals(['bar', 'baz', 'foo'], gormPersisterService.getAttributeNames(id).sort())
	}

	void testInvalidateNotDeleting() {
		when:
		gormPersisterService.create id
		gormPersisterService.setAttribute id, 'foo', 42
		gormPersisterService.setAttribute id, 'bar', 'wahoo'

		gormPersisterService.invalidate id

		assertTrue PersistentSession.get(id).invalidated

		checkAttributes()
		then:
		shouldFail(InvalidatedSessionException) {
			gormPersisterService.getLastAccessedTime id
		}
	}

	void testInvalidateDeleting() {
		when:
		grailsApplication.config.grails.plugin.databasesession.deleteInvalidSessions = true

		gormPersisterService.create id
		gormPersisterService.setAttribute id, 'foo', 42
		gormPersisterService.setAttribute id, 'bar', 'wahoo'

		gormPersisterService.invalidate id

		then:
		assertNull PersistentSession.get(id)

		checkAttributes()
		shouldFail(InvalidatedSessionException) {
			gormPersisterService.getLastAccessedTime id
		}
	}

	private void checkAttributes() {
		assertEquals 0, PersistentSessionAttribute.executeQuery(
			'select count(*) from PersistentSessionAttribute psa where psa.sessionId=:sessionId',
			[sessionId: id])[0]
	}

	void testGetLastAccessedTime() {
		when:
		gormPersisterService.create id

		long lastAccessed = gormPersisterService.getLastAccessedTime(id)

		sleep 500

		PersistentSession session = PersistentSession.get(id)

		gormPersisterService.getAttribute(id, 'foo')
		then:
		assertTrue gormPersisterService.getLastAccessedTime(id) > lastAccessed
	}

	void testMaxInactiveInterval() {
		when:
		gormPersisterService.create id

		assertEquals 30, gormPersisterService.getMaxInactiveInterval(id)

		gormPersisterService.setMaxInactiveInterval id, 15
		then:
		assertEquals 15, gormPersisterService.getMaxInactiveInterval(id)

		assertFalse PersistentSession.get(id).invalidated
		gormPersisterService.setMaxInactiveInterval id, 0
		assertTrue PersistentSession.get(id).invalidated
	}

	void testIsValid() {
		when:
		assertFalse gormPersisterService.isValid(id)

		gormPersisterService.create id
		then:
		assertTrue gormPersisterService.isValid(id)

		gormPersisterService.invalidate id

		assertFalse gormPersisterService.isValid(id)
	}

	boolean isValid(String sessionId) {
		PersistentSession session = PersistentSession.get(sessionId)
		session && !session.invalidated
	}


	private void flushAndClear() {
		sessionFactory.currentSession.flush()
		sessionFactory.currentSession.clear()
	}
}
