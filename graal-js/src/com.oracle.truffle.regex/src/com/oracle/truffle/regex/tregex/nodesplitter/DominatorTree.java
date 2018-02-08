/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.oracle.truffle.regex.tregex.nodesplitter;

import com.oracle.truffle.regex.util.CompilationFinalBitSet;

import java.util.ArrayList;
import java.util.Arrays;

final class DominatorTree {

    private final Graph graph;
    private int nextPostOrderIndex;
    private final ArrayList<GraphNode> postOrder;
    private int[] doms;
    private final CompilationFinalBitSet flagTraversed;

    DominatorTree(Graph graph) {
        this.graph = graph;
        flagTraversed = new CompilationFinalBitSet(graph.size() + DFANodeSplit.EXTRA_INITIAL_CAPACITY);
        postOrder = new ArrayList<>(graph.size() + DFANodeSplit.EXTRA_INITIAL_CAPACITY);
        doms = new int[graph.size() + DFANodeSplit.EXTRA_INITIAL_CAPACITY];
    }

    private boolean isTraversed(GraphNode node) {
        return flagTraversed.get(node.getId());
    }

    private void setTraversed(GraphNode node) {
        flagTraversed.set(node.getId());
    }

    void createDomTree() {
        buildPostOrder();
        buildDominatorTree();
        initDomTreeDepth(graph.getStart(), 1);
    }

    GraphNode idom(GraphNode n) {
        return postOrder.get(doms[n.getPostOrderIndex()]);
    }

    boolean dom(GraphNode a, GraphNode b) {
        int dom = doms[b.getPostOrderIndex()];
        while (true) {
            if (a.getPostOrderIndex() == dom) {
                return true;
            }
            if (dom == doms[dom]) {
                return false;
            }
            dom = doms[dom];
        }
    }

    private boolean graphIsConsistent() {
        for (GraphNode n : graph.getNodes()) {
            for (GraphNode s : n.successors(graph)) {
                if (!s.hasPredecessor(n)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void buildPostOrder() {
        assert graphIsConsistent();
        flagTraversed.clear();
        nextPostOrderIndex = 0;
        postOrder.clear();
        traversePostOrder(graph.getStart());
        assert allNodesTraversed();
    }

    private void traversePostOrder(GraphNode cur) {
        setTraversed(cur);
        for (GraphNode n : cur.successors(graph)) {
            if (!isTraversed(n)) {
                traversePostOrder(n);
            }
        }
        cur.setPostOrderIndex(nextPostOrderIndex++);
        postOrder.add(cur);
        assert postOrder.get(cur.getPostOrderIndex()) == cur;
    }

    private boolean allNodesTraversed() {
        for (GraphNode n : graph.getNodes()) {
            if (!isTraversed(n)) {
                return false;
            }
        }
        return true;
    }

    private void buildDominatorTree() {
        if (doms.length < graph.size()) {
            int newLength = doms.length * 2;
            while (newLength < graph.size()) {
                newLength *= 2;
            }
            doms = new int[newLength];
        }
        Arrays.fill(doms, -1);
        doms[graph.getStart().getPostOrderIndex()] = graph.getStart().getPostOrderIndex();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = postOrder.size() - 1; i >= 0; i--) {
                GraphNode b = postOrder.get(i);
                if (b == graph.getStart()) {
                    continue;
                }
                // find a predecessor that was already processed
                GraphNode selectedPredecessor = null;
                for (GraphNode p : b.predecessors()) {
                    if (p.getPostOrderIndex() > i) {
                        selectedPredecessor = p;
                        break;
                    }
                }
                if (selectedPredecessor == null) {
                    throw new IllegalStateException();
                }
                int newIDom = selectedPredecessor.getPostOrderIndex();
                for (GraphNode p : b.predecessors()) {
                    if (p == selectedPredecessor) {
                        continue;
                    }
                    if (doms[p.getPostOrderIndex()] != -1) {
                        newIDom = intersect(p.getPostOrderIndex(), newIDom);
                    }
                }
                if (doms[b.getPostOrderIndex()] != newIDom) {
                    doms[b.getPostOrderIndex()] = newIDom;
                    changed = true;
                }
            }
        }
        for (GraphNode n : graph.getNodes()) {
            n.clearDomChildren();
        }
        for (int i = 0; i < graph.size(); i++) {
            GraphNode dominator = postOrder.get(doms[i]);
            GraphNode child = postOrder.get(i);
            if (dominator != child) {
                dominator.addDomChild(child);
            }
        }
    }

    private int intersect(int b1, int b2) {
        int finger1 = b1;
        int finger2 = b2;
        while (finger1 != finger2) {
            while (finger1 < finger2) {
                finger1 = doms[finger1];
            }
            while (finger2 < finger1) {
                finger2 = doms[finger2];
            }
        }
        return finger1;
    }

    private void initDomTreeDepth(GraphNode curNode, int depth) {
        curNode.setDomTreeDepth(depth);
        for (GraphNode child : curNode.domChildren(graph)) {
            initDomTreeDepth(child, depth + 1);
        }
    }
}
