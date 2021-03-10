package com.github.spiceh2020.sparql.anything.rdf.test;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.util.Properties;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.Test;

import com.github.spiceh2020.sparql.anything.spreadsheet.RDFTriplifier;

public class TestTriplifier {

	private static Graph getTestGraph() {
		Graph gs1 = GraphFactory.createGraphMem();
		gs1.add(new Triple(NodeFactory.createURI("http://example.org/a"), NodeFactory.createURI("http://example.org/b"),
				NodeFactory.createURI("http://example.org/c")));
		return gs1;
	}

	@Test
	public void testNTriples() {
		RDFTriplifier st = new RDFTriplifier();
		URL url = st.getClass().getClassLoader().getResource("ntriples.nt");
		Properties p = new Properties();
		DatasetGraph dg;
		try {
			dg = st.triplify(url, p);
			assertTrue(dg.getDefaultGraph().isIsomorphicWith(getTestGraph()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testTurtle() {
		RDFTriplifier st = new RDFTriplifier();
		URL url = st.getClass().getClassLoader().getResource("turtle.ttl");
		Properties p = new Properties();
		DatasetGraph dg;
		try {
			dg = st.triplify(url, p);
			assertTrue(dg.getDefaultGraph().isIsomorphicWith(getTestGraph()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testJSONLD() {
		RDFTriplifier st = new RDFTriplifier();
		URL url = st.getClass().getClassLoader().getResource("jsonld.jsonld");
		Properties p = new Properties();
		DatasetGraph dg;
		try {
			dg = st.triplify(url, p);
			assertTrue(dg.getDefaultGraph().isIsomorphicWith(getTestGraph()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testRDF() {
		RDFTriplifier st = new RDFTriplifier();
		URL url = st.getClass().getClassLoader().getResource("rdf.rdf");
		Properties p = new Properties();
		DatasetGraph dg;
		try {
			dg = st.triplify(url, p);
			assertTrue(dg.getDefaultGraph().isIsomorphicWith(getTestGraph()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testOWL() {
		RDFTriplifier st = new RDFTriplifier();
		URL url = st.getClass().getClassLoader().getResource("owl.owl");
		Properties p = new Properties();
		DatasetGraph dg;
		try {
			dg = st.triplify(url, p);
			assertTrue(dg.getDefaultGraph().isIsomorphicWith(getTestGraph()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testNQ() {
		RDFTriplifier st = new RDFTriplifier();
		URL url = st.getClass().getClassLoader().getResource("nquads.nq");
		Properties p = new Properties();
		DatasetGraph dg;
		try {
			dg = st.triplify(url, p);
			assertTrue(dg.getGraph(NodeFactory.createURI("http://example.org/g")).isIsomorphicWith(getTestGraph()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testTRDF() {
		RDFTriplifier st = new RDFTriplifier();
		URL url = st.getClass().getClassLoader().getResource("trdf.trdf");
		Properties p = new Properties();
		DatasetGraph dg;
		try {
			dg = st.triplify(url, p);
			assertTrue(dg.getGraph(NodeFactory.createURI("http://example.org/g")).isIsomorphicWith(getTestGraph()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testTRIG() {
		RDFTriplifier st = new RDFTriplifier();
		URL url = st.getClass().getClassLoader().getResource("trig.trig");
		Properties p = new Properties();
		DatasetGraph dg;
		try {
			dg = st.triplify(url, p);
			assertTrue(dg.getGraph(NodeFactory.createURI("http://example.org/g")).isIsomorphicWith(getTestGraph()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testTRIX() {
		RDFTriplifier st = new RDFTriplifier();
		URL url = st.getClass().getClassLoader().getResource("trix.trix");
		Properties p = new Properties();
		DatasetGraph dg;
		try {
			dg = st.triplify(url, p);
			assertTrue(dg.getGraph(NodeFactory.createURI("http://example.org/g")).isIsomorphicWith(getTestGraph()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
