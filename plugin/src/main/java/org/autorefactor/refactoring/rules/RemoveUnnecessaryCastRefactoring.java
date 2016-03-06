/*
 * AutoRefactor - Eclipse plugin to automatically refactor Java code bases.
 *
 * Copyright (C) 2014-2015 Jean-Noël Rouvignac - initial API and implementation
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
package org.autorefactor.refactoring.rules;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.autorefactor.refactoring.ASTBuilder;
import org.autorefactor.refactoring.Primitive;
import org.autorefactor.util.NotImplementedException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import static org.autorefactor.refactoring.ASTHelper.*;
import static org.autorefactor.refactoring.Primitive.*;
import static org.eclipse.jdt.core.dom.ASTNode.*;
import static org.eclipse.jdt.core.dom.InfixExpression.Operator.*;

/**
 * See {@link #getDescription()} method.
 * <p>
 * TODO JNR remove casts from method parameters
 */
@SuppressWarnings("javadoc")
public class RemoveUnnecessaryCastRefactoring extends AbstractRefactoringRule {

    @Override
    public String getDescription() {
        return "Removes unnecessary widening casts from return statements, assignments and infix expressions.";
    }

    @Override
    public String getName() {
        return "Remove unnecessary casts";
    }

    @Override
    public boolean visit(CastExpression node) {
        if (canRemoveCast(node)) {
            final ASTBuilder b = ctx.getASTBuilder();
            ctx.getRefactorings().replace(node, b.move(node.getExpression()));
        }
        return VISIT_SUBTREE;
    }

    private boolean canRemoveCast(CastExpression node) {
        final ASTNode parent = node.getParent();
        switch (parent.getNodeType()) {
        case RETURN_STATEMENT:
            final MethodDeclaration md = getAncestor(parent, MethodDeclaration.class);
            return isAssignmentCompatible(node.getExpression(), md.getReturnType2());

        case ASSIGNMENT:
            final Assignment as = (Assignment) parent;
            return isAssignmentCompatible(node.getExpression(), as)
                    || isConstantExpressionAssignmentConversion(node);

        case VARIABLE_DECLARATION_FRAGMENT:
            final VariableDeclarationFragment vdf = (VariableDeclarationFragment) parent;
            return isAssignmentCompatible(node.getExpression(), resolveTypeBinding(vdf))
                    || isConstantExpressionAssignmentConversion(node);

        case INFIX_EXPRESSION:
            final InfixExpression ie = (InfixExpression) parent;
            final Expression lo = ie.getLeftOperand();
            final Expression ro = ie.getRightOperand();
            if (node.equals(lo)) {
                return (isStringConcat(ie) || isAssignmentCompatible(node.getExpression(), ro))
                        && !isPrimitiveTypeNarrowing(node)
                        && !hasOperator(ie, DIVIDE)
                        && !hasOperator(ie, PLUS)
                        && !hasOperator(ie, MINUS);
            } else {
                final boolean integralDivision = isIntegralDivision(ie);
                return ((!hasBeenRefactored(lo) && isStringConcat(ie) && !removingCastWillChangeDisplay(node))
                            || (!integralDivision && isAssignmentCompatibleInInfixExpression(node, ie))
                            || (integralDivision && canRemoveCastInIntegralDivision(node, ie)))
                        && !isPrimitiveTypeNarrowing(node)
                        && !isIntegralDividedByFloatingPoint(node, ie);
            }
        }
        return false;
    }

    private boolean removingCastWillChangeDisplay(CastExpression node) {
        ITypeBinding exprBinding = node.getExpression().resolveTypeBinding();
        ITypeBinding castBinding = node.resolveTypeBinding();
        // FIXME how about casting primitive wrappers too?
        Primitive exprEnum = Primitive.valueOfPrimitiveOrWrapper(exprBinding);
        Primitive castEnum = Primitive.valueOfPrimitiveOrWrapper(castBinding);
        if (exprEnum != null && castEnum != null) {
            switch (exprEnum) {
            case CHAR:
                switch (castEnum) {
                case BYTE:
                case SHORT:
                case INT:
                case LONG:
                case FLOAT:
                case DOUBLE:
                    return true;

                default:
                    return false;
                }

            case BYTE:
                switch (castEnum) {
                case BYTE:
                case SHORT:
                case INT:
                case LONG:

                case FLOAT:
                case DOUBLE:
                    return true;

                default:
                    return false;
                }

            default:
                break;
            }
        }
        return false;
    }

    private boolean canRemoveCastInIntegralDivision(CastExpression node, InfixExpression ie) {
        final ITypeBinding leftOperandType = getLeftOperandType(ie, node);
        return isIntegralDivision(ie) // safety check
                && isAssignmentCompatible(node.getExpression().resolveTypeBinding(), leftOperandType)
                && compareTo(node.resolveTypeBinding(), leftOperandType) >= 0;
    }

    private boolean isIntegralDivision(final InfixExpression ie) {
        return isIntegralType(ie) && hasOperator(ie, DIVIDE);
    }

    private boolean isAssignmentCompatibleInInfixExpression(final CastExpression node, final InfixExpression ie) {
        final ITypeBinding leftOpType = getLeftOperandType(ie, node);
        return isAssignmentCompatible(node.getExpression().resolveTypeBinding(), leftOpType)
                && isAssignmentCompatible(node.resolveTypeBinding(), leftOpType);
    }

    private ITypeBinding getLeftOperandType(InfixExpression ie, CastExpression node) {
        final ArrayList<Expression> operands = allOperands(ie);
        final List<Expression> previousOperands = operands.subList(0, operands.indexOf(node));
        if (isAnyRefactored(previousOperands)) {
            return null;
        }
        return getTypeBinding(previousOperands);
    }

    private ITypeBinding getTypeBinding(final List<Expression> previousOperands) {
        final Iterator<Expression> it = previousOperands.iterator();
        ITypeBinding maxTypeBinding = it.next().resolveTypeBinding();
        while (it.hasNext()) {
            final ITypeBinding typeBinding = it.next().resolveTypeBinding();
            if (compareTo(maxTypeBinding, typeBinding) < 0) {
                maxTypeBinding = typeBinding;
            }
        }
        return maxTypeBinding;
    }

    private int compareTo(ITypeBinding primitiveBinding1, ITypeBinding primitiveBinding2) {
        final int rank1 = getPrimitiveRank(primitiveBinding1);
        final int rank2 = getPrimitiveRank(primitiveBinding2);
        return rank1 - rank2;
    }

    private int getPrimitiveRank(ITypeBinding typeBinding) {
        Primitive primEnum = Primitive.valueOfPrimitiveOrWrapper(typeBinding);
        if (primEnum != null) {
            return primEnum.ordinal();
        }
        throw new NotImplementedException(null, "for type '" + typeBinding.getQualifiedName() + "'");
    }

    private boolean isAnyRefactored(final List<Expression> operands) {
        for (Expression operand : operands) {
            if (hasBeenRefactored(operand)) {
                return true;
            }
        }
        return false;
    }

    private ArrayList<Expression> allOperands(InfixExpression ie) {
        final List<Expression> eo = extendedOperands(ie);
        final ArrayList<Expression> results = new ArrayList<Expression>(2 + eo.size());
        results.add(ie.getLeftOperand());
        results.add(ie.getRightOperand());
        results.addAll(eo);
        return results;
    }

    /** If left operand is refactored, we cannot easily make inferences about right operand. Wait for next iteration. */
    private boolean hasBeenRefactored(Expression leftOperand) {
        return ctx.getRefactorings().hasBeenRefactored(leftOperand);
    }

    private boolean isIntegralDividedByFloatingPoint(CastExpression node, InfixExpression ie) {
        final Expression rightOp = ie.getRightOperand();
        return isIntegralType(ie.getLeftOperand())
                && hasOperator(ie, DIVIDE)
                && isFloatingPointType(rightOp)
                && node.equals(rightOp);
    }

    private boolean isIntegralType(final Expression expr) {
        return hasType(expr, "byte", "char", "short", "int", "long");
    }

    private boolean isFloatingPointType(final Expression expr) {
        return hasType(expr, "float", "double");
    }

    /** @see JLS, section 5.2 Assignment Conversion */
    private boolean isConstantExpressionAssignmentConversion(CastExpression node) {
        final Object value = node.getExpression().resolveConstantExpressionValue();
        if (value instanceof Integer) {
            final int val = (Integer) value;
            return     (hasType(node, "byte")  &&   -128 <= val && val <= 127)
                    || (hasType(node, "short") && -32768 <= val && val <= 32767)
                    || (hasType(node, "char")  &&      0 <= val && val <= 65535);
        }
        return false;
    }

    private boolean isStringConcat(InfixExpression ie) {
        return hasType(ie, "java.lang.String");
    }

    private boolean isPrimitiveTypeNarrowing(CastExpression node) {
        final ITypeBinding typeBinding1 = node.getType().resolveBinding();
        final ITypeBinding typeBinding2 = node.getExpression().resolveTypeBinding();
        return valueOfPrimitive(typeBinding1) == null
                && valueOfPrimitive(typeBinding2) == null
                && isAssignmentCompatible(typeBinding1, typeBinding2);
    }

    private boolean isAssignmentCompatible(Expression expr, Type type) {
        if (expr != null && type != null) {
            return isAssignmentCompatible(expr.resolveTypeBinding(), type.resolveBinding());
        }
        return false;
    }

    private boolean isAssignmentCompatible(Expression expr, ITypeBinding typeBinding) {
        if (expr != null && typeBinding != null) {
            return isAssignmentCompatible(expr.resolveTypeBinding(), typeBinding);
        }
        return false;
    }

    private boolean isAssignmentCompatible(Expression expr1, Expression expr2) {
        if (expr1 != null && expr2 != null) {
            return isAssignmentCompatible(expr1.resolveTypeBinding(), expr2.resolveTypeBinding());
        }
        return false;
    }

    private boolean isAssignmentCompatible(final ITypeBinding binding1, final ITypeBinding binding2) {
        if (binding1 != null && binding2 != null) {
            return binding1.isAssignmentCompatible(binding2);
        }
        return false;
    }

}
