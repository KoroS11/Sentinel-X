package com.sentinelx;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:backendapplicationtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"spring.flyway.enabled=false",
	"jwt.secret=backend_application_test_secret_which_is_long_enough_12345",
	"jwt.expiration-ms=3600000",
	"jwt.refresh-expiration-ms=604800000"
})
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
