package com.segue.backend;

import com.segue.backend.config.DotenvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SegueBackendApplication {

	public static void main(String[] args) {
		DotenvLoader.load();
		SpringApplication.run(SegueBackendApplication.class, args);
	}

}
