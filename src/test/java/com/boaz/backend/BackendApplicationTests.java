package com.boaz.backend;

import com.boaz.backend.support.TestcontainersBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests extends TestcontainersBase {

	@Test
	void contextLoads() {
	}

}
