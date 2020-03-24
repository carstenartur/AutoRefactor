/*
 * AutoRefactor - Eclipse plugin to automatically refactor Java code bases.
 *
 * Copyright (C) 2019 Fabrice TIERCELIN - initial API and implementation
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

import java.util.Collection;

import org.autorefactor.jdt.core.dom.ASTRewrite;
import org.autorefactor.jdt.internal.corext.dom.ASTNodeFactory;
import org.autorefactor.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ThisExpression;

/** See {@link #getDescription()} method. */
public class ContainsAllRatherThanLoopCleanUp extends AbstractCollectionMethodRatherThanLoopCleanUp {
    /**
     * Get the name.
     *
     * @return the name.
     */
    @Override
    public String getName() {
        return MultiFixMessages.CleanUpRefactoringWizard_ContainsAllRatherThanLoopCleanUp_name;
    }

    /**
     * Get the description.
     *
     * @return the description.
     */
    @Override
    public String getDescription() {
        return MultiFixMessages.CleanUpRefactoringWizard_ContainsAllRatherThanLoopCleanUp_description;
    }

    /**
     * Get the reason.
     *
     * @return the reason.
     */
    @Override
    public String getReason() {
        return MultiFixMessages.CleanUpRefactoringWizard_ContainsRatherThanLoopCleanUp_reason;
    }

    @Override
    protected Expression getExpressionToFind(final MethodInvocation condition, final Expression forVar, final Expression iterable) {
        Expression expression= ASTNodes.getUnparenthesedExpression(condition.getExpression());
        Expression arg0= ASTNodes.getUnparenthesedExpression(ASTNodes.arguments(condition).get(0));

        if (ASTNodes.isSameVariable(forVar, arg0) || ASTNodes.match(forVar, arg0)) {
            return expression;
        }

        return null;
    }

    @Override
    protected MethodInvocation getMethodToReplace(final Expression condition) {
        PrefixExpression negation= ASTNodes.as(condition, PrefixExpression.class);

        if (negation != null && ASTNodes.hasOperator(negation, PrefixExpression.Operator.NOT)) {
            MethodInvocation method= ASTNodes.as(negation.getOperand(), MethodInvocation.class);

            if (ASTNodes.usesGivenSignature(method, Collection.class.getCanonicalName(), "contains", Object.class.getCanonicalName()) //$NON-NLS-1$
                    && method.getExpression() != null
                    && ASTNodes.as(method.getExpression(), ThisExpression.class) == null) {
                return method;
            }
        }

        return null;
    }

    @Override
    protected Expression newMethod(final Expression iterable, final Expression toFind, final boolean isPositive) {
        ASTNodeFactory ast= cuRewrite.getASTBuilder();
        ASTRewrite rewrite= cuRewrite.getASTRewrite();
        MethodInvocation invoke= ast.invoke(rewrite.createMoveTarget(toFind), "containsAll", rewrite.createMoveTarget(iterable)); //$NON-NLS-1$

        if (isPositive) {
            return ast.not(invoke);
        }

        return invoke;
    }
}
