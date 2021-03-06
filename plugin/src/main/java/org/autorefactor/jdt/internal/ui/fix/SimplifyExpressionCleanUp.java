/*
 * AutoRefactor - Eclipse plugin to automatically refactor Java code bases.
 *
 * Copyright (C) 2013-2016 Jean-Noël Rouvignac - initial API and implementation
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
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicBoolean;

import org.autorefactor.jdt.core.dom.ASTRewrite;
import org.autorefactor.jdt.internal.corext.dom.ASTNodeFactory;
import org.autorefactor.jdt.internal.corext.dom.ASTNodes;
import org.autorefactor.jdt.internal.corext.dom.ASTSemanticMatcher;
import org.autorefactor.util.Pair;
import org.autorefactor.util.Utils;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.WhileStatement;

/** See {@link #getDescription()} method. */
public class SimplifyExpressionCleanUp extends AbstractCleanUpRule {
    /**
     * Get the name.
     *
     * @return the name.
     */
    @Override
    public String getName() {
        return MultiFixMessages.CleanUpRefactoringWizard_SimplifyExpressionCleanUp_name;
    }

    /**
     * Get the description.
     *
     * @return the description.
     */
    @Override
    public String getDescription() {
        return MultiFixMessages.CleanUpRefactoringWizard_SimplifyExpressionCleanUp_description;
    }

    /**
     * Get the reason.
     *
     * @return the reason.
     */
    @Override
    public String getReason() {
        return MultiFixMessages.CleanUpRefactoringWizard_SimplifyExpressionCleanUp_reason;
    }

    /**
     * A mapping of child operation to parent operation that mandates using
     * parentheses.
     */
    private static final List<Pair<InfixExpression.Operator, InfixExpression.Operator>> SHOULD_HAVE_PARENTHESES= Arrays
            .<Pair<InfixExpression.Operator, InfixExpression.Operator>>asList(Pair.of(InfixExpression.Operator.CONDITIONAL_AND, InfixExpression.Operator.CONDITIONAL_OR), Pair.of(InfixExpression.Operator.AND, InfixExpression.Operator.XOR),
                    Pair.of(InfixExpression.Operator.AND, InfixExpression.Operator.OR), Pair.of(InfixExpression.Operator.XOR, InfixExpression.Operator.OR), Pair.of(InfixExpression.Operator.LEFT_SHIFT, InfixExpression.Operator.OR), Pair.of(InfixExpression.Operator.LEFT_SHIFT, InfixExpression.Operator.AND),
                    Pair.of(InfixExpression.Operator.RIGHT_SHIFT_SIGNED, InfixExpression.Operator.OR), Pair.of(InfixExpression.Operator.RIGHT_SHIFT_SIGNED, InfixExpression.Operator.AND),
                    Pair.of(InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED, InfixExpression.Operator.OR), Pair.of(InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED, InfixExpression.Operator.AND));

    // TODO Very few parenthesized expressions are actually needed. They are:
    // 1) inside InfixExpressions with logical operators (&&, ||, etc.)
    // Sometimes needed to explicit code, some like it like that too
    // 2) Inside String concatenations if they hold an InfixExpression that does
    // not resolve to String (what about PrefixExpression and
    // PostFixExpression?)
    // 3) Around CastExpression
    // Any others?

    // TODO JNR String s = "some " + " string " + "" + ( "fhj" + "prout" );

    @Override
    public boolean visit(final ParenthesizedExpression node) {
        Expression innerExpression= getExpressionWithoutParentheses(node);

        if (innerExpression != node) {
            replaceBy(node, innerExpression);
            return false;
        }

        return true;
    }

    private Expression getExpressionWithoutParentheses(final ParenthesizedExpression node) {
        ASTNode parent= node.getParent();
        Expression innerExpression= node.getExpression();
        if (innerExpression instanceof ParenthesizedExpression) {
            return innerExpression;
        }
        if (parent instanceof InfixExpression) {
            InfixExpression parentInfixExpression = (InfixExpression) parent;
            if (innerExpression instanceof InfixExpression) {
                InfixExpression.Operator innerOp = ((InfixExpression) innerExpression).getOperator();
                if (innerOp == parentInfixExpression.getOperator()
                        && OperatorEnum.isAssociative(innerOp)
                        // Leave String concatenations with mixed type
                        // to other if statements in this method.
                        && Utils.equalNotNull(innerExpression.resolveTypeBinding(), parentInfixExpression.resolveTypeBinding())) {
                    return innerExpression;
                }
            }
        }
        // Infix, prefix or postfix without parenthesis is not readable
        if ((parent instanceof InfixExpression
                && ASTNodes.hasOperator((InfixExpression) parent, InfixExpression.Operator.PLUS, InfixExpression.Operator.MINUS)
                || parent instanceof PrefixExpression
                        && ASTNodes.hasOperator((PrefixExpression) parent, PrefixExpression.Operator.PLUS, PrefixExpression.Operator.MINUS)) && (innerExpression instanceof PrefixExpression
                && ASTNodes.hasOperator((PrefixExpression) innerExpression, PrefixExpression.Operator.DECREMENT, PrefixExpression.Operator.INCREMENT, PrefixExpression.Operator.PLUS, PrefixExpression.Operator.MINUS) || innerExpression instanceof PostfixExpression
                && ASTNodes.hasOperator((PostfixExpression) innerExpression, PostfixExpression.Operator.DECREMENT, PostfixExpression.Operator.INCREMENT)) || isInnerExprHardToRead(innerExpression, parent)) {
            return node;
        }
        if (isUselessParenthesesInStatement(parent, node)) {
            return innerExpression;
        }
        int compareTo= OperatorEnum.compareTo(innerExpression, parent);
        if (compareTo < 0) {
            return node;
        }
        if (compareTo > 0) {
            return innerExpression;
        }
        if (
                // TODO JNR can we revert the condition in the InfixExpression?
                // parentheses are sometimes needed to explicit code,
                // some like it like that
                innerExpression instanceof InfixExpression
                // TODO JNR add additional code to check if the cast is really required
                // or if it can be removed.
                || innerExpression instanceof CastExpression
                // Infix and prefix or postfix without parenthesis is not readable
                || (parent instanceof InfixExpression
                        || parent instanceof PrefixExpression
                        || parent instanceof PostfixExpression)
                        && (innerExpression instanceof PrefixExpression
                                || innerExpression instanceof PostfixExpression)) {
            return node;
        }

        return innerExpression;
    }

    /**
     * Returns whether the supplied expression is complex enough to read.
     *
     * @param innerExpression the inner expression to test for ease of read
     * @param parent    the parent node to test for ease of read
     * @return true if the expressions is hard to read, false otherwise
     */
    private boolean isInnerExprHardToRead(final Expression innerExpression, final ASTNode parent) {
        if (parent instanceof InfixExpression) {
            if (innerExpression instanceof InfixExpression) {
                InfixExpression innerIe= (InfixExpression) innerExpression;
                InfixExpression.Operator innerOp= innerIe.getOperator();
                InfixExpression.Operator parentOp= ((InfixExpression) parent).getOperator();
                return ASTNodes.hasOperator((InfixExpression) parent, InfixExpression.Operator.EQUALS) || shouldHaveParentheses(innerOp, parentOp)
                        || ASTNodes.is(innerIe.getLeftOperand(), Assignment.class)
                        || ASTNodes.is(innerIe.getRightOperand(), Assignment.class);
            }
        } else if (parent instanceof ConditionalExpression) {
            return innerExpression instanceof ConditionalExpression || innerExpression instanceof Assignment
                    || innerExpression instanceof InstanceofExpression || innerExpression instanceof InfixExpression;
        }

        return false;
    }

    private boolean isUselessParenthesesInStatement(final ASTNode parent, final ParenthesizedExpression node) {
        switch (parent.getNodeType()) {
        case ASTNode.ASSIGNMENT:
            Assignment a= (Assignment) parent;
            return node.equals(a.getRightHandSide());

        case ASTNode.METHOD_INVOCATION:
            MethodInvocation mi= (MethodInvocation) parent;
            return ASTNodes.arguments(mi).contains(node) || canRemoveParenthesesAroundExpression(mi, node);

        case ASTNode.IF_STATEMENT:
            IfStatement is= (IfStatement) parent;
            return node.equals(is.getExpression());

        case ASTNode.WHILE_STATEMENT:
            WhileStatement ws= (WhileStatement) parent;
            return node.equals(ws.getExpression());

        case ASTNode.DO_STATEMENT:
            DoStatement ds= (DoStatement) parent;
            return node.equals(ds.getExpression());

        case ASTNode.RETURN_STATEMENT:
            ReturnStatement rs= (ReturnStatement) parent;
            return node.equals(rs.getExpression());

        case ASTNode.VARIABLE_DECLARATION_FRAGMENT:
            VariableDeclarationFragment vdf= (VariableDeclarationFragment) parent;
            return node.equals(vdf.getInitializer());

        default:
            return false;
        }
    }

    private boolean canRemoveParenthesesAroundExpression(final MethodInvocation mi, final ParenthesizedExpression node) {
        if (node.equals(mi.getExpression())) {
            switch (node.getExpression().getNodeType()) {
            case ASTNode.ASSIGNMENT:
            case ASTNode.CAST_EXPRESSION:
            case ASTNode.CONDITIONAL_EXPRESSION:
            case ASTNode.INFIX_EXPRESSION:
                return false;

            default:
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean visit(final InfixExpression node) {
        Expression lhs= node.getLeftOperand();
        Expression rhs= node.getRightOperand();

        if (ASTNodes.hasOperator(node, InfixExpression.Operator.CONDITIONAL_OR)) {
            AtomicBoolean hasUselessOperand= new AtomicBoolean(false);
            List<Expression> remainingOperands= removeUselessOperands(node, hasUselessOperand, false, true);

            if (hasUselessOperand.get()) {
                replaceWithNewInfixExpression(node, remainingOperands);
                return false;
            }
        } else if (ASTNodes.hasOperator(node, InfixExpression.Operator.CONDITIONAL_AND)) {
            AtomicBoolean hasUselessOperand= new AtomicBoolean(false);
            List<Expression> remainingOperands= removeUselessOperands(node, hasUselessOperand, true, false);

            if (hasUselessOperand.get()) {
                replaceWithNewInfixExpression(node, remainingOperands);
                return false;
            }

            // FIXME this should actually check anywhere in the infix expression,
            // not only for left and right operands,
            // said otherwise: handle extended operands
            Expression nullCheckedExpressionLHS= ASTNodes.getNullCheckedExpression(lhs);
            Expression nullCheckedExpressionRHS= ASTNodes.getNullCheckedExpression(rhs);

            if (nullCheckedExpressionLHS != null) {
                if (isNullCheckRedundant(rhs, nullCheckedExpressionLHS)) {
                    ASTNodes.checkNoExtendedOperands(node);
                    replaceBy(node, rhs);
                    return false;
                }
            } else if (isNullCheckRedundant(lhs, nullCheckedExpressionRHS)) {
                replaceBy(node, lhs);
                return false;
            }
        } else if (ASTNodes.hasOperator(node, InfixExpression.Operator.EQUALS, InfixExpression.Operator.NOT_EQUALS, InfixExpression.Operator.XOR) && !node.hasExtendedOperands()
                && !maybeReduceBooleanExpression(node, lhs, rhs)) {
            return false;
        }

        if (shouldHaveParentheses(node)) {
            addParentheses(node);
            return false;
        }

        return true;
    }

    private List<Expression> removeUselessOperands(final InfixExpression node, final AtomicBoolean hasUselessOperand, final Boolean neutralElement, final Boolean shortCircuitValue) {
        List<Expression> allOperands= ASTNodes.allOperands(node);

        for (ListIterator<Expression> it= allOperands.listIterator(); it.hasNext();) {
            Expression operand= it.next();
            Object value= operand.resolveConstantExpressionValue();

            if (shortCircuitValue.equals(value)) {
                while (it.hasNext()) {
                    hasUselessOperand.set(true);
                    it.next();
                    it.remove();
                }
                break;
            }

            if (neutralElement.equals(value)) {
                hasUselessOperand.set(true);
                it.remove();
            }
        }

        return allOperands;
    }

    private boolean maybeReduceBooleanExpression(final InfixExpression node, final Expression leftExpression,
            final Expression rightExpression) {
        Boolean leftBoolean= ASTNodes.getBooleanLiteral(leftExpression);
        Boolean rightBoolean= ASTNodes.getBooleanLiteral(rightExpression);

        if (leftBoolean != null) {
            return replace(node, leftBoolean, rightExpression);
        }
        if (rightBoolean != null) {
            return replace(node, rightBoolean, leftExpression);
        }

        Expression leftOppositeExpression= null;
        PrefixExpression leftPrefix= ASTNodes.as(leftExpression, PrefixExpression.class);
        if (leftPrefix != null && ASTNodes.hasOperator(leftPrefix, PrefixExpression.Operator.NOT)) {
            leftOppositeExpression= leftPrefix.getOperand();
        }

        Expression rightOppositeExpression= null;
        PrefixExpression rightPrefix= ASTNodes.as(rightExpression, PrefixExpression.class);
        if (rightPrefix != null && ASTNodes.hasOperator(rightPrefix, PrefixExpression.Operator.NOT)) {
            rightOppositeExpression= rightPrefix.getOperand();
        }

        ASTNodeFactory ast= cuRewrite.getASTBuilder();
        ASTRewrite rewrite= cuRewrite.getASTRewrite();
        if (leftOppositeExpression != null) {
            if (rightOppositeExpression != null) {
                rewrite.replace(node,
                        ast.infixExpression(rewrite.createMoveTarget(leftOppositeExpression), getAppropriateOperator(node), rewrite.createMoveTarget(rightOppositeExpression)), null);
            } else {
                InfixExpression.Operator reverseOp= getReverseOperator(node);
                rewrite.replace(node, ast.infixExpression(rewrite.createMoveTarget(leftOppositeExpression), reverseOp, rewrite.createMoveTarget(rightExpression)), null);
            }

            return false;
        }
        if (rightOppositeExpression != null) {
            InfixExpression.Operator reverseOp= getReverseOperator(node);
            rewrite.replace(node, ast.infixExpression(rewrite.createMoveTarget(leftExpression), reverseOp, rewrite.createMoveTarget(rightOppositeExpression)), null);
            return false;
        }

        return true;
    }

    private InfixExpression.Operator getAppropriateOperator(final InfixExpression node) {
        if (ASTNodes.hasOperator(node, InfixExpression.Operator.NOT_EQUALS)) {
            return InfixExpression.Operator.XOR;
        }

        return node.getOperator();
    }

    private InfixExpression.Operator getReverseOperator(final InfixExpression node) {
        if (ASTNodes.hasOperator(node, InfixExpression.Operator.EQUALS)) {
            return InfixExpression.Operator.XOR;
        }

        return InfixExpression.Operator.EQUALS;
    }

    private boolean replace(final InfixExpression node, final boolean isTrue, final Expression exprToCopy) {
        ASTNodes.checkNoExtendedOperands(node);

        if (!ASTNodes.isPrimitive(node.getLeftOperand(), boolean.class.getSimpleName()) && !ASTNodes.isPrimitive(node.getRightOperand(), boolean.class.getSimpleName())) {
            return true;
        }

        // Either:
        // - Two boolean primitives: no possible NPE
        // - One boolean primitive and one Boolean object, this code already run
        // the risk of an NPE, so we can replace the infix expression without
        // fearing we would introduce a previously non existing NPE.
        ASTNodeFactory ast= cuRewrite.getASTBuilder();
        ASTRewrite rewrite= cuRewrite.getASTRewrite();

        Expression operand;
        if (isTrue == ASTNodes.hasOperator(node, InfixExpression.Operator.EQUALS)) {
            operand= rewrite.createMoveTarget(exprToCopy);
        } else {
            operand= ast.negate(exprToCopy);
        }

        rewrite.replace(node, operand, null);
        return false;
    }

    private void replaceWithNewInfixExpression(final InfixExpression node, final List<Expression> remainingOperands) {
        if (remainingOperands.size() == 1) {
            replaceBy(node, remainingOperands.get(0));
        } else {
            ASTNodeFactory ast= cuRewrite.getASTBuilder();
            ASTRewrite rewrite= cuRewrite.getASTRewrite();
            rewrite.replace(node, ast.infixExpression(node.getOperator(), rewrite.createMoveTarget(remainingOperands)), null);
        }
    }

    private boolean shouldHaveParentheses(final InfixExpression node) {
        InfixExpression.Operator childOp= node.getOperator();

        if (node.getParent() instanceof InfixExpression) {
            InfixExpression ie= (InfixExpression) node.getParent();
            return shouldHaveParentheses(childOp, ie.getOperator());
        }

        return false;
    }

    private boolean shouldHaveParentheses(final InfixExpression.Operator actualChildOp, final InfixExpression.Operator actualParentOp) {
        for (Pair<InfixExpression.Operator, InfixExpression.Operator> pair : SHOULD_HAVE_PARENTHESES) {
            InfixExpression.Operator childOp= pair.getFirst();
            InfixExpression.Operator parentOp= pair.getSecond();

            if (childOp.equals(actualChildOp) && parentOp.equals(actualParentOp)) {
                return true;
            }
        }

        return false;
    }

    private void addParentheses(final Expression expression) {
        ASTNodeFactory ast= cuRewrite.getASTBuilder();
        ASTRewrite rewrite= cuRewrite.getASTRewrite();
        rewrite.replace(expression, ast.parenthesize(rewrite.createMoveTarget(expression)), null);
    }

    private void replaceBy(final ASTNode node, final Expression expression) {
        ASTRewrite rewrite= cuRewrite.getASTRewrite();
        rewrite.replace(node, rewrite.createMoveTarget(expression), null);
    }

    /**
     * The previous null check is redundant if:
     * <ul>
     * <li>the null checked expression is reused in an instanceof expression</li>
     * <li>the null checked expression is reused in an expression checking for
     * object equality against an expression that resolves to a non null
     * constant</li>
     * </ul>
     */
    private boolean isNullCheckRedundant(final Expression e, final Expression nullCheckedExpression) {
        if (nullCheckedExpression != null) {
            if (e instanceof InstanceofExpression) {
                Expression expression= ((InstanceofExpression) e).getLeftOperand();
                return expression.subtreeMatch(ASTSemanticMatcher.INSTANCE, nullCheckedExpression);
            }
            if (e instanceof MethodInvocation) {
                MethodInvocation expression= (MethodInvocation) e;
                if (expression.getExpression() != null && expression.getExpression().resolveConstantExpressionValue() != null
                        && ASTNodes.arguments(expression).size() == 1
                        && ASTNodes.arguments(expression).get(0).subtreeMatch(ASTSemanticMatcher.INSTANCE, nullCheckedExpression)) {
                    // Did we invoke java.lang.Object.equals() or
                    // java.lang.String.equalsIgnoreCase()?
                    return ASTNodes.usesGivenSignature(expression, Object.class.getCanonicalName(), "equals", Object.class.getCanonicalName()) //$NON-NLS-1$
                            || ASTNodes.usesGivenSignature(expression, String.class.getCanonicalName(), "equalsIgnoreCase", String.class.getCanonicalName()); //$NON-NLS-1$
                }
            }
        }

        return false;
    }
}
