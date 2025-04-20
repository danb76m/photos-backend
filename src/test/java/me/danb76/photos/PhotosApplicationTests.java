package me.danb76.photos;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.mockito.Mockito;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;

@SpringBootTest
@EnableAutoConfiguration(exclude = {
		org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
		org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
		org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration.class,
		org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration.class,
		org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class,
		org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration.class,
		org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration.class
})
class PhotosApplicationTests {

	@Configuration
	static class TestConfig {
		@Bean
		DataSource dataSource() {
			return Mockito.mock(DataSource.class);
		}

		@Bean
		EntityManagerFactory entityManagerFactory() {
			return Mockito.mock(EntityManagerFactory.class);
		}
	}

	@Test
	void contextLoads(ApplicationContext context) {

	}
}