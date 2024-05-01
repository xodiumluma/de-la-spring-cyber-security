/*
 * Copyright 2002-2024 the original author or authors.
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

package org.springframework.security.authorization.method;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Function;

import reactor.util.annotation.NonNull;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.expression.Expression;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.Assert;

/**
 * For internal use only, as this contract is likely to change.
 *
 * @author Evgeniy Cheban
 * @author DingHao
 * @since 5.8
 */
final class PreAuthorizeExpressionAttributeRegistry extends AbstractExpressionAttributeRegistry<ExpressionAttribute> {

	private final MethodAuthorizationDeniedHandler defaultHandler = new ThrowingMethodAuthorizationDeniedHandler();

	private Function<Class<? extends MethodAuthorizationDeniedHandler>, MethodAuthorizationDeniedHandler> handlerResolver;

	PreAuthorizeExpressionAttributeRegistry() {
		this.handlerResolver = (clazz) -> this.defaultHandler;
	}

	@NonNull
	@Override
	ExpressionAttribute resolveAttribute(Method method, Class<?> targetClass) {
		Method specificMethod = AopUtils.getMostSpecificMethod(method, targetClass);
		PreAuthorize preAuthorize = findPreAuthorizeAnnotation(specificMethod, targetClass);
		if (preAuthorize == null) {
			return ExpressionAttribute.NULL_ATTRIBUTE;
		}
		Expression expression = getExpressionHandler().getExpressionParser().parseExpression(preAuthorize.value());
		MethodAuthorizationDeniedHandler handler = resolveHandler(method, targetClass);
		return new PreAuthorizeExpressionAttribute(expression, handler);
	}

	private MethodAuthorizationDeniedHandler resolveHandler(Method method, Class<?> targetClass) {
		Function<AnnotatedElement, HandleAuthorizationDenied> lookup = AuthorizationAnnotationUtils
			.withDefaults(HandleAuthorizationDenied.class);
		HandleAuthorizationDenied deniedHandler = lookup.apply(method);
		if (deniedHandler != null) {
			return this.handlerResolver.apply(deniedHandler.handlerClass());
		}
		deniedHandler = lookup.apply(targetClass(method, targetClass));
		if (deniedHandler != null) {
			return this.handlerResolver.apply(deniedHandler.handlerClass());
		}
		return this.defaultHandler;
	}

	private PreAuthorize findPreAuthorizeAnnotation(Method method, Class<?> targetClass) {
		Function<AnnotatedElement, PreAuthorize> lookup = findUniqueAnnotation(PreAuthorize.class);
		PreAuthorize preAuthorize = lookup.apply(method);
		return (preAuthorize != null) ? preAuthorize : lookup.apply(targetClass(method, targetClass));
	}

	/**
	 * Uses the provided {@link ApplicationContext} to resolve the
	 * {@link MethodAuthorizationDeniedHandler} from {@link PreAuthorize}.
	 * @param context the {@link ApplicationContext} to use
	 */
	void setApplicationContext(ApplicationContext context) {
		Assert.notNull(context, "context cannot be null");
		this.handlerResolver = (clazz) -> resolveHandler(context, clazz);
	}

	private MethodAuthorizationDeniedHandler resolveHandler(ApplicationContext context,
			Class<? extends MethodAuthorizationDeniedHandler> handlerClass) {
		if (handlerClass == this.defaultHandler.getClass()) {
			return this.defaultHandler;
		}
		String[] beanNames = context.getBeanNamesForType(handlerClass);
		if (beanNames.length == 0) {
			throw new IllegalStateException("Could not find a bean of type " + handlerClass.getName());
		}
		if (beanNames.length > 1) {
			throw new IllegalStateException("Expected to find a single bean of type " + handlerClass.getName()
					+ " but found " + Arrays.toString(beanNames));
		}
		return context.getBean(beanNames[0], handlerClass);
	}

}
