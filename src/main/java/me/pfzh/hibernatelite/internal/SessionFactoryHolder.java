package me.pfzh.hibernatelite.internal;

import org.hibernate.SessionFactory;

/**
 * 全局 {@link SessionFactory} 持有者。
 *
 * <p>生命周期与应用一致：由 {@code HibernateLite.Builder} 构建时创建，
 * 应用关闭时调用 {@link #close()} 释放。</p>
 *
 * <p>本类不可变、线程安全。不管理 Session，不参与事务。</p>
 */
public final class SessionFactoryHolder implements AutoCloseable {

    private final SessionFactory factory;

    public SessionFactoryHolder(SessionFactory factory) {
        if (factory == null) {
            throw new IllegalArgumentException("SessionFactory 不能为 null");
        }
        this.factory = factory;
    }

    /**
     * 获取全局唯一的 SessionFactory。
     */
    public SessionFactory get() {
        return factory;
    }

    /**
     * 关闭 SessionFactory，释放连接池等资源。
     * 幂等：重复调用安全。
     */
    @Override
    public void close() {
        if (!factory.isClosed()) {
            factory.close();
        }
    }

}
