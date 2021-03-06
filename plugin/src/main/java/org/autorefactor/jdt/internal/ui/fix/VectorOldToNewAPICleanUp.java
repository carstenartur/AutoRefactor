/*
 * AutoRefactor - Eclipse plugin to automatically refactor Java code bases.
 *
 * Copyright (C) 2013-2016 Jean-Noël Rouvignac - initial API and implementation
 * Copyright (C) 2016 Fabrice Tiercelin - Make sure we do not visit again modified nodes
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

import java.util.List;
import java.util.Vector;

import org.autorefactor.jdt.core.dom.ASTRewrite;
import org.autorefactor.jdt.internal.corext.dom.ASTNodeFactory;
import org.autorefactor.jdt.internal.corext.dom.ASTNodes;
import org.autorefactor.jdt.internal.corext.dom.Release;
import org.autorefactor.util.IllegalArgumentException;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;

/** See {@link #getDescription()} method. */
public class VectorOldToNewAPICleanUp extends AbstractCleanUpRule {
    /**
     * Get the name.
     *
     * @return the name.
     */
    @Override
    public String getName() {
        return MultiFixMessages.CleanUpRefactoringWizard_VectorOldToNewAPICleanUp_name;
    }

    /**
     * Get the description.
     *
     * @return the description.
     */
    @Override
    public String getDescription() {
        return MultiFixMessages.CleanUpRefactoringWizard_VectorOldToNewAPICleanUp_description;
    }

    /**
     * Get the reason.
     *
     * @return the reason.
     */
    @Override
    public String getReason() {
        return MultiFixMessages.CleanUpRefactoringWizard_VectorOldToNewAPICleanUp_reason;
    }

    @Override
    public boolean isJavaVersionSupported(final Release javaSeRelease) {
        return javaSeRelease.getMinorVersion() >= 2;
    }

    @Override
    public boolean visit(final MethodInvocation node) {
        if (ASTNodes.usesGivenSignature(node, Vector.class.getCanonicalName(), "elementAt", int.class.getSimpleName())) { //$NON-NLS-1$
            replaceWith(node, "get"); //$NON-NLS-1$
        } else if (ASTNodes.usesGivenSignature(node, Vector.class.getCanonicalName(), "addElement", Object.class.getCanonicalName())) { //$NON-NLS-1$
            replaceWith(node, "add"); //$NON-NLS-1$
        } else if (ASTNodes.usesGivenSignature(node, Vector.class.getCanonicalName(), "insertElementAt", Object.class.getCanonicalName(), int.class.getSimpleName())) { //$NON-NLS-1$
            replaceWithAndSwapArguments(node, "add"); //$NON-NLS-1$
        } else if (ASTNodes.usesGivenSignature(node, Vector.class.getCanonicalName(), "copyInto", Object[].class.getCanonicalName())) { //$NON-NLS-1$
            replaceWith(node, "toArray"); //$NON-NLS-1$
        } else if (ASTNodes.usesGivenSignature(node, Vector.class.getCanonicalName(), "removeAllElements")) { //$NON-NLS-1$
            replaceWith(node, "clear"); //$NON-NLS-1$
        } else if (ASTNodes.usesGivenSignature(node, Vector.class.getCanonicalName(), "removeElement", Object.class.getCanonicalName())) { //$NON-NLS-1$
            replaceWithSpecial(node, "remove"); //$NON-NLS-1$
        } else if (ASTNodes.usesGivenSignature(node, Vector.class.getCanonicalName(), "removeElementAt", int.class.getSimpleName())) { //$NON-NLS-1$
            replaceWith(node, "remove"); //$NON-NLS-1$
        } else if (ASTNodes.usesGivenSignature(node, Vector.class.getCanonicalName(), "setElementAt", Object.class.getCanonicalName(), int.class.getSimpleName())) { //$NON-NLS-1$
            replaceWithAndSwapArguments(node, "set"); //$NON-NLS-1$
        } else {
            return true;
        }

        return false;
    }

    private void replaceWith(final MethodInvocation node, final String newMethodName) {
        ASTNodeFactory ast= cuRewrite.getASTBuilder();
        cuRewrite.getASTRewrite().set(node, MethodInvocation.NAME_PROPERTY, ast.simpleName(newMethodName), null);
    }

    private void replaceWithSpecial(final MethodInvocation node, final String newMethodName) {
        List<Expression> args= ASTNodes.arguments(node);
        assertSize(args, 1);
        Expression arg0= args.get(0);

        ASTNodeFactory ast= cuRewrite.getASTBuilder();
        ASTRewrite rewrite= cuRewrite.getASTRewrite();
        rewrite.set(node, MethodInvocation.NAME_PROPERTY, ast.simpleName(newMethodName), null);
        if (ASTNodes.hasType(arg0, int.class.getSimpleName(), short.class.getSimpleName(), byte.class.getSimpleName())) {
            rewrite.replace(arg0, ast.cast(ast.type(Object.class.getSimpleName()), rewrite.createMoveTarget(arg0)), null);
        }
    }

    private void replaceWithAndSwapArguments(final MethodInvocation node, final String newMethodName) {
        List<Expression> args= ASTNodes.arguments(node);
        assertSize(args, 2);
        Expression arg1= args.get(1);

        ASTNodeFactory ast= cuRewrite.getASTBuilder();
        ASTRewrite rewrite= cuRewrite.getASTRewrite();
        rewrite.set(node, MethodInvocation.NAME_PROPERTY, ast.simpleName(newMethodName), null);
        rewrite.moveToIndex(arg1, 0, rewrite.createMoveTarget(arg1), null);
    }

    private void assertSize(final List<Expression> args, final int expectedSize) {
        if (args == null) {
            throw new IllegalArgumentException(null, "Expected " + args + "to not be null"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (args.size() != expectedSize) {
            Expression node= !args.isEmpty() ? args.get(0) : null;
            throw new IllegalArgumentException(node,
                    "Expected " + args + " to have size <" + expectedSize + ">, but found <" + args.size() + ">"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        }
    }
}
