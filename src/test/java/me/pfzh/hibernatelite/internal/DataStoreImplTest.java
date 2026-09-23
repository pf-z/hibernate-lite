package me.pfzh.hibernatelite.internal;

import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.fixture.TestUser;
import me.pfzh.hibernatelite.query.JpqlExecutor;
import me.pfzh.hibernatelite.query.JpqlQuery;
import me.pfzh.hibernatelite.query.QueryExecutor;
import me.pfzh.hibernatelite.transaction.TransactionCallback;
import me.pfzh.hibernatelite.transaction.TransactionManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataStoreImplTest {

    private CrudExecutor crud;
    private TransactionManager tx;
    private QueryExecutor queryExecutor;
    private JpqlExecutor jpqlExecutor;
    private SessionFactoryHolder holder;
    private SessionFactory sessionFactory;
    private DataStoreImpl store;

    @BeforeEach
    void setUp() {
        crud = mock(CrudExecutor.class);
        tx = mock(TransactionManager.class);
        queryExecutor = mock(QueryExecutor.class);
        jpqlExecutor = mock(JpqlExecutor.class);
        sessionFactory = mock(SessionFactory.class);
        holder = new SessionFactoryHolder(sessionFactory);
        store = new DataStoreImpl(crud, tx, queryExecutor, jpqlExecutor, holder);
    }

    // ==================== CRUD 委托 ====================

    @Test
    void find_delegatesToCrud() {
        TestUser u = new TestUser("A");
        when(crud.find(TestUser.class, 1L)).thenReturn(u);

        assertSame(u, store.find(TestUser.class, 1L));
    }

    @Test
    void save_delegatesToCrud() {
        TestUser u = new TestUser("A");
        when(crud.save(u)).thenReturn(u);

        assertSame(u, store.save(u));
    }

    @Test
    void saveAll_delegatesToCrud() {
        List<TestUser> users = List.of(new TestUser("A"));
        when(crud.saveAll(users)).thenReturn(users);

        assertSame(users, store.saveAll(users));
    }

    @Test
    void delete_delegatesToCrud() {
        TestUser u = new TestUser("A");
        store.delete(u);
        verify(crud).delete(u);
    }

    // ==================== 事务委托 ====================

    @Test
    void transaction_returnsValue() {
        TransactionCallback<String> cb = () -> "ok";
        when(tx.execute(cb)).thenReturn("ok");

        assertEquals("ok", store.transaction(cb));
    }

    @Test
    void transaction_runnable_usesDefaultMethod() {
        when(tx.execute(any())).thenReturn(null);

        store.transaction(() -> { });

        verify(tx).execute(any());
    }

    // ==================== query (DSL) ====================

    @Test
    void query_dsl_returnsLambdaQuery() {
        var q = store.query(TestUser.class);
        assertNotNull(q);
    }

    @Test
    void query_dsl_nullType_throws() {
        assertThrows(NullPointerException.class, () -> store.query((Class<TestUser>) null));
    }

    // ==================== query (JPQL) ====================

    @Test
    void query_jpql_returnsJpqlQuery() {
        JpqlQuery<TestUser> q = store.query("SELECT u FROM TestUser u", TestUser.class);
        assertNotNull(q);
    }

    @Test
    void query_jpql_nullJpql_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> store.query((String) null, TestUser.class));
    }

    @Test
    void query_jpql_blankJpql_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> store.query("   ", TestUser.class));
    }

    @Test
    void query_jpql_nullResultType_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> store.query("SELECT u FROM TestUser u", null));
    }

    // ==================== unwrap ====================

    @Test
    void unwrap_sessionFactory_returnsIt() {
        assertSame(sessionFactory, store.unwrap(SessionFactory.class));
    }

    @Test
    void unwrap_unsupportedType_throws() {
        assertThrows(HibernateLiteException.class,
                () -> store.unwrap(String.class));
    }

    @Test
    void unwrap_nullType_throws() {
        assertThrows(IllegalArgumentException.class, () -> store.unwrap(null));
    }

    // ==================== 构造器 ====================

    @Test
    void constructor_rejectsNull() {
        assertThrows(NullPointerException.class,
                () -> new DataStoreImpl(null, tx, queryExecutor, jpqlExecutor, holder));
        assertThrows(NullPointerException.class,
                () -> new DataStoreImpl(crud, null, queryExecutor, jpqlExecutor, holder));
        assertThrows(NullPointerException.class,
                () -> new DataStoreImpl(crud, tx, null, jpqlExecutor, holder));
        assertThrows(NullPointerException.class,
                () -> new DataStoreImpl(crud, tx, queryExecutor, null, holder));
        assertThrows(NullPointerException.class,
                () -> new DataStoreImpl(crud, tx, queryExecutor, jpqlExecutor, null));
    }

    // ==================== close ====================

    @Test
    void close_delegatesToHolder() {
        store.close();

        verify(sessionFactory, times(1)).isClosed();
        verify(sessionFactory, times(1)).close();
    }

    @Test
    void close_isIdempotent() {
        when(sessionFactory.isClosed()).thenReturn(false, true);

        store.close();
        store.close();

        verify(sessionFactory, times(1)).close();
    }
}