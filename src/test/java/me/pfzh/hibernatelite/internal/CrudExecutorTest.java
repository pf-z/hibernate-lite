package me.pfzh.hibernatelite.internal;

import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.fixture.TestUser;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
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

        crud = new CrudExecutor(new SessionFactoryHolder(sf));
    }

    @AfterEach
    void cleanup() {
        SessionContext.reset();
    }

    // ---------- find ----------

    @Test
    void find_delegatesToSession() {
        TestUser expected = new TestUser("Alice");
        when(session.find(TestUser.class, 1L)).thenReturn(expected);

        TestUser result = crud.find(TestUser.class, 1L);

        assertSame(expected, result);
    }

    @Test
    void find_nullArgs_throws() {
        assertThrows(IllegalArgumentException.class, () -> crud.find(null, 1L));
        assertThrows(IllegalArgumentException.class, () -> crud.find(TestUser.class, null));
    }

    // ---------- save ----------

    @Test
    void save_newEntity_usesPersistAndAutoCommits() {
        TestUser u = new TestUser("Alice");   // id == null

        TestUser result = crud.save(u);

        assertSame(u, result);
        verify(session, times(1)).persist(u);
        verify(session, never()).merge(any());
        verify(tx, times(1)).commit();
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
    }

    @Test
    void save_inTransaction_doesNotAutoCommit() {
        TestUser u = new TestUser("Alice");
        SessionContext.begin(sf);

        crud.save(u);

        verify(tx, never()).commit();

        SessionContext.commit();
        verify(tx, times(1)).commit();
    }

    @Test
    void save_exception_rollsBack() {
        TestUser u = new TestUser("Alice");
        doThrow(new RuntimeException("db error")).when(session).persist(u);

        assertThrows(RuntimeException.class, () -> crud.save(u));

        verify(tx, times(1)).rollback();
        verify(tx, never()).commit();
    }

    // ---------- saveAll ----------

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
    }

    @Test
    void saveAll_emptyList_returnsImmediately() {
        List<TestUser> result = crud.saveAll(List.of());

        assertTrue(result.isEmpty());
        verify(sf, never()).openSession();
    }

    @Test
    void saveAll_nullElement_throws() {
        List<TestUser> users = new ArrayList<>();
        users.add(new TestUser("A"));
        users.add(null);

        assertThrows(HibernateLiteException.class, () -> crud.saveAll(users));
        verify(tx, times(1)).rollback();
    }

    // ---------- delete ----------

    @Test
    void delete_managedEntity_removesDirectly() {
        TestUser u = new TestUser("A");
        when(session.contains(u)).thenReturn(true);

        crud.delete(u);

        verify(session, times(1)).remove(u);
        verify(session, never()).get(any(Class.class), any());
    }

    @Test
    void delete_detachedEntity_loadsByIdThenRemoves() {
        TestUser u = new TestUser("A");
        u.setId(1L);                                            // ← 必须有 ID
        when(session.contains(u)).thenReturn(false);
        when(session.get(TestUser.class, 1L)).thenReturn(u);    // ← mock get

        crud.delete(u);

        verify(session, times(1)).get(TestUser.class, 1L);
        verify(session, times(1)).remove(u);
    }

    // ---------- 无 @Id ----------

    static class NoId {}

    @Test
    void save_entityWithoutId_throws() {
        assertThrows(HibernateLiteException.class, () -> crud.save(new NoId()));
    }
}