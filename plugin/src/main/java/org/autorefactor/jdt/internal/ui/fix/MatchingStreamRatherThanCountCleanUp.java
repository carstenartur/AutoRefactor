/*
 * AutoRefactor - Eclipse plugin to automatically refactor Java code bases.
 *
 * Copyright (C) 2014-2016 Jean-Noël Rouvignac - initial API and implementation
 * Copyright (C) 2016-2017 Fabrice Tiercelin - Annoying remaining loop variable occurrence
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program under LICENSE-GNUGPL.  If not, see
 * <http://www.gnu.org/licenses/>.
 *
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution under LICENSE-ECLIPSE, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.autorefactor.jdt.internal.ui.fix;

import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.DoublePredicate;
import java.util.function.IntPredicate;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.autorefactor.jdt.core.dom.ASTRewrite;
import org.autorefactor.jdt.internal.corext.dom.ASTNodeFactory;
import org.autorefactor.jdt.internal.corext.dom.ASTNodes;
import org.autorefactor.jdt.internal.corext.dom.OrderedInfixExpression;
import org.autorefactor.jdt.internal.corext.dom.Release;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.text.edits.TextEditGroup;

/** See {@link #getDescription()} method. */
public class MatchingStreamRatherThanCountCleanUp extends AbstractCleanUpRule {
	private static final String FIND_FIRST_METHOD= "findFirst"; //$NON-NLS-1$
	private static final String FIND_ANY_METHOD= "findAny"; //$NON-NLS-1$
	private static final String IS_EMPTY_METHOD= "isEmpty"; //$NON-NLS-1$
	private static final String IS_PRESENT_METHOD= "isPresent"; //$NON-NLS-1$
	private static final String FILTER_METHOD= "filter"; //$NON-NLS-1$
	private static final String COUNT_METHOD= "count"; //$NON-NLS-1$
	private static final String NONEMATCH_METHOD= "noneMatch"; //$NON-NLS-1$
	private static final String ANYMATCH_METHOD= "anyMatch"; //$NON-NLS-1$

	@Override
	public String getName() {
		return MultiFixMessages.MatchingStreamRatherThanCountCleanUp_name;
	}

	@Override
	public String getDescription() {
		return MultiFixMessages.MatchingStreamRatherThanCountCleanUp_description;
	}

	@Override
	public String getReason() {
		return MultiFixMessages.MatchingStreamRatherThanCountCleanUp_reason;
	}

	@Override
	public boolean isJavaVersionSupported(final Release javaSeRelease) {
		return javaSeRelease.getMinorVersion() >= 8;
	}

	@Override
	public boolean visit(final InfixExpression visited) {
		OrderedInfixExpression<MethodInvocation, Expression> orderedCondition= ASTNodes.orderedInfix(visited, MethodInvocation.class, Expression.class);

		if (orderedCondition != null) {
			MethodInvocation countMethod= orderedCondition.getFirstOperand();
			Long literalCount= ASTNodes.getIntegerLiteral(orderedCondition.getSecondOperand());

			if (literalCount != null
					&& (ASTNodes.usesGivenSignature(countMethod, Stream.class.getCanonicalName(), COUNT_METHOD)
							|| ASTNodes.usesGivenSignature(countMethod, IntStream.class.getCanonicalName(), COUNT_METHOD)
							|| ASTNodes.usesGivenSignature(countMethod, LongStream.class.getCanonicalName(), COUNT_METHOD)
							|| ASTNodes.usesGivenSignature(countMethod, DoubleStream.class.getCanonicalName(), COUNT_METHOD))) {
				Boolean isMatchingAny= null;

				if (Long.valueOf(0L).equals(literalCount)) {
					if (Arrays.asList(InfixExpression.Operator.GREATER, InfixExpression.Operator.NOT_EQUALS).contains(orderedCondition.getOperator())) {
						isMatchingAny= Boolean.TRUE;
					} else if (Arrays.asList(InfixExpression.Operator.EQUALS, InfixExpression.Operator.LESS_EQUALS).contains(orderedCondition.getOperator())) {
						isMatchingAny= Boolean.FALSE;
					}
				} else if (Long.valueOf(1L).equals(literalCount)) {
					if (InfixExpression.Operator.GREATER_EQUALS.equals(orderedCondition.getOperator())) {
						isMatchingAny= Boolean.TRUE;
					} else if (InfixExpression.Operator.LESS.equals(orderedCondition.getOperator())) {
						isMatchingAny= Boolean.FALSE;
					}
				}

				maybeReplaceCommonMethod(visited, isMatchingAny, countMethod.getExpression());
			}
		}

		return true;
	}

	@Override
	public boolean visit(final MethodInvocation visited) {
		PrefixExpression prefixExpression= ASTNodes.getTypedAncestor(visited, PrefixExpression.class);

		if (prefixExpression == null
				|| !ASTNodes.hasOperator(prefixExpression, PrefixExpression.Operator.NOT)) {
			return maybeReplaceFindMethod(visited, visited, true);
		}

		return true;
	}

	@Override
	public boolean visit(final PrefixExpression visited) {
		MethodInvocation existenceMethod= ASTNodes.as(visited.getOperand(), MethodInvocation.class);

		if (existenceMethod != null
				&& ASTNodes.hasOperator(visited, PrefixExpression.Operator.NOT)) {
			return maybeReplaceFindMethod(visited, existenceMethod, false);
		}

		return true;
	}

	private boolean maybeReplaceFindMethod(final Expression visited, final MethodInvocation existenceMethod, final boolean isMatchingAny) {
		final boolean isEmptyMethod= ASTNodes.usesGivenSignature(existenceMethod, Optional.class.getCanonicalName(), IS_EMPTY_METHOD)
				|| ASTNodes.usesGivenSignature(existenceMethod, OptionalInt.class.getCanonicalName(), IS_EMPTY_METHOD)
				|| ASTNodes.usesGivenSignature(existenceMethod, OptionalLong.class.getCanonicalName(), IS_EMPTY_METHOD)
				|| ASTNodes.usesGivenSignature(existenceMethod, OptionalDouble.class.getCanonicalName(), IS_EMPTY_METHOD);

		if (isEmptyMethod
				|| ASTNodes.usesGivenSignature(existenceMethod, Optional.class.getCanonicalName(), IS_PRESENT_METHOD)
				|| ASTNodes.usesGivenSignature(existenceMethod, OptionalInt.class.getCanonicalName(), IS_PRESENT_METHOD)
				|| ASTNodes.usesGivenSignature(existenceMethod, OptionalLong.class.getCanonicalName(), IS_PRESENT_METHOD)
				|| ASTNodes.usesGivenSignature(existenceMethod, OptionalDouble.class.getCanonicalName(), IS_PRESENT_METHOD)) {
			MethodInvocation findMethod= ASTNodes.as(existenceMethod.getExpression(), MethodInvocation.class);

			if (ASTNodes.usesGivenSignature(findMethod, Stream.class.getCanonicalName(), FIND_FIRST_METHOD)
					|| ASTNodes.usesGivenSignature(findMethod, IntStream.class.getCanonicalName(), FIND_FIRST_METHOD)
					|| ASTNodes.usesGivenSignature(findMethod, LongStream.class.getCanonicalName(), FIND_FIRST_METHOD)
					|| ASTNodes.usesGivenSignature(findMethod, DoubleStream.class.getCanonicalName(), FIND_FIRST_METHOD)
					|| ASTNodes.usesGivenSignature(findMethod, Stream.class.getCanonicalName(), FIND_ANY_METHOD)
					|| ASTNodes.usesGivenSignature(findMethod, IntStream.class.getCanonicalName(), FIND_ANY_METHOD)
					|| ASTNodes.usesGivenSignature(findMethod, LongStream.class.getCanonicalName(), FIND_ANY_METHOD)
					|| ASTNodes.usesGivenSignature(findMethod, DoubleStream.class.getCanonicalName(), FIND_ANY_METHOD)) {
				return maybeReplaceCommonMethod(visited, isMatchingAny ^ isEmptyMethod, findMethod.getExpression());
			}
		}

		return true;
	}

	private boolean maybeReplaceCommonMethod(final Expression visited, final Boolean isMatchingAny, final Expression filteringExpression) {
		MethodInvocation filterMethod= ASTNodes.as(filteringExpression, MethodInvocation.class);

		if (isMatchingAny != null
				&& filterMethod != null
				&& filterMethod.getExpression() != null
				&& !ASTNodes.is(filterMethod.getExpression(), ThisExpression.class)
				&& (ASTNodes.usesGivenSignature(filterMethod, Stream.class.getCanonicalName(), FILTER_METHOD, Predicate.class.getCanonicalName())
						|| ASTNodes.usesGivenSignature(filterMethod, IntStream.class.getCanonicalName(), FILTER_METHOD, IntPredicate.class.getCanonicalName())
						|| ASTNodes.usesGivenSignature(filterMethod, LongStream.class.getCanonicalName(), FILTER_METHOD, LongPredicate.class.getCanonicalName())
						|| ASTNodes.usesGivenSignature(filterMethod, DoubleStream.class.getCanonicalName(), FILTER_METHOD, DoublePredicate.class.getCanonicalName()))) {
			LambdaExpression predicate= ASTNodes.as((Expression) filterMethod.arguments().get(0), LambdaExpression.class);

			if (predicate != null
					&& ASTNodes.isPassiveWithoutFallingThrough(predicate.getBody())) {
				replaceMethod(visited, filterMethod, isMatchingAny);
				return false;
			}
		}

		return true;
	}

	private void replaceMethod(final Expression visited, final MethodInvocation filterMethod, final boolean isMatchingAny) {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		ASTNodeFactory ast= cuRewrite.getASTBuilder();
		TextEditGroup group= new TextEditGroup(MultiFixMessages.MatchingStreamRatherThanCountCleanUp_description);

		rewrite.replace(visited, filterMethod, group);
		rewrite.replace(filterMethod.getName(), ast.newSimpleName(isMatchingAny ? ANYMATCH_METHOD : NONEMATCH_METHOD), group);
	}
}
