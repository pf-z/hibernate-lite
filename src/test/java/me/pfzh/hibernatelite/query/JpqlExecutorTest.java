package me.pfzh.hibernatelite.query;

import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.internal.SessionFactoryHolder;
import me.pfzh.hibernatelite.internal.SessionContext;
import me.pfzh.hibernatelite.internal.TransactionTemplate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class JpqlExecutorTest {

    private SessionFactory sf;
    private Session session;
    private Transaction tx;
    private JpqlExecutor executor;

    @BeforeEach
    void setUp() {
        sf = mock(SessionFactory.class);
        session = mock(Session.class);
        tx = mock(Transaction.class);

        when(sf.openSession()).thenReturn(session);
        when(session.beginTransaction()).thenReturn(tx);
        when(session.isOpen()).thenReturn(true);
        when(tx.isActive()).thenReturn(true);

        TransactionTemplate txTemplate = new TransactionTemplate(new SessionFactoryHolder(sf));
        executor = new JpqlExecutor(txTemplate);
    }

    @AfterEach
    void cleanup() {
        SessionContext.close();
    }

    // ==================== 构造器 ====================

    @Test
    void constructor_nullTemplate_throws() {
        assertThrows(IllegalArgumentException.class, () -> new JpqlExecutor(null));
    }

    // ==================== 参数校验 ====================

    @Test
    void list_nullJpql_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> executor.list(null, Object.class, Map.of()));
    }

    @Test
    void list_blankJpql_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> executor.list("   ", Object.class, Map.of()));
    }

    @Test
    void list_nullResultType_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> executor.list("SELECT u FROM User u", null, Map.of()));
    }

    @Test
    void list_zeroLimit_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> executor.list("SELECT u FROM User u", Object.class, Map.of(), 0));
    }

    @Test
    void count_nullJpql_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> executor.count(null, Map.of()));
    }

    // ==================== 异常包装 ====================

    @Test
    void list_hibernateException_isWrapped() {
        when(session.createQuery(anyString(), ArgumentMatchers.<Class<Object>>any()))
                .thenThrow(new org.hibernate.HibernateException("db down"));

        HibernateLiteException ex = assertThrows(HibernateLiteException.class,
                () -> executor.list("SELECT u FROM User u", Object.class, Map.of()));

        assertTrue(ex.getMessage().contains("jpql.list failed"));
        assertInstanceOf(org.hibernate.HibernateException.class, ex.getCause());
    }
}