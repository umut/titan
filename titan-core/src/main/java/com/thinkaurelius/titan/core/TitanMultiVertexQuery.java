package com.thinkaurelius.titan.core;

import com.tinkerpop.blueprints.*;

import java.util.Collection;
import java.util.Map;

/**
 * A MultiVertexQuery is identical to a {@link VertexQuery} but executed against a set of vertices simultaneously.
 * In other words, {@link TitanMultiVertexQuery} allows identical {@link VertexQuery} executed against a non-trivial set
 * of vertices to be executed in one batch which can significantly reduce the query latency.
 * <p/>
 * The query specification methods are identical to {@link VertexQuery}. The result set method return Maps from the specified
 * set of anchor vertices to their respective individual result sets.
 * <p/>
 * Note, that the {@link #limit(int)} constraint applies to each individual result set.
 *
 * @author Matthias Broecheler (me@matthiasb.com)
 */

public interface TitanMultiVertexQuery<Q extends TitanMultiVertexQuery<Q>> extends BaseVertexQuery<Q> {

   /* ---------------------------------------------------------------
    * Query Specification
    * ---------------------------------------------------------------
    */

    /**
     * Adds the given vertex to the set of vertices against which to execute this query.
     *
     * @param vertex
     * @return this query builder
     */
    public TitanMultiVertexQuery addVertex(TitanVertex vertex);

    /**
     * Adds the given collection of vertices to the set of vertices against which to execute this query.
     *
     * @param vertices
     * @return this query builder
     */
    public TitanMultiVertexQuery addAllVertices(Collection<TitanVertex> vertices);


    @Override
    public Q adjacent(TitanVertex vertex);

    @Override
    public Q types(TitanType... type);

    @Override
    public Q labels(String... labels);

    @Override
    public Q keys(String... keys);

    @Override
    public Q direction(Direction d);

    @Override
    public Q has(TitanKey key, Object value);

    @Override
    public Q has(TitanLabel label, TitanVertex vertex);

    @Override
    public Q has(String key);

    @Override
    public Q hasNot(String key);

    @Override
    public Q has(String type, Object value);

    @Override
    public Q hasNot(String key, Object value);


    @Override
    public Q has(TitanKey key, Predicate predicate, Object value);

    @Override
    public Q has(String key, Predicate predicate, Object value);

    @Override
    public <T extends Comparable<?>> Q interval(String key, T start, T end);

    @Override
    public <T extends Comparable<?>> Q interval(TitanKey key, T start, T end);

    @Override
    public Q limit(int limit);

    @Override
    public Q orderBy(String key, Order order);

    @Override
    public Q orderBy(TitanKey key, Order order);

   /* ---------------------------------------------------------------
    * Query execution
    * ---------------------------------------------------------------
    */

    /**
     * Returns an iterable over all incident edges that match this query for each vertex
     *
     * @return Iterable over all incident edges that match this query for each vertex
     */
    public Map<TitanVertex, Iterable<TitanEdge>> titanEdges();

    /**
     * Returns an iterable over all incident properties that match this query for each vertex
     *
     * @return Iterable over all incident properties that match this query for each vertex
     */
    public Map<TitanVertex, Iterable<TitanProperty>> properties();


    /**
     * Returns an iterable over all incident relations that match this query for each vertex
     *
     * @return Iterable over all incident relations that match this query for each vertex
     */
    public Map<TitanVertex, Iterable<TitanRelation>> relations();

    /**
     * Retrieves all vertices connected to each of the query's central vertices by edges
     * matching the conditions defined in this query.
     * <p/>
     * No guarantee is made as to the order in which the vertices are returned.
     *
     * @return An iterable of all vertices connected to each of the query's central vertices by matching edges
     */
    public Map<TitanVertex, Iterable<TitanVertex>> vertices();

    /**
     * Retrieves all vertices connected to each of the query's central vertices by edges
     * matching the conditions defined in this query.
     * <p/>
     * No guarantee is made as to the order in which the vertices are listed. Use {@link com.thinkaurelius.titan.core.VertexList#sort()}
     * to sort by vertex idAuthorities most efficiently.
     * <p/>
     * The query engine will determine the most efficient way to retrieve the vertices that match this query.
     *
     * @return A list of all vertices' ids connected to each of the query's central vertex by matching edges
     */
    public Map<TitanVertex, VertexList> vertexIds();

}
