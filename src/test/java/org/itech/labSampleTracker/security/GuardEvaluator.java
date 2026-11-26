package org.itech.labSampleTracker.security;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mockito.Mockito;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.security.access.expression.ExpressionUtils;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.util.SimpleMethodInvocation;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Évalue, sans démarrer l'application, l'expression @PreAuthorize que Spring
 * applique à un point d'accès : celle de la méthode, sinon celle de la classe.
 */
final class GuardEvaluator {

	private static final Set<RequestMethod> WRITES = Set.of(RequestMethod.POST, RequestMethod.PUT,
			RequestMethod.PATCH, RequestMethod.DELETE);

	private final DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
	private final Map<Class<?>, Object> targets = new HashMap<>();

	/** Points d'écriture (POST, PUT, PATCH, DELETE ou toute méthode) des contrôleurs donnés. */
	static List<Method> writes(List<Class<?>> controllers, Set<String> excluded) {
		List<Method> out = new ArrayList<>();
		for (Class<?> c : controllers) {
			for (Method m : c.getDeclaredMethods()) {
				RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
				if (mapping == null || excluded.contains(name(m))) {
					continue;
				}
				boolean anyMethod = mapping.method().length == 0;
				if (anyMethod || Arrays.stream(mapping.method()).anyMatch(WRITES::contains)) {
					out.add(m);
				}
			}
		}
		return out;
	}

	static String name(Method m) {
		return m.getDeclaringClass().getSimpleName() + "." + m.getName();
	}

	static String guard(Method m) {
		PreAuthorize pa = AnnotatedElementUtils.findMergedAnnotation(m, PreAuthorize.class);
		if (pa == null) {
			pa = AnnotatedElementUtils.findMergedAnnotation(m.getDeclaringClass(), PreAuthorize.class);
		}
		return pa == null ? null : pa.value();
	}

	/** L'utilisateur portant cette autorité (ROLE_X en session web, X dans le jeton de l'API) passe-t-il ? */
	boolean allows(Method m, String authority) {
		String expr = guard(m);
		assertNotNull(expr, name(m) + " : écriture sans contrôle");
		Authentication auth = new TestingAuthenticationToken("u", "x", authority);
		Object target = targets.computeIfAbsent(m.getDeclaringClass(), Mockito::mock);
		EvaluationContext ctx = handler.createEvaluationContext(() -> auth,
				new SimpleMethodInvocation(target, m, new Object[m.getParameterCount()]));
		Expression e = handler.getExpressionParser().parseExpression(expr);
		return ExpressionUtils.evaluateAsBoolean(e, ctx);
	}
}
