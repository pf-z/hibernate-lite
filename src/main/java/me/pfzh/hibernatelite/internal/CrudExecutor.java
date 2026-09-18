package me.pfzh.hibernatelite.internal;

import jakarta.persistence.Id;
import me.pfzh.hibernatelite.exception.HibernateLiteException;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class CrudExecutor {

    private static final int BATCH_SIZE = 50;

    private final SessionFactoryHolder factoryHolder;

    public CrudExecutor(SessionFactoryHolder factoryHolder) {
        if (factoryHolder == null) {
            throw new IllegalArgumentException("SessionFactoryHolder 不能为 null");
        }
        this.factoryHolder = factoryHolder;
    }

    // ==================== 读操作 ====================

    public <T> T find(Class<T> type, Object id) {
        requireNonNull(type, "实体类型");
        requireNonNull(id, "主键");
        return wrap("find", () -> execute(() ->
                SessionContext.current(factoryHolder.get()).find(type, id)));
    }

    // ==================== 写操作 ====================

    public <T> T save(T entity) {
        requireNonNull(entity, "实体");
        return wrap("save", () -> execute(() ->
                doSave(SessionContext.current(factoryHolder.get()), entity)));
    }

    public <T> List<T> saveAll(List<T> entities) {
        requireNonNull(entities, "实体列表");
        if (entities.isEmpty()) return entities;
        return wrap("saveAll", () -> execute(() ->
                doSaveAll(SessionContext.current(factoryHolder.get()), entities)));
    }

    public void delete(Object entity) {
        requireNonNull(entity, "实体");
        wrap("delete", () -> {
            execute(() -> {
                Session session = SessionContext.current(factoryHolder.get());
                if (session.contains(entity)) {
                    session.remove(entity);
                    return null;
                }
                // 游离态：按 ID 加载后删除
                Object id = readId(entity);
                if (id == null) {
                    throw new HibernateLiteException(
                            "删除的实体 ID 为 null: " + entity.getClass().getName());
                }
                Object managed = session.get(entity.getClass(), id);
                if (managed == null) {
                    // 数据库无此记录，幂等返回
                    return null;
                }
                session.remove(managed);
                return null;
            });
            return null;
        });
    }

    // ==================== 内部：统一事务 + 异常 ====================

    private <R> R execute(Supplier<R> action) {
        SessionFactory sf = factoryHolder.get();
        boolean autoTx = !SessionContext.inTransaction();
        if (autoTx) SessionContext.begin(sf);
        try {
            R result = action.get();
            if (autoTx) SessionContext.commit();
            return result;
        } catch (RuntimeException e) {
            if (autoTx) {
                SessionContext.rollback();
            } else {
                SessionContext.markRollbackOnly();
            }
            throw e;
        }
    }

    private <R> R wrap(String operation, Supplier<R> action) {
        try {
            return action.get();
        } catch (HibernateLiteException e) {
            throw e;
        } catch (HibernateException e) {
            throw new HibernateLiteException(operation + " 失败", e);
        }
    }

    // ==================== 内部：实现 ====================

    @SuppressWarnings("unchecked")
    private <T> T doSave(Session session, T entity) {
        Object id = readId(entity);
        if (id == null) {
            session.persist(entity);
            return entity;
        }
        return (T) session.merge(entity);
    }

    private <T> List<T> doSaveAll(Session session, List<T> entities) {
        List<T> result = new ArrayList<>(entities.size());
        for (int i = 0; i < entities.size(); i++) {
            T entity = entities.get(i);
            if (entity == null) {
                throw new HibernateLiteException("批量保存时第 " + i + " 条为 null");
            }
            result.add(doSave(session, entity));
            if ((i + 1) % BATCH_SIZE == 0) {
                session.flush();
                session.clear();
            }
        }
        return result;
    }

    private Object readId(Object entity) {
        Class<?> clazz = entity.getClass();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(Id.class)) {
                    f.setAccessible(true);
                    try {
                        return f.get(entity);
                    } catch (IllegalAccessException e) {
                        throw new HibernateLiteException(
                                "读取 @Id 失败: " + c.getName() + "#" + f.getName(), e);
                    } catch (RuntimeException e) {
                        throw new HibernateLiteException(
                                "反射访问 @Id 失败: " + c.getName() + "#" + f.getName(), e);
                    }
                }
            }
        }
        throw new HibernateLiteException("实体缺少 @Id: " + clazz.getName());
    }

    private static void requireNonNull(Object v, String name) {
        if (v == null) {
            throw new IllegalArgumentException(name + " 不能为 null");
        }
    }
}