package me.pfzh.hibernatelite.internal;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionContextTest {

    private SessionFactory sf;
    private Session session;
    private Transaction tx;

    @BeforeEach
    void setUp() {
        sf = mock(SessionFactory.class);
        session = mock(Session.class);
        tx = mock(Transaction.class);

        when(sf.openSession()).thenReturn(session);
        when(session.beginTransaction()).thenReturn(tx);
        when(session.isOpen()).thenReturn(true);
        when(tx.isActive()).thenReturn(true);
    }

    @AfterEach
    void cleanup() {
        SessionContext.reset();
    }

    @Test
    void current_createsSessionOnce() {
        Session s1 = SessionContext.current(sf);
        Session s2 = SessionContext.current(sf);

        assertSame(s1, s2);
        verify(sf, times(1)).openSession();
    }

    @Test
    void begin_opensTransactionAtDepthZero() {
        SessionContext.begin(sf);

        verify(session, times(1)).beginTransaction();
        assertTrue(SessionContext.inTransaction());
    }

    @Test
    void begin_nested_doesNotOpenNewTransaction() {
        SessionContext.begin(sf);
        SessionContext.begin(sf);
        SessionContext.begin(sf);

        verify(session, times(1)).beginTransaction();
    }

    @Test
    void commit_nested_onlyCommitsAtOutermost() {
        SessionContext.begin(sf);
        SessionContext.begin(sf);

        SessionContext.commit();
        verify(tx, never()).commit();

        SessionContext.commit();
        verify(tx, times(1)).commit();
    }

    @Test
    void commit_closesSessionAndClearsThreadLocal() {
        SessionContext.begin(sf);
        SessionContext.commit();

        verify(session, times(1)).close();
        assertFalse(SessionContext.inTransaction());
    }

    @Test
    void commitWithoutBegin_throws() {
        assertThrows(RuntimeException.class, SessionContext::commit);
    }

    @Test
    void rollback_rollsBackAndCloses() {
        SessionContext.begin(sf);
        SessionContext.rollback();

        verify(tx, times(1)).rollback();
        verify(session, times(1)).close();
        assertFalse(SessionContext.inTransaction());
    }

    @Test
    void rollback_nested_decrementsDepth() {
        SessionContext.begin(sf);
        SessionContext.begin(sf);
        SessionContext.begin(sf);   // depth = 3

        SessionContext.rollback();   // depth = 2
        assertTrue(SessionContext.inTransaction());

        SessionContext.rollback();   // depth = 1
        assertTrue(SessionContext.inTransaction());

        SessionContext.rollback();   // depth = 0 → 真正回滚 + 关闭
        assertFalse(SessionContext.inTransaction());
        verify(tx, times(1)).rollback();
        verify(session, times(1)).close();
    }

    @Test
    void rollback_nested_marksRollbackOnly() {
        SessionContext.begin(sf);
        SessionContext.begin(sf);

        SessionContext.rollback();

        verify(tx, times(1)).markRollbackOnly();
        verify(tx, never()).rollback();
        verify(session, never()).close();
    }

    @Test
    void rollback_withoutContext_isNoop() {
        SessionContext.rollback();
    }

    @Test
    void begin_whenSessionOpenFails_cleansUp() {
        when(session.beginTransaction()).thenThrow(new RuntimeException("db down"));

        assertThrows(RuntimeException.class, () -> SessionContext.begin(sf));

        SessionContext.current(sf);
        verify(sf, times(2)).openSession();
    }
}