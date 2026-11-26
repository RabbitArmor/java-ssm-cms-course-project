package edu.course.lab.cms.app;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import edu.course.lab.cms.platform.persistence.TimestampFillHandler;
import javax.sql.DataSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.bind.annotation.ControllerAdvice;

@Configuration
@EnableTransactionManagement
@EnableAspectJAutoProxy
@ComponentScan(basePackages = {
        "edu.course.lab.cms.platform", "edu.course.lab.cms.identity",
        "edu.course.lab.cms.taxonomy", "edu.course.lab.cms.content"
}, excludeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION,
        classes = {Controller.class, ControllerAdvice.class}))
@MapperScan(basePackages = "edu.course.lab.cms", annotationClass = Mapper.class)
public class PersistenceConfig {
    @Bean(destroyMethod = "close")
    public HikariDataSource dataSource(Environment environment) {
        var config = new HikariConfig();
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl(environment.getRequiredProperty("CMS_DB_URL"));
        config.setUsername(environment.getRequiredProperty("CMS_DB_USER"));
        config.setPassword(environment.getRequiredProperty("CMS_DB_PASSWORD"));
        config.setPoolName("cms-db");
        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(3_000);
        config.setInitializationFailTimeout(-1);
        config.setConnectionInitSql("SET time_zone = '+00:00'");
        return new HikariDataSource(config);
    }

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource, TimestampFillHandler timestamps) throws Exception {
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        var pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(100L);
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(pagination);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        var global = GlobalConfigUtils.defaults();
        global.setMetaObjectHandler(timestamps);
        factory.setGlobalConfig(global);
        factory.setPlugins(interceptor);
        var mapperResources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/**/*.xml");
        if (mapperResources.length > 0) {
            factory.setMapperLocations(mapperResources);
        }
        return factory.getObject();
    }

    @Bean
    public DataSourceTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
