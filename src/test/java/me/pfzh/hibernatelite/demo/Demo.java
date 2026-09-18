package me.pfzh.hibernatelite.demo;

import com.zaxxer.hikari.HikariDataSource;
import me.pfzh.hibernatelite.DataStore;
import me.pfzh.hibernatelite.HibernateLite;
import me.pfzh.hibernatelite.fixture.TestUser;

public class Demo {

    public static void main(String[] args) {
        // 1. 创建 Hikari 数据源
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setDriverClassName("org.h2.Driver");

        // 2. 启动库
        DataStore db = HibernateLite.builder()
                .dataSource(ds)
                .entities(TestUser.class)
                .ddlAuto("update")       // Demo 用内存库，自动建表
                .showSql(true)
                .build();

        // 3. 单条保存
        TestUser u = db.save(new TestUser("Alice"));
        System.out.println("saved id = " + u.getId());

        // 4. 查询
        TestUser found = db.find(TestUser.class, u.getId());
        System.out.println("found = " + found.getName());

        // 5. 批量保存
        db.saveAll(java.util.List.of(
                new TestUser("Bob"),
                new TestUser("Charlie")
        ));

        // 6. 事务（有返回值）
        Long id = db.transaction(() -> {
            TestUser dave = db.save(new TestUser("Dave"));
            return dave.getId();
        });
        System.out.println("transaction saved id = " + id);

        // 7. 删除
        db.delete(found);

        // 8. 关闭
        db.unwrap(org.hibernate.SessionFactory.class).close();
        ds.close();

        System.out.println("✅ Demo 跑通");
    }
}