/*
 * AutoRefactor - Eclipse plugin to automatically refactor Java code bases.
 *
 * Copyright (C) 2019 Fabrice Tiercelin - initial API and implementation
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

import org.autorefactor.jdt.core.dom.ASTRewrite;
import org.autorefactor.jdt.internal.corext.dom.ASTNodeFactory;
import org.autorefactor.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.text.edits.TextEditGroup;

/** See {@link #getDescription()} method. */
public class OppositeComparisonRatherThanNegativeExpressionCleanUp extends AbstractCleanUpRule {
	@Override
	public String getName() {
		return MultiFixMessages.CleanUpRefactoringWizard_OppositeComparisonRatherThanNegativeExpressionCleanUp_name;
	}

	@Override
	public String getDescription() {
		return MultiFixMessages.CleanUpRefactoringWizard_OppositeComparisonRatherThanNegativeExpressionCleanUp_description;
	}

	@Override
	public String getReason() {
		return MultiFixMessages.CleanUpRefactoringWizard_OppositeComparisonRatherThanNegativeExpressionCleanUp_reason;
	}

	@Override
	public boolean visit(final PrefixExpression node) {
		if (ASTNodes.hasOperator(node, PrefixExpression.Operator.MINUS)) {
			MethodInvocation methodInvocation= ASTNodes.as(node.getOperand(), MethodInvocation.class);

			if (methodInvocation != null && methodInvocation.getExpression() != null && methodInvocation.arguments().size() == 1) {
				String[] classes= { Double.class.getCanonicalName(), Float.class.getCanonicalName(), Short.class.getCanonicalName(), Integer.class.getCanonicalName(), Long.class.getCanonicalName(), Character.class.getCanonicalName(), Byte.class.getCanonicalName(), Boolean.class.getCanonicalName() };

				for (String clazz : classes) {
					if (ASTNodes.usesGivenSignature(methodInvocation, clazz, "compareTo", clazz) && ASTNodes.hasType((Expression) methodInvocation.arguments().get(0), clazz)) { //$NON-NLS-1$
						reverseObjects(node, methodInvocation);
						return false;
					}
				}
			}
		}

		return true;
	}

	private void reverseObjects(final PrefixExpression node, final MethodInvocation methodInvocation) {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		ASTNodeFactory ast= cuRewrite.getASTBuilder();
		TextEditGroup group= new TextEditGroup(MultiFixMessages.CleanUpRefactoringWizard_OppositeComparisonRatherThanNegativeExpressionCleanUp_name);

		rewrite.replace(node, ast.newMethodInvocation(ASTRewrite.parenthesizeIfNeeded(ast, ASTNodes.createMoveTarget(rewrite, (Expression) methodInvocation.arguments().get(0))), "compareTo", //$NON-NLS-1$
				ASTNodes.createMoveTarget(rewrite, ASTNodes.getUnparenthesedExpression(methodInvocation.getExpression()))), group);
	}
}
