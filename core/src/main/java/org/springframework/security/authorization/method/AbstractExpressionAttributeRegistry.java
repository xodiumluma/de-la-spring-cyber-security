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

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.aopalliance.intercept.MethodInvocation;

import org.springframework.core.MethodClassKey;
import org.springframework.lang.NonNull;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.util.Assert;

/**
 * For internal use only, as this contract is likely to change
 *
 * @author Evgeniy Cheban
 * @author DingHao
 */
abstract class AbstractExpressionAttributeRegistry<T extends ExpressionAttribute> {

	private final Map<MethodClassKey, T> cachedAttributes = new ConcurrentHashMap<>();

	private MethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();

	private PrePostTemplateDefaults defaults;

	/**
	 * Returns an {@link ExpressionAttribute} for the {@link MethodInvocation}.
	 * @param mi the {@link MethodInvocation} to use
	 * @return the {@link ExpressionAttribute} to use
	 */
	final T getAttribute(MethodInvocation mi) {
		Method method = mi.getMethod();
		Object target = mi.getThis();
		Class<?> targetClass = (target != null) ? target.getClass() : null;
		return getAttribute(method, targetClass);
	}

	/**
	 * Returns an {@link ExpressionAttribute} for the method and the target class.
	 * @param method the method
	 * @param targetClass the target class
	 * @return the {@link ExpressionAttribute} to use
	 */
	final T getAttribute(Method method, Class<?> targetClass) {
		MethodClassKey cacheKey = new MethodClassKey(method, targetClass);
		return this.cachedAttributes.computeIfAbsent(cacheKey, (k) -> resolveAttribute(method, targetClass));
	}

	final <A extends Annotation> Function<AnnotatedElement, A> findUniqueAnnotation(Class<A> type) {
		return (this.defaults != null) ? AuthorizationAnnotationUtils.withDefaults(type, this.defaults)
				: AuthorizationAnnotationUtils.withDefaults(type);
	}

	/**
	 * Returns the {@link MethodSecurityExpressionHandler}.
	 * @return the {@link MethodSecurityExpressionHandler} to use
	 */
	MethodSecurityExpressionHandler getExpressionHandler() {
		return this.expressionHandler;
	}

	void setExpressionHandler(MethodSecurityExpressionHandler expressionHandler) {
		Assert.notNull(expressionHandler, "expressionHandler cannot be null");
		this.expressionHandler = expressionHandler;
	}

	void setTemplateDefaults(PrePostTemplateDefaults defaults) {
		this.defaults = defaults;
	}

	/**
	 * Subclasses should implement this method to provide the non-null
	 * {@link ExpressionAttribute} for the method and the target class.
	 * @param method the method
	 * @param targetClass the target class
	 * @return the non-null {@link ExpressionAttribute}
	 */
	@NonNull
	abstract T resolveAttribute(Method method, Class<?> targetClass);

	Class<?> targetClass(Method method, Class<?> targetClass) {
		return (targetClass != null) ? targetClass : method.getDeclaringClass();
	}

}
