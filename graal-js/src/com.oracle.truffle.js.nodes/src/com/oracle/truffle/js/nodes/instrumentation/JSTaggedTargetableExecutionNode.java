/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.js.nodes.instrumentation;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.js.nodes.JavaScriptNode;
import com.oracle.truffle.js.nodes.access.GlobalConstantNode;
import com.oracle.truffle.js.nodes.access.JSTargetableNode;
import com.oracle.truffle.js.nodes.access.PropertyNode;
import com.oracle.truffle.js.nodes.access.ReadElementNode;
import com.oracle.truffle.js.nodes.instrumentation.JSTags.ReadElementExpressionTag;
import com.oracle.truffle.js.nodes.instrumentation.JSTags.ReadPropertyExpressionTag;

/**
 * A utility node used by the instrumentation framework to simulate the execution of an AST node
 * providing a target value (being the result of a previous execution).
 *
 * When calling <code>executeWithTarget(frame, target)</code>, the <code>target</code> value will be
 * reported to the instrumentation framework as an <code>onInput</code> value for the specified
 * event tag.
 *
 */
public final class JSTaggedTargetableExecutionNode extends JSTargetableNode {

    @Child private JSTargetableNode echo;
    @Child private JSTargetableNode child;

    private final SourceSection sourceSection;
    private final Class<? extends Tag> expectedTag;

    public static JSTargetableNode createFor(JSTargetableNode originalNode, SourceSection sourceSection, SourceSection tSection) {
        // The original node will not report events. Rather, the event will be reported by the
        // wrapper node.
        JSTargetableNode clone = null;
        Class<? extends Tag> expectedTag = null;
        if (originalNode instanceof ReadElementNode) {
            expectedTag = ReadElementExpressionTag.class;
            ReadElementNode originalRead = (ReadElementNode) originalNode;
            JavaScriptNode clonedTarget = cloneUninitializedNoSourceSection(originalRead.getTarget());
            JavaScriptNode clonedIndex = JSTaggedExecutionNode.createFor(cloneUninitialized(originalRead.getElement()), StandardTags.ExpressionTag.class);
            clone = ReadElementNode.create(clonedTarget, clonedIndex, originalRead.getContext());
        } else {
            assert originalNode instanceof PropertyNode || originalNode instanceof GlobalConstantNode;
            expectedTag = ReadPropertyExpressionTag.class;
            clone = (JSTargetableNode) cloneUninitializedNoSourceSection(originalNode);
        }
        JSTargetableNode wrapper = new JSTaggedTargetableExecutionNode(clone, expectedTag, tSection);
        wrapper.setSourceSection(sourceSection);
        return wrapper;
    }

    @SuppressWarnings("unchecked")
    private static <T extends JavaScriptNode> T cloneUninitializedNoSourceSection(Node node) {
        T cloned = cloneUninitialized((T) node);
        eraseAllSourceSections(cloned);
        cloned.removeSourceSection();
        return cloned;
    }

    private static void eraseAllSourceSections(Node node) {
        NodeUtil.forEachChild(node, new NodeVisitor() {

            @Override
            public boolean visit(Node n) {
                if (n instanceof JavaScriptNode) {
                    ((JavaScriptNode) n).removeSourceSection();
                }
                NodeUtil.forEachChild(n, this);
                return true;
            }
        });
    }

    protected JSTaggedTargetableExecutionNode(JSTargetableNode child, Class<? extends Tag> expectedTag, SourceSection sourceSection) {
        this.child = child;
        this.expectedTag = expectedTag;
        this.sourceSection = sourceSection;
        this.echo = new TargetValueEchoNode(getNodeObject());
        this.echo.setSourceSection(sourceSection);
    }

    @Override
    public Object getNodeObject() {
        if (child instanceof WrapperNode) {
            return ((InstrumentableNode) ((WrapperNode) child).getDelegateNode()).getNodeObject();
        }
        return child.getNodeObject();
    }

    public JSTargetableNode getChild() {
        return child;
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        if (tag == expectedTag) {
            return true;
        } else {
            return super.hasTag(tag);
        }
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return child.execute(frame);
    }

    @Override
    public JavaScriptNode getTarget() {
        return child.getTarget();
    }

    @Override
    public Object executeWithTarget(VirtualFrame frame, Object target) {
        echo.executeWithTarget(frame, target);
        return child.executeWithTarget(frame, target);
    }

    @Override
    protected JSTargetableNode copyUninitialized() {
        return new JSTaggedTargetableExecutionNode(cloneUninitialized(child), expectedTag, sourceSection);
    }

    private static class TargetValueEchoNode extends JSTargetableNode {

        private final Object nodeObjectValue;

        TargetValueEchoNode(Object nodeObject) {
            this.nodeObjectValue = nodeObject;
        }

        @Override
        public Object executeWithTarget(VirtualFrame frame, Object target) {
            return target;
        }

        @Override
        public Object getNodeObject() {
            return nodeObjectValue;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new AssertionError();
        }
    }

}
