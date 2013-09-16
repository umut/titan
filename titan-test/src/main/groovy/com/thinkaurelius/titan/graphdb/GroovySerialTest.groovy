package com.thinkaurelius.titan.graphdb

import static org.junit.Assert.*

import org.apache.commons.configuration.Configuration
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.carrotsearch.junitbenchmarks.BenchmarkOptions
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Iterables
import com.thinkaurelius.titan.core.TitanEdge
import com.thinkaurelius.titan.core.TitanGraph
import com.thinkaurelius.titan.core.TitanKey
import com.thinkaurelius.titan.core.TitanTransaction
import com.thinkaurelius.titan.core.TitanVertex
import com.thinkaurelius.titan.diskstorage.StorageException
import com.thinkaurelius.titan.graphdb.FakeVertex
import com.thinkaurelius.titan.graphdb.database.StandardTitanGraph
import com.thinkaurelius.titan.testutil.GraphGenerator
import com.thinkaurelius.titan.testutil.JUnitBenchmarkProvider
import com.tinkerpop.frames.FramedGraph
import com.tinkerpop.gremlin.Tokens.T
import com.tinkerpop.gremlin.groovy.Gremlin

@BenchmarkOptions(warmupRounds=1, benchmarkRounds=1)
public abstract class GroovySerialTest {
    
    @Rule
    public TestRule benchmark = JUnitBenchmarkProvider.get();
    
    static {
        Gremlin.load()
    }
    
    protected static final int VERTEX_COUNT = 10 * 100;
    protected static final int EDGE_COUNT = VERTEX_COUNT * 5;
    protected static final int VERTEX_PROP_COUNT = 20;
    protected static final int EDGE_PROP_COUNT = 10;
    
    private static final String DEFAULT_EDGE_LABEL = "el_0";
    private static final int TX_COUNT = 5;
    private static final int OPS_PER_TX = 100;
    
    protected final Random random = new Random(7); // Arbitrary seed; no special significance except that it remains constant between comparable runs
    
    protected final GraphGenerator gen;
    protected final TitanTransaction tx;
    protected final TitanGraph graph;
    protected final Configuration conf;
    
    private static final Logger log = LoggerFactory.getLogger(GroovySerialTest.class);


    public GroovySerialTest(Configuration conf) throws StorageException {
        this.conf = conf;
    }
    
    public void open() {
        if (null == graph)
            try {
                graph = getGraph();
            } catch (StorageException e) {
                throw new RuntimeException(e);
            }
        if (null == gen)
            gen = getGenerator();
        tx = graph.newTransaction();
    }

    protected abstract StandardTitanGraph getGraph() throws StorageException;
    protected abstract GraphGenerator getGenerator();
    
    public void close() {
        if (null != tx && tx.isOpen())
            tx.commit();

        if (null != graph)
            graph.shutdown();
    }

    public void newTx() {
        if (null != tx && tx.isOpen())
            tx.commit();
        
        tx = graph.newTransaction();
    }
    
    @Before
    public void setUp() throws Exception {
        open();
    }
    
    /**
     * Retrieve 100 vertices, each by its exact uid. Repeat the process with
     * different uids in 50 transactions. The transactions are read-only and are
     * all rolled back rather than committed.
     * 
     */
    @Test
    public void testVertexUidLookup() throws Exception {
        rollbackTx({ txIndex, tx ->
            TitanKey uidKey = tx.getPropertyKey(GraphGenerator.UID_PROP)
            for (int u = 0; u < GroovySerialTest.OPS_PER_TX; u++) {
                long uid = txIndex * GroovySerialTest.OPS_PER_TX + u
                TitanVertex v = tx.getVertex(uidKey, uid)
                assertNotNull(v)
                assertEquals(uid, v.getProperty(uidKey))
            }
        })
    }
    
    /**
     * Same as {@link #testVertexUidLookup}, except add or modify a single property
     * on every vertex retrieved and commit the changes in each transaction
     * instead of rolling back.
     * 
     */
    @Test
    public void testVertexPropertyModification() {
        commitTx({ txIndex, tx ->
            TitanKey uidKey = tx.getPropertyKey(GraphGenerator.UID_PROP)
            for (int u = 0; u < GroovySerialTest.OPS_PER_TX; u++) {
                long uid = txIndex * GroovySerialTest.OPS_PER_TX + u
                TitanVertex v = tx.getVertex(uidKey, uid)
                assertNotNull(v)
                assertEquals(uid, v.getProperty(uidKey))
                Set<String> props = ImmutableSet.copyOf(v.getPropertyKeys())
                String propKeyToModify = gen.getVertexPropertyName(random.nextInt(VERTEX_PROP_COUNT))
                if (props.contains(propKeyToModify)) {
                    v.removeProperty(propKeyToModify)
                    v.setProperty(propKeyToModify, random.nextInt(GraphGenerator.MAX_VERTEX_PROP_VALUE))
                }
            }
        })
    }

    /**
     * Retrieve a vertex by randomly chosen uid, then delete its first edge.
     * 
     */
    @Test
    public void testEdgeRemoval() {
        int deleted = 0;
        commitTx({ txIndex, tx ->
            TitanKey uidKey = tx.getPropertyKey(GraphGenerator.UID_PROP)
            for (int u = 0; u < GroovySerialTest.OPS_PER_TX; u++) {
                long uid = Math.abs(random.nextLong()) % gen.getMaxUid()
                TitanVertex v = tx.getVertex(uidKey, uid)
                assertNotNull(v)
                Iterable<TitanEdge> edges = v.getEdges()
                assertNotNull(edges)
                TitanEdge e = Iterables.getFirst(edges, null)
                if (null == e) {
                    u--
                    continue
                }
                e.remove()
                deleted++
            }
        })
        assertEquals(TX_COUNT * GroovySerialTest.OPS_PER_TX, deleted);
    }

    /**
     * Retrieve a vertex by randomly chosen uid, then remove the vertex. After
     * removing all vertices, add new vertices with the same uids as those
     * removed (but no incident edges or properties besides uid)
     * 
     */
    @Test
    public void testVertexRemoval() {
        Set<Long> visited = new HashSet<Long>(TX_COUNT);
        commitTx({ txIndex, tx ->
            def uid
            def v = null
            while (null == v) {
                uid = Math.abs(random.nextLong()) % gen.getMaxUid();
                TitanKey uidKey = tx.getPropertyKey(GraphGenerator.UID_PROP);
                v = tx.getVertex(uidKey, uid);
            }
            assertNotNull(v)
            tx.removeVertex(v)
            visited.add(uid)
        })
        
        tx = graph.newTransaction()
        // Insert new vertices with the same uids as removed vertices, but no edges or properties besides uid
        TitanKey uidKey = tx.getPropertyKey(GraphGenerator.UID_PROP)
        for (long uid : visited) {
            TitanVertex v = tx.addVertex()
            v.setProperty(uidKey, uid)
        }
        tx.commit()
    }
    
    /**
     * JUnitBenchmarks appears to include {@code Before} method execution in round-avg times.
     * This method has no body and exists only to measure that overhead.
     */
    @Test
    public void testNoop() {
        // Do nothing
    }
    
    /**
     * Query for edges using a vertex-centric index on a known high-out-degree vertex
     * 
     */
    @Test
    public void testVertexCentricIndexQuery() {
        noTx({ index ->
            long uid = gen.getHighDegVertexUid()
            String label = gen.getHighDegEdgeLabel()
            String pkey  = gen.getHighDegEdgeProp()
            def v = graph.V(GraphGenerator.UID_PROP, uid).next();
            
            // Only uncomment this in debugging...
//            def c1 = v.outE(label).count()
//            assertEquals(Math.round(VERTEX_COUNT - 1), c1)
            
            // TODO add a T.gt to limit the total size to ~100 or something equivalently fast
            def c2 = v.outE(label).has(pkey, T.lt, (int)(gen.getMaxUid() / 4)).count()
            assertEquals(Math.round(VERTEX_COUNT / 4) - 1, c2)
        })
    }
    
    /**
     * Same query as in {@link #testEdgePropertyQuery()}, except with limit(1).
     * 
     */
    @Test
    public void testLimitedEdgeQuery() {
        rollbackTx({ txIndex, tx ->
            for (int u = 0; u < GroovySerialTest.OPS_PER_TX; u++) {
                int n = Iterables.size(tx.query().limit(1).has(gen.getEdgePropertyName(0), 0).edges());
                assertTrue(0 <= n);
                assertTrue(n <= 1);
            }
        })
    }
    
    /**
     * Retrieve all vertices with an OUT-unique standard-indexed property and limit(1).
     * 
     */
    @Test
    public void testLimitedVertexQuery() {
        rollbackTx({ txIndex, tx ->
            for (int u = 0; u < GroovySerialTest.OPS_PER_TX; u++) {
                int n = Iterables.size(tx.query().limit(1).has(gen.getVertexPropertyName(0), 0).vertices());
                assertTrue(0 <= n);
                assertTrue(n <= 1);
            }
        })
    }
    
    /**
     * Retrieve all vertices with uid equal to a randomly chosen value. Note
     * that uid is standard-indexed and BOTH-unique, so this query should return
     * one vertex in practice, but no limit is specified.
     * 
     */
    @Test
    public void testVertexPropertyQuery() {
        rollbackTx({ txIndex, tx ->
            for (int u = 0; u < GroovySerialTest.OPS_PER_TX; u++) {
                int n = Iterables.size(tx.query().has("uid", Math.abs(random.nextLong()) % gen.getMaxUid()).vertices());
                assertTrue(1 == n);
            }
        })
    }
    
    /**
     * Retrieve all edges with a single OUT-unique standard-indexed property. No limit.
     * 
     */
    @Test
    public void testEdgePropertyQuery() {
        rollbackTx({ txIndex, tx ->
            int n = Iterables.size(tx.query().has(gen.getEdgePropertyName(0), 0).edges())
            assertTrue(0 < n)
        })
    }
    
    /**
     * Retrieve all edges matching on has(...) clause and one hasNot(...)
     * clause, both on OUT-unique standard-indexed properties. No limit.
     * 
     */
    @Test
    public void testHasAndHasNotEdgeQuery() {
        rollbackTx({ txIndex, tx ->
            int n = Iterables.size(tx.query().has(gen.getEdgePropertyName(0), 0).hasNot(gen.getEdgePropertyName(1), 0).edges());
            assertTrue(0 < n);
        })
    }
    
    /**
     * Retrieve all vertices matching on has(...) clause and one hasNot(...)
     * clause, both on OUT-unique standard-indexed properties. No limit.
     * 
     */
    @Test
    public void testHasAndHasNotVertexQuery() {
        rollbackTx({ txIndex, tx ->
            for (int u = 0; u < GroovySerialTest.OPS_PER_TX; u++) {
                int n = Iterables.size(tx.query().has(gen.getVertexPropertyName(0), 0).hasNot(gen.getVertexPropertyName(1), 0).vertices());
                assertTrue(0 < n);
            }
        })
    }
    
    /**
     * Retrieve vertices by uid, then retrieve their associated properties. All
     * access is done through a FramedGraph interface. This is inspired by part
     * of the ONLAB benchmark, but written separately ("from scratch").
     * 
     */
    @Test
    public void testFramedUidAndPropertyLookup() {
        FramedGraph<TitanGraph> fg = new FramedGraph<TitanGraph>(graph);
        int totalNonNullProps = 0;
        for (int t = 0; t < TX_COUNT; t++) {
            for (int u = 0; u < GroovySerialTest.OPS_PER_TX; u++) {
                Long uid = (long)t * GroovySerialTest.OPS_PER_TX + u;
                Iterable<FakeVertex> iter = fg.getVertices(GraphGenerator.UID_PROP, uid, FakeVertex.class);
                boolean visited = false;
                for (FakeVertex fv : iter) {
                    assertTrue(uid == fv.getUid().longValue());
                    // Three property retrievals, as in ONLAB, with some
                    // busywork to attempt to prevent the runtime or compiler
                    // from optimizing this all away
                    int nonNullProps = 0;
                    if (null != fv.getProp0())
                        nonNullProps++;
                    if (null != fv.getProp1())
                        nonNullProps++;
                    if (null != fv.getProp2())
                        nonNullProps++;
                    assertTrue(0 <= nonNullProps);
                    totalNonNullProps += nonNullProps;
                    visited = true;
                }
                assertTrue(visited);
            }
        }
        // The chance of this going to zero during random scale-free graph
        // generation (for a graph of non-trivial size) is insignificant.
        assertTrue(0 < totalNonNullProps);
    }
    
    
    /*
     * Helper methods
     */
    
    def void rollbackTx(closure) {
        doTx(closure, { tx -> tx.rollback() })
    }
    
    def void commitTx(closure) {
        doTx(closure, { tx -> tx.commit() })
    }

    def void doTx(txWork, afterWork) {
        for (int t = 0; t < TX_COUNT; t++) {
            tx = graph.newTransaction();
            txWork.call(t, tx)
            afterWork.call(tx)
        }
    }
    
    def void noTx(closure) {
        for (int t = 0; t < TX_COUNT; t++) {
            closure.call(t)
            graph.rollback()
        }
    }
       
}