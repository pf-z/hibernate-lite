package me.pfzh.hibernatelite.internal;

import me.pfzh.hibernatelite.exception.HibernateLiteException;
import me.pfzh.hibernatelite.fixture.TestUser;
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
    private SessionFactoryHolder holder;
    private SessionFactory sessionFactory;
    private DataStoreImpl store;

    @BeforeEach
    void setUp() {
        crud = mock(CrudExecutor.class);
        tx = mock(TransactionManager.class);
        sessionFactory = mock(SessionFactory.class);
        holder = new SessionFactoryHolder(sessionFactory);
        store = new DataStoreImpl(crud, tx, holder);
    }

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

    @Test
    void constructor_rejectsNull() {
        assertThrows(NullPointerException.class,
                () -> new DataStoreImpl(null, tx, holder));
        assertThrows(NullPointerException.class,
                () -> new DataStoreImpl(crud, null, holder));
        assertThrows(NullPointerException.class,
                () -> new DataStoreImpl(crud, tx, null));
    }

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