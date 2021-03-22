package com.github.spiceh2020.sparql.anything.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;

import org.apache.commons.io.IOUtils;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.ARQ;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.engine.ExecutionContext;
import org.apache.jena.sparql.engine.main.OpExecutor;
import org.apache.jena.sparql.engine.main.OpExecutorFactory;
import org.apache.jena.sparql.engine.main.QC;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.Test;

import com.github.spiceh2020.sparql.anything.engine.FacadeXOpExecutor;
import com.github.spiceh2020.sparql.anything.engine.TriplifierRegister;
import com.github.spiceh2020.sparql.anything.engine.TriplifierRegisterException;

public class AppTest {
	public static String PREFIX = "http://example.org/";

	static DatasetGraph createExampleGraph() {
		DatasetGraph dg = DatasetGraphFactory.create();
		Graph g = GraphFactory.createGraphMem();
		g.add(new Triple(NodeFactory.createURI(PREFIX + "s"), NodeFactory.createURI(PREFIX + "p"),
				NodeFactory.createURI(PREFIX + "o")));
		dg.addGraph(NodeFactory.createURI(PREFIX + "g"), g);
		dg.setDefaultGraph(g);
		return dg;
	}

	;
	@Test
	public void testConstructAndSelect() throws IOException {
		System.out.println(new TestTriplifier().getClass().getName());
//		Triplifier t = new TestTriplifier();
		try {

			OpExecutorFactory customExecutorFactory = new OpExecutorFactory() {
				@Override
				public OpExecutor create(ExecutionContext execCxt) {
					return new FacadeXOpExecutor(execCxt);
				}
			};

			QC.setFactory(ARQ.getContext(), customExecutorFactory);

			TriplifierRegister.getInstance().registerTriplifier("com.github.spiceh2020.sparql.anything.test.TestTriplifier", new String[]{"test"}, new String[]{"test-mime"});

			Dataset kb = DatasetFactory.createGeneral();
			Query q = QueryFactory
					.create("CONSTRUCT {?s ?p ?o} WHERE { SERVICE<x-sparql-anything:http://example.org/file.test>{?s ?p ?o}}");

			Model m = ModelFactory.createDefaultModel();
			m.add(m.createResource(PREFIX + "s"), m.createProperty(PREFIX + "p"), m.createResource(PREFIX + "o"));

			assertTrue(QueryExecutionFactory.create(q, kb).execConstruct().isIsomorphicWith(m));

			Query select = QueryFactory.create(
					"SELECT DISTINCT ?g ?s ?p ?o WHERE { SERVICE<x-sparql-anything:http://example.org/file.test>{GRAPH ?g {?s ?p ?o}}}");
			ResultSet rs = QueryExecutionFactory.create(select, kb).execSelect();
			QuerySolution qs = rs.next();

			assertTrue(qs.getResource("g").getURI().equals(PREFIX + "g"));
			assertTrue(qs.getResource("s").getURI().equals(PREFIX + "s"));
			assertTrue(qs.getResource("p").getURI().equals(PREFIX + "p"));
			assertTrue(qs.getResource("o").getURI().equals(PREFIX + "o"));

			TriplifierRegister.getInstance().removeTriplifier("com.github.spiceh2020.sparql.anything.test.TestTriplifier");
		} catch (TriplifierRegisterException e) {
			e.printStackTrace();
		}
	}

	;
	@Test
	public void testRelativePath() throws IOException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
		Class.forName("com.github.spiceh2020.sparql.anything.test.TestTriplifier2").getConstructor().newInstance();
		try {

			OpExecutorFactory customExecutorFactory = new OpExecutorFactory() {
				@Override
				public OpExecutor create(ExecutionContext execCxt) {
					return new FacadeXOpExecutor(execCxt);
				}
			};

			QC.setFactory(ARQ.getContext(), customExecutorFactory);

//			TriplifierRegister.getInstance().registerTriplifier(t);
			TriplifierRegister.getInstance().registerTriplifier("com.github.spiceh2020.sparql.anything.test.TestTriplifier2", new String[]{"test2"}, new String[]{"test-mime2"});

			Dataset kb = DatasetFactory.createGeneral();

			Query select = QueryFactory.create(
					"SELECT DISTINCT ?g ?s ?p ?o WHERE { SERVICE<x-sparql-anything:media-type=test-mime2,location=src/main/resources/test.json> {GRAPH ?g {?s ?p ?o}}}");

			ResultSet rs = QueryExecutionFactory.create(select, kb).execSelect();
			QuerySolution qs = rs.next();

			String content = IOUtils.toString(new File("src/main/resources/test.json").toURI().toURL(),
					Charset.defaultCharset());

			assertTrue(qs.getResource("g").getURI().equals(PREFIX + "g"));
			assertTrue(qs.getResource("s").getURI().equals(PREFIX + "s"));
			assertTrue(qs.getResource("p").getURI().equals(PREFIX + "p"));
			assertTrue(qs.get("o").toString().replace("\\", "").equals(content));

			TriplifierRegister.getInstance().removeTriplifier("com.github.spiceh2020.sparql.anything.test.TestTriplifier");

		} catch (TriplifierRegisterException e) {
			e.printStackTrace();
		}
	}

}
