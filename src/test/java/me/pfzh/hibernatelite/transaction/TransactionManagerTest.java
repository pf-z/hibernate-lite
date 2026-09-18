package me.pfzh.hibernatelite.transaction;

import me.pfzh.hibernatelite.internal.SessionContext;
import me.pfzh.hibernatelite.internal.SessionFactoryHolder;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransactionManagerTest {

    private SessionFactory sf;
    private Session session;
    private Transaction tx;
    private TransactionManager txManager;

    @BeforeEach
    void setUp() {
        sf = mock(SessionFactory.class);
        session = mock(Session.class);
        tx = mock(Transaction.class);

        when(sf.openSession()).thenReturn(session);
        when(session.beginTransaction()).thenReturn(tx);
        when(session.isOpen()).thenReturn(true);
        when(tx.isActive()).thenReturn(true);

        txManager = new TransactionManager(new SessionFactoryHolder(sf));
    }

    @AfterEach
    void cleanup() {
        SessionContext.rollback();
    }

    @Test
    void execute_normal_commitsAndReturnsResult() {
        String result = txManager.execute(() -> "ok");

        assertEquals("ok", result);
        verify(tx, times(1)).commit();
        verify(tx, never()).rollback();
    }

    @Test
    void execute_exception_rollsBackAndRethrows() {
        RuntimeException original = new RuntimeException("boom");

        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                txManager.execute(() -> { throw original; })
        );

        assertSame(original, thrown);
        verify(tx, times(1)).rollback();
        verify(tx, never()).commit();
    }

    @Test
    void execute_nested_usesOuterTransaction() {
        AtomicBoolean innerRan = new AtomicBoolean(false);

        txManager.execute(() -> {
            txManager.execute(() -> {
                innerRan.set(true);
                return null;
            });
            return null;
        });

        assertTrue(innerRan.get());
        verify(session, times(1)).beginTransaction();
        verify(tx, times(1)).commit();
    }

    @Test
    void execute_innerException_rollsBackWholeOuter() {
        assertThrows(RuntimeException.class, () ->
                txManager.execute(() -> {
                    txManager.execute(() -> { throw new RuntimeException("inner"); });
                    return null;
                })
        );

        verify(tx, times(1)).rollback();
        verify(tx, never()).commit();
    }

    @Test
    void execute_nullCallback_throws() {
        assertThrows(NullPointerException.class,
                () -> txManager.execute((TransactionCallback<Object>) null));
    }

    @Test
    void constructor_rejectsNull() {
        assertThrows(NullPointerException.class,
                () -> new TransactionManager(null));
    }
}
