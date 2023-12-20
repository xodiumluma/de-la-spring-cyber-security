/*
 * Copyright 2002-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.security.config.annotation.method.configuration;

import io.micrometer.observation.ObservationRegistry;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.context.annotation.Role;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.security.access.hierarchicalroles.NullRoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authorization.AuthoritiesAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.authorization.method.Jsr250AuthorizationManager;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

/**
 * {@link Configuration} for enabling JSR-250 Spring Security Method Security.
 *
 * @author Evgeniy Cheban
 * @author Josh Cummings
 * @since 5.6
 * @see EnableMethodSecurity
 */
@Configuration(proxyBeanMethods = false)
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
final class Jsr250MethodSecurityConfiguration implements ImportAware {

	private int interceptorOrderOffset;

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	static MethodInterceptor jsr250AuthorizationMethodInterceptor(
			ObjectProvider<GrantedAuthorityDefaults> defaultsProvider,
			ObjectProvider<SecurityContextHolderStrategy> strategyProvider,
			ObjectProvider<ObservationRegistry> registryProvider, ObjectProvider<RoleHierarchy> roleHierarchyProvider,
			Jsr250MethodSecurityConfiguration configuration) {
		Jsr250AuthorizationManager jsr250 = new Jsr250AuthorizationManager();
		AuthoritiesAuthorizationManager authoritiesAuthorizationManager = new AuthoritiesAuthorizationManager();
		RoleHierarchy roleHierarchy = roleHierarchyProvider.getIfAvailable(NullRoleHierarchy::new);
		authoritiesAuthorizationManager.setRoleHierarchy(roleHierarchy);
		jsr250.setAuthoritiesAuthorizationManager(authoritiesAuthorizationManager);
		defaultsProvider.ifAvailable((d) -> jsr250.setRolePrefix(d.getRolePrefix()));
		SecurityContextHolderStrategy strategy = strategyProvider
			.getIfAvailable(SecurityContextHolder::getContextHolderStrategy);
		AuthorizationManager<MethodInvocation> manager = new DeferringObservationAuthorizationManager<>(
				registryProvider, jsr250);
		AuthorizationManagerBeforeMethodInterceptor interceptor = AuthorizationManagerBeforeMethodInterceptor
			.jsr250(manager);
		interceptor.setOrder(interceptor.getOrder() + configuration.interceptorOrderOffset);
		interceptor.setSecurityContextHolderStrategy(strategy);
		return interceptor;
	}

	@Override
	public void setImportMetadata(AnnotationMetadata importMetadata) {
		EnableMethodSecurity annotation = importMetadata.getAnnotations().get(EnableMethodSecurity.class).synthesize();
		this.interceptorOrderOffset = annotation.offset();
	}

}
