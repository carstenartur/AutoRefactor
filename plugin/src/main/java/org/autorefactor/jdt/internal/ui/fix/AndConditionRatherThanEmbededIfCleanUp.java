/*
 * AutoRefactor - Eclipse plugin to automatically refactor Java code bases.
 *
 * Copyright (C) 2013-2015 Jean-Noël Rouvignac - initial API and implementation
 * Copyright (C) 2016 Fabrice Tiercelin - Make sure we do not visit again modified nodes
 * Copyright (C) 2019 Fabrice Tiercelin - Add parenthesis when it's needed
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
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;

/** See {@link #getDescription()} method. */
public class AndConditionRatherThanEmbededIfCleanUp extends AbstractCleanUpRule {
    /**
     * Get the name.
     *
     * @return the name.
     */
    @Override
    public String getName() {
        return MultiFixMessages.CleanUpRefactoringWizard_AndConditionRatherThanEmbededIfCleanUp_name;
    }

    /**
     * Get the description.
     *
     * @return the description.
     */
    @Override
    public String getDescription() {
        return MultiFixMessages.CleanUpRefactoringWizard_AndConditionRatherThanEmbededIfCleanUp_description;
    }

    /**
     * Get the reason.
     *
     * @return the reason.
     */
    @Override
    public String getReason() {
        return MultiFixMessages.CleanUpRefactoringWizard_AndConditionRatherThanEmbededIfCleanUp_reason;
    }

    @Override
    public boolean visit(final IfStatement node) {
        if (node.getElseStatement() == null) {
            IfStatement is= ASTNodes.as(node.getThenStatement(), IfStatement.class);

            if (is != null
                    && is.getElseStatement() == null
                    && ASTNodes.getNbOperands(node.getExpression()) + ASTNodes.getNbOperands(is.getExpression()) < ASTNodes.EXCESSIVE_OPERAND_NUMBER) {
                replaceIfNoElseStatement(node, is);
                return false;
            }
        }

        return true;
    }

    private void replaceIfNoElseStatement(final IfStatement outerIf, final IfStatement innerIf) {
        ASTNodeFactory ast= cuRewrite.getASTBuilder();
        ASTRewrite rewrite= cuRewrite.getASTRewrite();

        InfixExpression ie= ast.infixExpression(ast.parenthesizeIfNeeded(rewrite.createMoveTarget(outerIf.getExpression())), InfixExpression.Operator.CONDITIONAL_AND,
                ast.parenthesizeIfNeeded(rewrite.createMoveTarget(innerIf.getExpression())));
        rewrite.replace(innerIf.getExpression(), ie, null);
        rewrite.replace(outerIf, rewrite.createMoveTarget(innerIf), null);
    }
}
