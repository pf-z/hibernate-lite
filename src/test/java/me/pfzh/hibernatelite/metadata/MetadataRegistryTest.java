package me.pfzh.hibernatelite.metadata;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MetadataRegistryTest {

    @Entity
    static class EntityA {
        @Id
        private Long id;
    }

    @Entity
    static class EntityB {
        @Id
        private Long id;
    }

    @Test
    void get_sameInstance_forSameClass() {
        MetadataRegistry registry = new MetadataRegistry();
        EntityMeta m1 = registry.get(EntityA.class);
        EntityMeta m2 = registry.get(EntityA.class);
        assertSame(m1, m2);
    }

    @Test
    void get_differentInstances_forDifferentClasses() {
        MetadataRegistry registry = new MetadataRegistry();
        EntityMeta m1 = registry.get(EntityA.class);
        EntityMeta m2 = registry.get(EntityB.class);
        assertNotSame(m1, m2);
        assertEquals(EntityA.class, m1.getEntityClass());
        assertEquals(EntityB.class, m2.getEntityClass());
    }

    @Test
    void get_null_throws() {
        MetadataRegistry registry = new MetadataRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.get(null));
    }

    @Test
    void instancesAreIsolated() {
        MetadataRegistry r1 = new MetadataRegistry();
        MetadataRegistry r2 = new MetadataRegistry();
        EntityMeta m1 = r1.get(EntityA.class);
        EntityMeta m2 = r2.get(EntityA.class);
        assertNotSame(m1, m2);   // 不同实例独立
    }

    @Test
    void concurrentAccess_returnsConsistentInstance() throws InterruptedException {
        MetadataRegistry registry = new MetadataRegistry();
        int threadCount = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        java.util.Set<EntityMeta> results = java.util.concurrent.ConcurrentHashMap.newKeySet();

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    results.add(registry.get(EntityA.class));
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(1, results.size(), "并发下应只创建一个 EntityMeta");
    }
}