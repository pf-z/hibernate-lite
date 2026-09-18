package me.pfzh.hibernatelite;

import me.pfzh.hibernatelite.internal.CrudExecutor;
import me.pfzh.hibernatelite.internal.DataStoreImpl;
import me.pfzh.hibernatelite.internal.SessionFactoryHolder;
import me.pfzh.hibernatelite.transaction.TransactionManager;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.Properties;

/**
 * Hibernate-Lite 引导入口。
 */
public final class HibernateLite {

    private HibernateLite() {}

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private DataSource dataSource;
        private Class<?>[] entities;
        private boolean showSql = false;
        private String ddlAuto = "validate";   // ← 默认安全

        private Builder() {}

        public Builder dataSource(DataSource dataSource) {
            this.dataSource = Objects.requireNonNull(dataSource, "dataSource 不能为 null");
            return this;
        }

        public Builder entities(Class<?>... entities) {
            Objects.requireNonNull(entities, "entities 不能为 null");
            if (entities.length == 0) {
                throw new IllegalArgumentException("至少注册一个实体类");
            }
            this.entities = entities;
            return this;
        }

        public Builder showSql(boolean showSql) {
            this.showSql = showSql;
            return this;
        }

        /**
         * 设置 Hibernate DDL 模式。默认 {@code "validate"}（不改表）。
         * 开发时可设为 {@code "update"} 自动建表。
         *
         * <p>可选值：{@code "none"} / {@code "validate"} / {@code "update"}
         * / {@code "create"} / {@code "create-drop"}</p>
         */
        public Builder ddlAuto(String ddlAuto) {
            this.ddlAuto = Objects.requireNonNull(ddlAuto, "ddlAuto 不能为 null");
            return this;
        }

        public DataStore build() {
            if (dataSource == null) {
                throw new IllegalStateException("必须配置 dataSource");
            }
            if (entities == null || entities.length == 0) {
                throw new IllegalStateException("必须注册至少一个实体类");
            }

            SessionFactory sessionFactory = buildSessionFactory();
            SessionFactoryHolder holder = new SessionFactoryHolder(sessionFactory);
            CrudExecutor crud = new CrudExecutor(holder);
            TransactionManager tx = new TransactionManager(holder);

            return new DataStoreImpl(crud, tx, holder);
        }

        private SessionFactory buildSessionFactory() {
            Properties props = new Properties();
            props.put(AvailableSettings.DATASOURCE, dataSource);
            props.put(AvailableSettings.SHOW_SQL, showSql);
            props.put(AvailableSettings.HBM2DDL_AUTO, ddlAuto);

            StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                    .applySettings(props)
                    .build();

            try {
                MetadataSources sources = new MetadataSources(registry);
                for (int i = 0; i < entities.length; i++) {
                    Class<?> entity = entities[i];
                    if (entity == null) {
                        throw new IllegalArgumentException("entities 第 " + i + " 个为 null");
                    }
                    sources.addAnnotatedClass(entity);
                }
                return sources.buildMetadata().buildSessionFactory();
            } catch (RuntimeException e) {
                StandardServiceRegistryBuilder.destroy(registry);
                throw e;
            }
        }
    }
}