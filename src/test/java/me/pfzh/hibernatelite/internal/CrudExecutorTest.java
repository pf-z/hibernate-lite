package me.pfzh.hibernatelite.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.fixture.TestUser;
import me.pfzh.hibernatelite.metadata.MetadataRegistry;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.resource.transaction.spi.TransactionStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CrudExecutorTest {

    private SessionFactory sf;
    private Session session;
    private Transaction tx;
    private CrudExecutor crud;

    @BeforeEach
    void setUp() {
        sf = mock(SessionFactory.class);
        session = mock(Session.class);
        tx = mock(Transaction.class);

        when(sf.openSession()).thenReturn(session);
        when(session.beginTransaction()).thenReturn(tx);
        when(session.isOpen()).thenReturn(true);
        when(tx.isActive()).thenReturn(true);

        // ★ 改这里
        MetadataRegistry registry = new MetadataRegistry();
        TransactionTemplate txTemplate = new TransactionTemplate(new SessionFactoryHolder(sf));
        crud = new CrudExecutor(registry, txTemplate);
    }

    @AfterEach
    void cleanup() {
        SessionContext.reset();
    }

    // ============================================================
    // find
    // ============================================================

    @Test
    void find_delegatesToSession_andAutoCommits() {
        TestUser expected = new TestUser("Alice");
        when(session.find(TestUser.class, 1L)).thenReturn(expected);

        TestUser result = crud.find(TestUser.class, 1L);

        assertSame(expected, result);
        verify(session, times(1)).find(TestUser.class, 1L);
        verify(tx, times(1)).commit();
        verify(session, times(1)).close();
    }

    @Test
    void find_nullArgs_throws() {
        assertThrows(IllegalArgumentException.class, () -> crud.find(null, 1L));
        assertThrows(IllegalArgumentException.class, () -> crud.find(TestUser.class, null));
        verify(sf, never()).openSession();
    }

    @Test
    void find_hibernateException_isWrapped() {
        when(session.find(TestUser.class, 1L))
                .thenThrow(new HibernateException("db down"));

        HibernateLiteException ex = assertThrows(HibernateLiteException.class,
                () -> crud.find(TestUser.class, 1L));

        assertTrue(ex.getMessage().contains("find failed"),
                "Expected message to contain 'find failed', actual: " + ex.getMessage());
        assertInstanceOf(HibernateException.class, ex.getCause());
    }

    // ============================================================
    // save
    // ============================================================

    @Test
    void save_newEntity_usesPersistAndAutoCommits() {
        TestUser u = new TestUser("Alice");   // id == null

        TestUser result = crud.save(u);

        assertSame(u, result);
        verify(session, times(1)).persist(u);
        verify(session, never()).merge(any());
        verify(tx, times(1)).commit();
        verify(session, times(1)).close();
    }

    @Test
    void save_existingEntity_usesMergeAndReturnsMerged() {
        TestUser u = new TestUser("Alice");
        u.setId(1L);
        TestUser merged = new TestUser("Alice");
        merged.setId(1L);
        when(session.merge(u)).thenReturn(merged);

        TestUser result = crud.save(u);

        assertSame(merged, result);
        verify(session, times(1)).merge(u);
        verify(session, never()).persist(any());
        verify(tx, times(1)).commit();
        verify(session, times(1)).close();
    }

    @Test
    void save_inTransaction_doesNotAutoCommit() {
        TestUser u = new TestUser("Alice");
        SessionContext.begin(sf);

        crud.save(u);

        verify(tx, never()).commit();
        verify(session, never()).close();   // 外部事务持有 session，不关

        SessionContext.commit();
        verify(tx, times(1)).commit();
        verify(session, times(1)).close();
    }

    @Test
    void save_exception_rollsBack() {
        TestUser u = new TestUser("Alice");
        doThrow(new RuntimeException("db error")).when(session).persist(u);

        assertThrows(RuntimeException.class, () -> crud.save(u));

        verify(tx, times(1)).rollback();
        verify(tx, never()).commit();
        verify(session, times(1)).close();
    }

    /**
     * 外层已有事务时，内层操作失败：
     * - 不立即回滚外层
     * - 只标记 rollback-only
     * - session 不关
     *
     * 注意：这里只验证「标记」行为。
     * 「外层 commit 时因 rollback-only 而回滚」由 SessionContextTest 覆盖，
     * 避免在本测试里 mock tx.getStatus() 造成耦合。
     */
    @Test
    void save_exceptionInsideOuterTransaction_marksRollbackOnly() {
        TestUser u = new TestUser("Alice");
        doThrow(new RuntimeException("db error")).when(session).persist(u);

        SessionContext.begin(sf);

        assertThrows(RuntimeException.class, () -> crud.save(u));

        verify(tx, never()).rollback();
        verify(tx, times(1)).markRollbackOnly();
        verify(session, never()).close();

        // 手动清理，避免影响其他测试
        SessionContext.rollback();
        verify(tx, times(1)).rollback();
    }

    @Test
    void save_entityWithoutId_throws() {
        assertThrows(HibernateLiteException.class, () -> crud.save(new NoId()));
        verify(tx, times(1)).rollback();
    }

    @Test
    void save_businessKeyWithNonNewId_throws() {
        BusinessKeyEntity e = new BusinessKeyEntity();
        e.username = "Alice";

        HibernateLiteException ex = assertThrows(HibernateLiteException.class,
                () -> crud.save(e));

        assertTrue(ex.getMessage().contains("business identifier"),
                "Expected message to contain 'business identifier', actual: " + ex.getMessage());
    }

    // ============================================================
    // saveAll
    // ============================================================

    @Test
    void saveAll_returnsMergedResults() {
        TestUser u1 = new TestUser("A"); u1.setId(1L);
        TestUser u2 = new TestUser("B"); u2.setId(2L);
        TestUser m1 = new TestUser("A"); m1.setId(1L);
        TestUser m2 = new TestUser("B"); m2.setId(2L);

        when(session.merge(u1)).thenReturn(m1);
        when(session.merge(u2)).thenReturn(m2);

        List<TestUser> result = crud.saveAll(List.of(u1, u2));

        assertEquals(2, result.size());
        assertSame(m1, result.get(0));
        assertSame(m2, result.get(1));
        verify(tx, times(1)).commit();
        verify(session, times(1)).close();
    }

    @Test
    void saveAll_exactlyBatchSize_flushesOnce() {
        List<TestUser> users = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            TestUser u = new TestUser("u" + i);
            u.setId((long) i);
            users.add(u);
        }
        for (TestUser u : users) {
            when(session.merge(u)).thenReturn(u);
        }

        crud.saveAll(users);

        verify(session, times(1)).flush();
        verify(session, times(1)).clear();
        verify(tx, times(1)).commit();
        verify(session, times(1)).close();
    }

    @Test
    void saveAll_overBatchSize_flushesAndClears() {
        List<TestUser> users = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            TestUser u = new TestUser("u" + i);
            u.setId((long) i);
            users.add(u);
        }
        for (TestUser u : users) {
            when(session.merge(u)).thenReturn(u);
        }

        crud.saveAll(users);

        verify(session, times(2)).flush();
        verify(session, times(2)).clear();
        verify(tx, times(1)).commit();
        verify(session, times(1)).close();
    }

    @Test
    void saveAll_emptyList_returnsImmediately() {
        List<TestUser> result = crud.saveAll(List.of());

        assertTrue(result.isEmpty());
        verify(sf, never()).openSession();
        verifyNoInteractions(tx);
    }

    @Test
    void saveAll_nullElement_throwsWithIndex() {
        List<TestUser> users = new ArrayList<>();
        users.add(new TestUser("A"));
        users.add(null);

        HibernateLiteException ex = assertThrows(HibernateLiteException.class,
                () -> crud.saveAll(users));

        assertTrue(ex.getMessage().contains("index 1"),
                "Expected message to contain 'index 1', actual: " + ex.getMessage());
        verify(tx, times(1)).rollback();
    }

    @Test
    void saveAll_nullList_throws() {
        assertThrows(IllegalArgumentException.class, () -> crud.saveAll(null));
        verify(sf, never()).openSession();
    }

    // ============================================================
    // delete
    // ============================================================

    @Test
    void delete_managedEntity_removesDirectly() {
        TestUser u = new TestUser("A");
        when(session.contains(u)).thenReturn(true);

        crud.delete(u);

        verify(session, times(1)).remove(u);
        verify(session, never()).get(any(Class.class), any());
        verify(tx, times(1)).commit();
        verify(session, times(1)).close();
    }

    @Test
    void delete_detachedEntity_loadsByIdThenRemoves() {
        TestUser u = new TestUser("A");
        u.setId(1L);
        when(session.contains(u)).thenReturn(false);
        when(session.get(TestUser.class, 1L)).thenReturn(u);

        crud.delete(u);

        verify(session, times(1)).get(TestUser.class, 1L);
        verify(session, times(1)).remove(u);
        verify(tx, times(1)).commit();
        verify(session, times(1)).close();
    }

    @Test
    void delete_detachedEntity_notFound_isIdempotent() {
        TestUser u = new TestUser("A");
        u.setId(1L);
        when(session.contains(u)).thenReturn(false);
        when(session.get(TestUser.class, 1L)).thenReturn(null);

        assertDoesNotThrow(() -> crud.delete(u));

        verify(session, never()).remove(any());
        verify(tx, times(1)).commit();
        verify(session, times(1)).close();
    }

    @Test
    void delete_detachedEntityWithoutId_throws() {
        TestUser u = new TestUser("A");   // id == null
        when(session.contains(u)).thenReturn(false);

        HibernateLiteException ex = assertThrows(HibernateLiteException.class,
                () -> crud.delete(u));

        assertTrue(ex.getMessage().contains("Entity ID is null"),
                "Expected message to contain 'Entity ID is null', actual: " + ex.getMessage());
        verify(session, never()).remove(any());
        verify(tx, times(1)).rollback();
    }

    @Test
    void delete_nullEntity_throws() {
        assertThrows(IllegalArgumentException.class, () -> crud.delete(null));
        verify(sf, never()).openSession();
    }

    // ============================================================
    // 内部类：用于边界测试
    // ============================================================

    /**
     * 非实体类：既无 {@code @Entity} 也无 {@code @Id}。
     *
     * <p>注意：不能加 {@code @Entity}，否则 Hibernate 启动时会因缺少
     * {@code @Id} 而报 "Persistent entity should have primary key"。</p>
     */
    static class NoId {
    }

    /**
     * 业务主键（无 {@code @GeneratedValue}），用于测试自动 save 语义的拒绝。
     */
    @Entity
    static class BusinessKeyEntity {
        @Id
        private String username;
    }
}