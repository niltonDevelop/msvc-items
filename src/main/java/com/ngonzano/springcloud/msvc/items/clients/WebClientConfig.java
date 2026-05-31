package com.ngonzano.springcloud.msvc.items.clients;

/*
 * Duplicado de config.WebClientConfig: mismo bean name 'webClientConfig' → ConflictingBeanDefinitionException.
 * Usar com.ngonzano.springcloud.msvc.items.config.WebClientConfig (baseUrl + /api/v1).
 *
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

	@Bean
	@LoadBalanced
	@Primary
	WebClient.Builder webClientBuilder() {
		return WebClient.builder();
	}
}
*/
