package dev.jorgemiguel.nutrikit.shared.tenancy;

import jakarta.persistence.EntityManagerFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;

/** Reemplaza el {@code JpaTransactionManager} que configura Spring Boot por uno que conoce el consultorio. */
@Configuration(proxyBeanMethods = false)
class TenancyConfig {

	@Bean
	JpaTransactionManager transactionManager(EntityManagerFactory emf) {
		return new JpaTransactionManagerConTenant(emf);
	}

}
