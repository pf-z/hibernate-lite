package me.pfzh.hibernatelite.internal;

import me.pfzh.hibernatelite.exception.HibernateLiteException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.resource.transaction.spi.TransactionStatus;

/**
 * 线程绑定的事务上下文。
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>用 {@link ThreadLocal} 管理当前线程的 {@link Session}</li>
 *   <li>支持嵌套事务（depth 计数，最外层才真正 commit）</li>
 *   <li>区分"库打开的 Session"和"外部传入的 Session"，只关自己开的</li>
 *   <li>方法返回/异常时清理 ThreadLocal，避免线程池泄漏</li>
 *   <li>支持 markRollbackOnly：内层失败时标记整个事务回滚</li>
 * </ul>
 *
 * <p><b>本类方法为内部 API</b>，只允许 {@code CrudExecutor} 和
 * {@code TransactionManager} 调用。用户代码请勿直接使用。</p>
 *
 * <p><b>线程安全</b>：所有状态都在 ThreadLocal 中，天然线程隔离。</p>
 */
public final class SessionContext {

    private static final ThreadLocal<Holder> CURRENT = new ThreadLocal<>();

    /** 禁止实例化 */
    private SessionContext() {}

    // ==================== 内部状态 ====================

    private static final class Holder {
        Session session;
        Transaction tx;
        int depth;
        boolean owner;      // true = 本库打开的，负责关闭
    }

    // ==================== 对外方法 ====================

    /**
     * 获取当前线程的 Session，没有则创建。
     */
    public static Session current(SessionFactory factory) {
        Holder h = CURRENT.get();
        if (h == null) {
            h = new Holder();
            h.session = factory.openSession();
            h.owner = true;
            CURRENT.set(h);
        }
        return h.session;
    }

    /**
     * 开启事务。若已在事务中，只增加深度计数（支持嵌套）。
     *
     * <p>只有最外层（depth 从 0 变 1）才真正调用
     * {@link Session#beginTransaction()}。</p>
     */
    public static void begin(SessionFactory factory) {
        Holder h = CURRENT.get();
        if (h == null) {
            h = new Holder();
            h.session = factory.openSession();
            h.owner = true;
            CURRENT.set(h);
        }
        if (h.depth == 0) {
            try {
                h.tx = h.session.beginTransaction();
            } catch (RuntimeException e) {
                closeIfOwner(h);
                throw new HibernateLiteException("开启事务失败", e);
            }
        }
        h.depth++;
    }

    /**
     * 提交事务。若仍处于嵌套中（depth > 1），只减少深度，不真正提交。
     *
     * <p>最外层提交前会检查事务是否被标记为 rollback-only：</p>
     * <ul>
     *   <li>已标记 → 回滚，抛异常</li>
     *   <li>未标记 → 正常提交</li>
     * </ul>
     */
    public static void commit() {
        Holder h = CURRENT.get();
        if (h == null) {
            throw new HibernateLiteException("commit 时无事务上下文");
        }
        if (h.depth <= 0) {
            throw new HibernateLiteException("commit 时事务深度为 0，状态不一致");
        }

        h.depth--;

        if (h.depth > 0) {
            // 嵌套中，不提交
            return;
        }

        // 最外层：处理提交
        if (h.tx == null) {
            closeIfOwner(h);
            return;
        }

        // 已被标记为回滚
        if (h.tx.getStatus() == TransactionStatus.MARKED_ROLLBACK) {
            try {
                h.tx.rollback();
            } catch (RuntimeException ignored) {
                // 回滚失败无法补救
            } finally {
                h.tx = null;
                closeIfOwner(h);
            }
            throw new HibernateLiteException("事务被标记为回滚");
        }

        // 正常提交
        try {
            if (h.tx.isActive()) {
                h.tx.commit();
            }
        } catch (RuntimeException e) {
            tryRollback(h);
            closeIfOwner(h);
            throw new HibernateLiteException("提交事务失败", e);
        } finally {
            h.tx = null;
            closeIfOwner(h);
        }
    }

    /**
     * 回滚事务。
     *
     * <p><b>嵌套语义</b>：</p>
     * <ul>
     *   <li>最外层（depth &lt;= 1）→ 直接回滚 + 关闭 Session</li>
     *   <li>嵌套中（depth &gt; 1）→ 只标记 rollback-only，等最外层统一回滚</li>
     * </ul>
     */
    public static void rollback() {
        Holder h = CURRENT.get();
        if (h == null) return;

        if (h.depth <= 1) {
            // 最外层或不在事务：直接回滚 + 关闭
            tryRollback(h);
            h.depth = 0;
            closeIfOwner(h);
        } else {
            // 嵌套中：标记 rollback-only，深度递减，让最外层处理
            h.depth--;
            markRollbackOnlyInternal(h);
        }
    }

    /**
     * 标记当前事务为 rollback-only。
     *
     * <p>用于内层操作失败但异常被吞掉的场景，保证最外层 commit 时回滚。</p>
     */
    public static void markRollbackOnly() {
        Holder h = CURRENT.get();
        markRollbackOnlyInternal(h);
    }

    /**
     * 当前线程是否在事务中。
     */
    public static boolean inTransaction() {
        Holder h = CURRENT.get();
        return h != null && h.depth > 0;
    }

    /**
     * 若当前不在事务中，关闭 Session 并清理 ThreadLocal。
     *
     * <p>供不需要事务的只读操作显式清理资源。</p>
     */
    public static void close() {
        Holder h = CURRENT.get();
        if (h == null) return;
        if (h.depth > 0) return;   // 有活动事务，不关
        closeIfOwner(h);
    }

    /**
     * 【测试专用】强制清理当前线程的 ThreadLocal。
     *
     * <p>包级私有，仅供 internal 包内的测试调用，不属于公开 API。</p>
     */
    static void reset() {
        Holder h = CURRENT.get();
        if (h == null) return;
        try {
            if (h.tx != null && h.tx.isActive()) {
                h.tx.rollback();
            }
        } catch (RuntimeException ignored) {
            // 测试清理，静默
        }
        closeIfOwner(h);
    }

    // ==================== 内部工具 ====================

    private static void markRollbackOnlyInternal(Holder h) {
        if (h == null || h.tx == null) return;
        try {
            h.tx.markRollbackOnly();
        } catch (RuntimeException ignored) {
            // 标记失败静默，不掩盖原始异常
        }
    }

    private static void tryRollback(Holder h) {
        if (h.tx != null && h.tx.isActive()) {
            try {
                h.tx.rollback();
            } catch (RuntimeException ignored) {
                // 回滚失败无法补救，静默忽略，不掩盖原始异常
            }
        }
    }

    private static void closeIfOwner(Holder h) {
        if (h.owner && h.session != null) {
            try {
                if (h.session.isOpen()) {
                    h.session.close();
                }
            } catch (RuntimeException ignored) {
                // 关闭失败无法补救
            }
        }
        CURRENT.remove();
    }
}