package org.semanticweb.ontop.pivotalrepr.impl;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.semanticweb.ontop.executor.InternalProposalExecutor;
import org.semanticweb.ontop.executor.deletion.ReactToChildDeletionExecutor;
import org.semanticweb.ontop.executor.expression.PushDownExpressionExecutor;
import org.semanticweb.ontop.executor.groundterm.GroundTermRemovalFromDataNodeExecutor;
import org.semanticweb.ontop.executor.join.JoinInternalCompositeExecutor;
import org.semanticweb.ontop.executor.renaming.PredicateRenamingExecutor;
import org.semanticweb.ontop.executor.substitution.SubstitutionPropagationExecutor;
import org.semanticweb.ontop.model.DataAtom;
import org.semanticweb.ontop.model.Variable;
import org.semanticweb.ontop.pivotalrepr.*;
import org.semanticweb.ontop.pivotalrepr.proposal.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * TODO: describe
 *
 * BEWARE: this class has a non-trivial mutable internal state!
 */
public class IntermediateQueryImpl implements IntermediateQuery {

    /**
     * Thrown when the internal state of the intermediate query is found to be inconsistent.
     *
     * Should not be expected (internal error).
     *
     */
    public static class InconsistentIntermediateQueryException extends RuntimeException {
        public InconsistentIntermediateQueryException(String message) {
            super(message);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(IntermediateQueryImpl.class);

    /**
     * TODO: use Guice to replace it.
     */
    private static final IntermediateQueryPrinter PRINTER = new BasicQueryTreePrinter();


    /**
     * Highly mutable (low control) so MUST NOT BE SHARED (except with InternalProposalExecutor)!
     */
    private final QueryTreeComponent treeComponent;

    private final MetadataForQueryOptimization metadata;

    /**
     * TODO: explain
     */
    private static final ImmutableMap<Class<? extends QueryOptimizationProposal>, Class<? extends StandardProposalExecutor>> STD_EXECUTOR_CLASSES;
    static {
        STD_EXECUTOR_CLASSES = ImmutableMap.<Class<? extends QueryOptimizationProposal>, Class<? extends StandardProposalExecutor>>of(
                UnionLiftProposal.class, UnionLiftProposalExecutor.class,
                PredicateRenamingProposal.class, PredicateRenamingExecutor.class);
    }

    /**
     * TODO: explain
     */
    private static final ImmutableMap<Class<? extends QueryOptimizationProposal>, Class<? extends InternalProposalExecutor>> INTERNAL_EXECUTOR_CLASSES;
    static {
        ImmutableMap.Builder<Class<? extends QueryOptimizationProposal>, Class<? extends InternalProposalExecutor>>
                internalExecutorMapBuilder = ImmutableMap.builder();
        internalExecutorMapBuilder.put(SubstitutionLiftProposal.class, SubstitutionLiftProposalExecutor.class);
        internalExecutorMapBuilder.put(InnerJoinOptimizationProposal.class, JoinInternalCompositeExecutor.class);
        internalExecutorMapBuilder.put(ReactToChildDeletionProposal.class, ReactToChildDeletionExecutor.class);
        internalExecutorMapBuilder.put(SubstitutionPropagationProposal.class, SubstitutionPropagationExecutor.class);
        internalExecutorMapBuilder.put(PushDownBooleanExpressionProposal.class, PushDownExpressionExecutor.class);
        internalExecutorMapBuilder.put(GroundTermRemovalFromDataNodeProposal.class, GroundTermRemovalFromDataNodeExecutor.class);
        INTERNAL_EXECUTOR_CLASSES = internalExecutorMapBuilder.build();
    }


    /**
     * For IntermediateQueryBuilders ONLY!!
     */
    public IntermediateQueryImpl(MetadataForQueryOptimization metadata, QueryTreeComponent treeComponent) {
        this.metadata = metadata;
        this.treeComponent = treeComponent;
    }

    @Override
    public MetadataForQueryOptimization getMetadata() {
        return metadata;
    }

    @Override
    public ConstructionNode getRootConstructionNode() throws InconsistentIntermediateQueryException{
        try {
            return treeComponent.getRootConstructionNode();
        } catch (IllegalTreeException e) {
            throw new InconsistentIntermediateQueryException(e.getMessage());
        }
    }

    @Override
    public ImmutableList<QueryNode> getNodesInBottomUpOrder() throws InconsistentIntermediateQueryException {
        try {
            return treeComponent.getNodesInBottomUpOrder();
        } catch (IllegalTreeException e) {
            throw new InconsistentIntermediateQueryException(e.getMessage());
        }
    }

    @Override
    public ImmutableList<QueryNode> getNodesInTopDownOrder() {
        try {
            return treeComponent.getNodesInTopDownOrder();
        } catch (IllegalTreeException e) {
            throw new InconsistentIntermediateQueryException(e.getMessage());
        }
    }

    @Override
    public ImmutableList<QueryNode> getChildren(QueryNode node) {
        return treeComponent.getCurrentSubNodesOf(node);
    }

    @Override
    public ImmutableList<QueryNode> getSubTreeNodesInTopDownOrder(QueryNode currentNode) {
        return treeComponent.getSubTreeNodesInTopDownOrder(currentNode);
    }

    @Override
    public boolean contains(QueryNode node) {
        return treeComponent.contains(node);
    }

    /**
     * TODO: make this extensible by using Guice as a dependency-injection solution for loading arbitrary ProposalExecutor
     */
    @Override
    public ProposalResults applyProposal(QueryOptimizationProposal proposal, boolean requireUsingInternalExecutor)
            throws InvalidQueryOptimizationProposalException, EmptyQueryException {

        /**
         * It assumes that the concrete proposal classes DIRECTLY
         * implements a registered interface (extending QueryOptimizationProposal).
         */
        Class<?>[] proposalClassHierarchy = proposal.getClass().getInterfaces();

        if (!requireUsingInternalExecutor) {
            /**
             * First look for a standard executor
             */
            for (Class proposalClass : proposalClassHierarchy) {
                if (STD_EXECUTOR_CLASSES.containsKey(proposalClass)) {
                    StandardProposalExecutor executor;
                    try {
                        executor = STD_EXECUTOR_CLASSES.get(proposalClass).newInstance();
                    } catch (InstantiationException | IllegalAccessException e) {
                        throw new RuntimeException(e.getMessage());
                    }
                    return executor.apply(proposal, this);
                }
            }
        }

        /**
         * Then, look for a internal one
         */
        for (Class proposalClass : proposalClassHierarchy) {
            if (INTERNAL_EXECUTOR_CLASSES.containsKey(proposalClass)) {
                InternalProposalExecutor executor;
                try {
                    executor = INTERNAL_EXECUTOR_CLASSES.get(proposalClass).newInstance();
                } catch (InstantiationException | IllegalAccessException e ) {
                    throw new RuntimeException(e.getMessage());
                }
                /**
                 * Has a SIDE-EFFECT on the tree component.
                 */
                return executor.apply(proposal, this, treeComponent);
            }
        }

        if (requireUsingInternalExecutor) {
            throw new RuntimeException("No INTERNAL executor found for a proposal of the type " + proposal.getClass());
        }
        else {
            throw new RuntimeException("No executor found for a proposal of the type " + proposal.getClass());
        }
    }

    @Override
    public ProposalResults applyProposal(QueryOptimizationProposal propagationProposal)
            throws InvalidQueryOptimizationProposalException, EmptyQueryException {
        return applyProposal(propagationProposal, false);
    }

    @Override
    public Optional<BinaryAsymmetricOperatorNode.ArgumentPosition> getOptionalPosition(QueryNode parentNode,
                                                                                      QueryNode childNode) {
        return treeComponent.getOptionalPosition(parentNode, childNode);
    }

    @Override
    public Optional<BinaryAsymmetricOperatorNode.ArgumentPosition> getOptionalPosition(QueryNode child) {
        Optional<QueryNode> optionalParent = getParent(child);
        if (optionalParent.isPresent()) {
            return getOptionalPosition(optionalParent.get(), child);
        }
        else {
            return Optional.absent();
        }
    }

    @Override
    public ImmutableList<QueryNode> getAncestors(QueryNode descendantNode) {
        try {
            return treeComponent.getAncestors(descendantNode);
        } catch (IllegalTreeException e) {
            throw new InconsistentIntermediateQueryException(e.getMessage());
        }
    }

    @Override
    public Optional<QueryNode> getParent(QueryNode node) {
        try {
            return treeComponent.getParent(node);
        } catch (IllegalTreeException e) {
            throw new InconsistentIntermediateQueryException(e.getMessage());
        }
    }

    @Override
    public Optional<QueryNode> getNextSibling(QueryNode node) {
        try {
            return treeComponent.nextSibling(node);
        } catch (IllegalTreeException e) {
            throw new InconsistentIntermediateQueryException(e.getMessage());
        }
    }

    @Override
    public Optional<QueryNode> getFirstChild(QueryNode node) {
        return treeComponent.getFirstChild(node);
    }

    /**
     * TODO: explain
     */
    @Override
    public void mergeSubQuery(final IntermediateQuery originalSubQuery) throws QueryMergingException {
        /**
         * TODO: explain
         */
        List<IntensionalDataNode> localDataNodes = findOrdinaryDataNodes(originalSubQuery.getRootConstructionNode().getProjectionAtom());
        if (localDataNodes.isEmpty())
            throw new QueryMergingException("No OrdinaryDataNode matches " + originalSubQuery.getRootConstructionNode().getProjectionAtom());


        for (IntensionalDataNode localDataNode : localDataNodes) {
            // TODO: make it be incremental
            ImmutableSet<Variable> localVariables = VariableCollector.collectVariables(this);

            try {
                IntermediateQuery cloneSubQuery = SubQueryUnificationTools.unifySubQuery(originalSubQuery,
                            localDataNode.getAtom(), localVariables);

                ConstructionNode subQueryRootNode = cloneSubQuery.getRootConstructionNode();
                ConstructionNode localSubTreeRootNode = subQueryRootNode.clone();
                treeComponent.replaceNode(localDataNode, localSubTreeRootNode);

                treeComponent.addSubTree(cloneSubQuery, subQueryRootNode, localSubTreeRootNode);
            } catch (SubQueryUnificationTools.SubQueryUnificationException | IllegalTreeUpdateException e) {
                throw new QueryMergingException(e.getMessage());
            }
        }
    }

    @Override
    public ConstructionNode getClosestConstructionNode(QueryNode node) {
        if (node instanceof ConstructionNode) {
            return (ConstructionNode) node;
        }

        for (QueryNode ancestor : getAncestors(node)) {
            if (ancestor instanceof ConstructionNode) {
                return (ConstructionNode) ancestor;
            }
        }
        throw new InconsistentIntermediateQueryException("The node " + node
                + " has no ancestor that is a ConstructionNode");
    }

    /**
     * Finds ordinary data nodes.
     *
     * TODO: explain
     */
    private ImmutableList<IntensionalDataNode> findOrdinaryDataNodes(DataAtom subsumingDataAtom)
            throws InconsistentIntermediateQueryException {
        ImmutableList.Builder<IntensionalDataNode> listBuilder = ImmutableList.builder();
        try {
            for(QueryNode node : treeComponent.getNodesInBottomUpOrder()) {
                if (node instanceof IntensionalDataNode) {
                    IntensionalDataNode dataNode = (IntensionalDataNode) node;
                    if (subsumingDataAtom.hasSamePredicateAndArity(dataNode.getAtom()))
                        listBuilder.add(dataNode);
                }
            }
        } catch (IllegalTreeException e) {
            throw new InconsistentIntermediateQueryException(e.getMessage());
        }
        return listBuilder.build();
    }


    /**
     * Not appearing in the interface because users do not
     * have to worry about it.
     */
    @Override
    public IntermediateQuery clone() {
        try {
            return IntermediateQueryUtils.convertToBuilder(this).build();
        } catch (IntermediateQueryBuilderException e) {
            throw new RuntimeException("BUG (internal error)!" + e.getLocalizedMessage());
        }
    }

    @Override
    public String toString() {
        return PRINTER.stringify(this);
    }
}