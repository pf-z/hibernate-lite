package me.pfzh.hibernatelite;

import me.pfzh.hibernatelite.internal.CrudExecutor;
import me.pfzh.hibernatelite.internal.DataStoreImpl;
import me.pfzh.hibernatelite.internal.SessionFactoryHolder;
import me.pfzh.hibernatelite.internal.TransactionTemplate;
import me.pfzh.hibernatelite.metadata.MetadataRegistry;
import me.pfzh.hibernatelite.query.JpqlExecutor;
import me.pfzh.hibernatelite.query.QueryExecutor;
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
 * Entry point for bootstrapping Hibernate-Lite.
 *
 * <p>This class hides Hibernate initialization details and provides
 * a simple builder-based API for creating a DataStore.</p>
 *
 * <p>The created DataStore manages CRUD operations, transactions,
 * and Hibernate resource lifecycle internally.</p>
 *
 * @author Pengfei Zhang
 * @since 2026/9/18
 */
public final class HibernateLite {

    /**
     * Utility class. Instances are not allowed.
     */
    private HibernateLite() {}

    /**
     * Creates a new configuration builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for configuring and creating a Hibernate-Lite instance.
     *
     * <p>The builder collects application configuration first,
     * then creates all Hibernate components during {@link #build()}.</p>
     */
    public static final class Builder {

        /**
         * Database connection provider.
         */
        private DataSource dataSource;

        /**
         * Entity classes managed by Hibernate.
         */
        private Class<?>[] entities;

        /**
         * Whether Hibernate should print generated SQL.
         */
        private boolean showSql = false;

        /**
         * Database schema management strategy.
         *
         * <p>"validate" is used by default because it only checks
         * schema consistency and does not modify existing tables.</p>
         */
        private String ddlAuto = "validate";   // ← 默认安全

        private Builder() {}

        /**
         * Sets the database DataSource.
         */
        public Builder dataSource(DataSource dataSource) {
            this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
            return this;
        }

        /**
         * Registers entity classes managed by Hibernate.
         *
         * @param entities classes annotated with @Entity
         */
        public Builder entities(Class<?>... entities) {
            Objects.requireNonNull(entities, "entities cannot be null");
            if (entities.length == 0) {
                throw new IllegalArgumentException("At least one entity is required");
            }
            this.entities = entities;
            return this;
        }

        /**
         * Enables or disables SQL logging.
         */
        public Builder showSql(boolean showSql) {
            this.showSql = showSql;
            return this;
        }

        /**
         * Sets Hibernate schema generation strategy.
         *
         * <p>Common values:</p>
         * <ul>
         *     <li>none - do nothing</li>
         *     <li>validate - check schema only</li>
         *     <li>update - automatically update schema</li>
         *     <li>create - recreate schema</li>
         *     <li>create-drop - create on startup and drop on shutdown</li>
         * </ul>
         */
        public Builder ddlAuto(String ddlAuto) {
            this.ddlAuto = Objects.requireNonNull(ddlAuto, "ddlAuto cannot be null");
            return this;
        }

        /**
         * Builds a complete Hibernate-Lite instance.
         *
         * <p>This method creates:</p>
         * <ul>
         *     <li>Hibernate SessionFactory</li>
         *     <li>SessionFactory lifecycle holder</li>
         *     <li>CRUD executor</li>
         *     <li>Transaction manager</li>
         * </ul>
         */
        public DataStore build() {
            if (dataSource == null) {
                throw new IllegalStateException("DataSource must be configured");
            }
            if (entities == null || entities.length == 0) {
                throw new IllegalStateException("At least one entity must be registered");
            }

            // Create the Hibernate core factory.
            SessionFactory sessionFactory = buildSessionFactory();
            try {
                // Wrap SessionFactory to centralize lifecycle management.
                SessionFactoryHolder holder = new SessionFactoryHolder(sessionFactory);
                // Shared metadata registry for the DataStore lifecycle.
                MetadataRegistry metadataRegistry = new MetadataRegistry();
                TransactionTemplate txTemplate = new TransactionTemplate(holder);
                // Components share the same SessionFactory holder and metadata registry.
                CrudExecutor crud = new CrudExecutor(metadataRegistry, txTemplate);
                TransactionManager tx = new TransactionManager(holder);
                QueryExecutor query = new QueryExecutor(metadataRegistry, txTemplate);   // ← 新增
                JpqlExecutor jpql = new JpqlExecutor(txTemplate);              // ← 新增
                // Expose simplified API to users.
                return new DataStoreImpl(crud, tx, query, jpql, holder);
            } catch (RuntimeException e) {
                sessionFactory.close();
                throw e;
            }
        }

        /**
         * Creates Hibernate SessionFactory from the provided configuration.
         *
         * <p>This is the only place where Hibernate native bootstrap
         * APIs are used.</p>
         */
        private SessionFactory buildSessionFactory() {

            Properties props = new Properties();

            // Use external DataSource instead of managing connections manually.
            props.put(AvailableSettings.DATASOURCE, dataSource);

            // Enable or disable SQL output.
            props.put(AvailableSettings.SHOW_SQL, showSql);

            // Configure database schema handling.
            props.put(AvailableSettings.HBM2DDL_AUTO, ddlAuto);

            /*
             * Create Hibernate service environment.
             *
             * This initializes internal services such as:
             * - connection handling
             * - transaction services
             * - SQL generation
             */
            StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                    .applySettings(props)
                    .build();

            try {
                MetadataSources sources = createSources(registry);

                /*
                 * Build metadata and create SessionFactory.
                 *
                 * SessionFactory is heavyweight and normally
                 * exists as a singleton during application lifetime.
                 */
                return sources.buildMetadata().buildSessionFactory();
            } catch (RuntimeException e) {

                /*
                 * Initialization failed.
                 *
                 * Release Hibernate registry resources to avoid leaks.
                 */
                StandardServiceRegistryBuilder.destroy(registry);
                throw e;
            }
        }

        private MetadataSources createSources(StandardServiceRegistry registry) {
            MetadataSources sources = new MetadataSources(registry);

            /*
             * Register entity mappings.
             *
             * Hibernate needs these classes to understand
             * the relationship between Java objects and tables.
             */
            for (int i = 0; i < entities.length; i++) {
                Class<?> entity = entities[i];
                if (entity == null) {
                    throw new IllegalArgumentException("Entity at index " + i + " is null");
                }
                sources.addAnnotatedClass(entity);
            }
            return sources;
        }
    }

}