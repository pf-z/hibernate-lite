package me.pfzh.hibernatelite.metadata;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class MetadataRegistryTest {

    // ==================== 测试实体 ====================

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

    static class NoIdEntity {
        private String name;
    }

    // ==================== 基础缓存行为 ====================

    @Test
    void get_sameInstance_forSameClass() {
        MetadataRegistry registry = new MetadataRegistry();
        EntityMeta m1 = registry.get(EntityA.class);
        EntityMeta m2 = registry.get(EntityA.class);
        assertSame(m1, m2);
    }

    @Test
    void get_returnsDifferentMeta_forDifferentClasses() {
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

    /**
     * 不同 MetadataRegistry 实例之间不共享缓存：
     * 同一个类在各自 registry 里会创建独立的 EntityMeta。
     */
    @Test
    void instancesAreIsolated() {
        MetadataRegistry r1 = new MetadataRegistry();
        MetadataRegistry r2 = new MetadataRegistry();
        EntityMeta m1 = r1.get(EntityA.class);
        EntityMeta m2 = r2.get(EntityA.class);
        assertNotSame(m1, m2);
    }

    // ==================== 非法实体 ====================

    @Test
    void get_invalidEntity_throws() {
        MetadataRegistry registry = new MetadataRegistry();
        assertThrows(HibernateLiteException.class, () -> registry.get(NoIdEntity.class));
    }

    /**
     * 非法实体的异常不会被缓存：
     * 每次 get 都会重新尝试创建并再次抛异常。
     */
    @Test
    void get_invalidEntity_retriesEachTime() {
        MetadataRegistry registry = new MetadataRegistry();
        assertThrows(HibernateLiteException.class, () -> registry.get(NoIdEntity.class));
        assertThrows(HibernateLiteException.class, () -> registry.get(NoIdEntity.class));
    }

    // ==================== 并发 ====================

    @Test
    void concurrentAccess_returnsConsistentInstance() throws InterruptedException {
        MetadataRegistry registry = new MetadataRegistry();
        int threadCount = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        Set<EntityMeta> results = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    results.add(registry.get(EntityA.class));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS), "tasks did not finish in time");

        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS), "pool did not terminate");

        assertEquals(1, results.size(), "并发下应只创建一个 EntityMeta");
    }
}