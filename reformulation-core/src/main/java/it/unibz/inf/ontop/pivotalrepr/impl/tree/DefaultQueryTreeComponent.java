package it.unibz.inf.ontop.pivotalrepr.impl.tree;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import it.unibz.inf.ontop.model.VariableGenerator;
import it.unibz.inf.ontop.pivotalrepr.*;
import it.unibz.inf.ontop.pivotalrepr.NonCommutativeOperatorNode.ArgumentPosition;
import it.unibz.inf.ontop.pivotalrepr.impl.IllegalTreeUpdateException;
import it.unibz.inf.ontop.model.Variable;
import it.unibz.inf.ontop.pivotalrepr.impl.IllegalTreeException;
import it.unibz.inf.ontop.pivotalrepr.impl.QueryTreeComponent;
import it.unibz.inf.ontop.pivotalrepr.impl.VariableCollector;

import java.util.*;


/**
 * TODO: describe
 *
 * Every time a node is added, please call the method collectPossiblyNewVariables()!
 */
public class DefaultQueryTreeComponent implements QueryTreeComponent {

    private final QueryTree tree;
    private final VariableGenerator variableGenerator;

    /**
     * TODO: explain
     */
    protected DefaultQueryTreeComponent(QueryTree tree) {
        this.tree = tree;
        this.variableGenerator = new VariableGenerator(
                VariableCollector.collectVariables(tree.getNodesInTopDownOrder()));
    }

    @Override
    public ImmutableList<QueryNode> getCurrentSubNodesOf(QueryNode node) {
        return tree.getChildren(node);
    }

    @Override
    public ConstructionNode getRootConstructionNode() {
        return tree.getRootNode();
    }

    @Override
    public ImmutableList<QueryNode> getNodesInBottomUpOrder() {
        return tree.getNodesInBottomUpOrder();
    }

    @Override
    public ImmutableList<QueryNode> getNodesInTopDownOrder() throws IllegalTreeException {
        return tree.getNodesInTopDownOrder();
    }

    @Override
    public ImmutableSet<EmptyNode> getEmptyNodes(QueryNode subTreeRoot) {
        return tree.getEmptyNodes(subTreeRoot);
    }

    @Override
    public boolean contains(QueryNode node) {
        return tree.contains(node);
    }

    @Override
    public void replaceNode(QueryNode previousNode, QueryNode replacingNode) {
        collectPossiblyNewVariables(replacingNode);
        tree.replaceNode(previousNode, replacingNode);
    }

    @Override
    public void addSubTree(IntermediateQuery subQuery, QueryNode subQueryTopNode, QueryNode localTopNode)
            throws IllegalTreeUpdateException {
        Queue<QueryNode> localParents = new LinkedList<>();
        localParents.add(localTopNode);
        Map<QueryNode, QueryNode> localToExternalNodeMap = new HashMap<>();
        localToExternalNodeMap.put(localTopNode, subQueryTopNode);

        while(!localParents.isEmpty()) {
            QueryNode localParent = localParents.poll();
            QueryNode externalParent = localToExternalNodeMap.get(localParent);

            for (QueryNode externalChild : subQuery.getChildren(externalParent)) {
                QueryNode localChild = externalChild.clone();
                localToExternalNodeMap.put(localChild, externalChild);
                localParents.add(localChild);
                addChild(localParent, localChild, subQuery.getOptionalPosition(externalParent, externalChild), false);
            }
        }

    }

    @Override
    public void removeSubTree(QueryNode subTreeRoot) {
        tree.removeSubTree(subTreeRoot);
    }

    @Override
    public ImmutableList<QueryNode> getSubTreeNodesInTopDownOrder(QueryNode currentNode) {
        return tree.getSubTreeNodesInTopDownOrder(currentNode);
    }

    @Override
    public Optional<ArgumentPosition> getOptionalPosition(QueryNode parentNode,
                                                                                     QueryNode childNode) {
        return tree.getOptionalPosition(parentNode, childNode);
    }

    @Override
    public ImmutableList<QueryNode> getAncestors(QueryNode descendantNode) throws IllegalTreeException {
        ImmutableList.Builder<QueryNode> ancestorBuilder = ImmutableList.builder();

        // Non-final
        Optional<QueryNode> optionalAncestor = getParent(descendantNode);

        while(optionalAncestor.isPresent()) {
            QueryNode ancestor = optionalAncestor.get();
            ancestorBuilder.add(ancestor);

            optionalAncestor = getParent(ancestor);
        }
        return ancestorBuilder.build();
    }

    @Override
    public Optional<QueryNode> getParent(QueryNode node) throws IllegalTreeException {
        return tree.getParent(node);
    }

    @Override
    public void removeOrReplaceNodeByUniqueChildren(QueryNode node) throws IllegalTreeUpdateException {
        tree.removeOrReplaceNodeByUniqueChild(node);
    }

    @Override
    public void replaceNodesByOneNode(ImmutableList<QueryNode> queryNodes, QueryNode replacingNode, QueryNode parentNode,
                                      Optional<ArgumentPosition> optionalPosition)
            throws IllegalTreeUpdateException {
        collectPossiblyNewVariables(replacingNode);
        tree.replaceNodesByOneNode(queryNodes, replacingNode, parentNode, optionalPosition);
    }


    @Override
    public void addChild(QueryNode parentNode, QueryNode childNode,
                         Optional<ArgumentPosition> optionalPosition, boolean canReplace)
            throws IllegalTreeUpdateException {
        collectPossiblyNewVariables(childNode);
        tree.addChild(parentNode, childNode, optionalPosition, true, canReplace);
    }

    @Override
    public Optional<QueryNode> nextSibling(QueryNode node) throws IllegalTreeException {
        Optional<QueryNode> optionalParent = tree.getParent(node);
        if (optionalParent.isPresent()) {
            ImmutableList<QueryNode> siblings = tree.getChildren(optionalParent.get());

            int index = siblings.indexOf(node);

            if (index == -1) {
                throw new IllegalTreeException("The node " + node + " does not appear as a child of its parent");
            }
            else if (index < (siblings.size() -1)) {
                return Optional.of(siblings.get(index + 1));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<QueryNode> getFirstChild(QueryNode node) {
        ImmutableList<QueryNode> children = tree.getChildren(node);

        switch(children.size()) {
            case 0:
                return Optional.empty();
            default:
                return Optional.of(children.get(0));
        }
    }

    @Override
    public void insertParent(QueryNode childNode, QueryNode newParentNode) throws IllegalTreeUpdateException {
        collectPossiblyNewVariables(newParentNode);
        tree.insertParent(childNode, newParentNode);
    }

    @Override
    public Variable generateNewVariable() {
        return variableGenerator.generateNewVariable();
    }

    @Override
    public Variable generateNewVariable(Variable formerVariable) {
        return variableGenerator.generateNewVariableFromVar(formerVariable);
    }

    @Override
    public ImmutableSet<Variable> getKnownVariables() {
        return variableGenerator.getKnownVariables();
    }

    @Override
    public void replaceNodeByChild(QueryNode parentNode, Optional<ArgumentPosition> optionalReplacingChildPosition) {
        tree.replaceNodeByChild(parentNode, optionalReplacingChildPosition);
    }

    /**
     * To be called every time a new node is added to the tree component.
     */
    private void collectPossiblyNewVariables(QueryNode newNode) {
        variableGenerator.registerAdditionalVariables(newNode.getVariables());
    }
}
