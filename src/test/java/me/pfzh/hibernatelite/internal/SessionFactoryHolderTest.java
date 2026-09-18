package me.pfzh.hibernatelite.internal;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionFactoryHolderTest {

    @Test
    void get_returnsSameInstance() {
        SessionFactory sf = mock(SessionFactory.class);
        SessionFactoryHolder holder = new SessionFactoryHolder(sf);
        assertSame(sf, holder.get());
    }

    @Test
    void constructor_rejectsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new SessionFactoryHolder(null));
    }

    @Test
    void close_closesFactory() {
        SessionFactory sf = mock(SessionFactory.class);
        when(sf.isClosed()).thenReturn(false);

        new SessionFactoryHolder(sf).close();

        verify(sf, times(1)).close();
    }

    @Test
    void close_isIdempotent() {
        SessionFactory sf = mock(SessionFactory.class);
        when(sf.isClosed()).thenReturn(false, true);

        SessionFactoryHolder holder = new SessionFactoryHolder(sf);
        holder.close();
        holder.close();

        verify(sf, times(1)).close();
    }
}