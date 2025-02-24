/*
 * Copyright (c) 2023 SPARQL Anything Contributors @ http://github.com/sparql-anything
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.sparqlanything.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import io.github.sparqlanything.model.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.ext.com.google.common.collect.Sets;
import org.jsfr.json.Collector;
import org.jsfr.json.JacksonParser;
import org.jsfr.json.JsonSurfer;
import org.jsfr.json.ValueBox;
import org.jsfr.json.provider.JacksonProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static com.fasterxml.jackson.core.JsonToken.END_ARRAY;
import static com.fasterxml.jackson.core.JsonToken.END_OBJECT;

public class JSONTriplifier implements Triplifier, Slicer {

	public static final String PROPERTY_JSONPATH = "json.path";
	private static final Logger logger = LoggerFactory.getLogger(JSONTriplifier.class);

	private void transform(Properties properties, FacadeXGraphBuilder builder) throws IOException, TriplifierHTTPException {

		JsonFactory factory = JsonFactory.builder().build();

		try (InputStream us = Triplifier.getInputStream(properties)) {
			JsonParser parser = factory.createParser(us);
			// Only 1 data source expected
			transformJSON(parser, builder);
		}
	}

	private void transformJSON(JsonParser parser, FacadeXGraphBuilder builder) throws IOException {

		builder.addRoot(SPARQLAnythingConstants.DATA_SOURCE_ID);
		logger.trace("Transforming json (dataSourceId {} rootId {})", SPARQLAnythingConstants.DATA_SOURCE_ID, SPARQLAnythingConstants.ROOT_ID);
		JsonToken token = parser.nextToken();
		if (token == JsonToken.START_OBJECT) {
			logger.trace("Transforming object");
			transformObject(parser, SPARQLAnythingConstants.DATA_SOURCE_ID, SPARQLAnythingConstants.ROOT_ID, builder);
		} else if (token == JsonToken.START_ARRAY) {
			logger.trace("Transforming array");
			transformArray(parser, SPARQLAnythingConstants.DATA_SOURCE_ID, SPARQLAnythingConstants.ROOT_ID, builder);
		}

	}

	private void transformArrayItem(int i, JsonToken token, JsonParser parser, String dataSourceId, String containerId, FacadeXGraphBuilder builder) throws IOException {
		switch (token) {
			case START_ARRAY:
				String childContainerIdArray = StringUtils.join(containerId, "/_", String.valueOf(i + 1));
				builder.addContainer(dataSourceId, containerId, i + 1, childContainerIdArray);
				transformArray(parser, dataSourceId, childContainerIdArray, builder);
				break;
			case START_OBJECT:
				String childContainerId = StringUtils.join(containerId, "/_", String.valueOf(i + 1));
				builder.addContainer(dataSourceId, containerId, i + 1, childContainerId);
				transformObject(parser, dataSourceId, childContainerId, builder);
				break;
			case VALUE_FALSE:
			case VALUE_TRUE:
				builder.addValue(dataSourceId, containerId, i + 1, parser.getValueAsBoolean());
				break;
			case VALUE_NUMBER_FLOAT:
				builder.addValue(dataSourceId, containerId, i + 1, parser.getValueAsDouble());
				break;
			case VALUE_NUMBER_INT:
				builder.addValue(dataSourceId, containerId, i + 1, parser.getValueAsInt());
				break;
			case VALUE_STRING:
				builder.addValue(dataSourceId, containerId, i + 1, parser.getValueAsString());
				break;
			case VALUE_NULL:
			case END_ARRAY:
			case END_OBJECT:
			case FIELD_NAME:
			case VALUE_EMBEDDED_OBJECT:
			case NOT_AVAILABLE:
			default:
				// NOP
				break;

		}
	}

	private void transformArrayItem(int i, Object o, String dataSourceId, String containerId, FacadeXGraphBuilder builder) {
		if (o instanceof List) {
			String childContainerIdarr = StringUtils.join(containerId, "/_", String.valueOf(i + 1));
			builder.addContainer(dataSourceId, containerId, i + 1, childContainerIdarr);
			transformArray((List) o, dataSourceId, childContainerIdarr, builder);
		} else if (o instanceof Map) {
			String childContainerId = StringUtils.join(containerId, "/_", String.valueOf(i + 1));
			builder.addContainer(dataSourceId, containerId, i + 1, childContainerId);
			transformMap((Map) o, dataSourceId, childContainerId, builder);
		} else if (o instanceof Boolean) {
			builder.addValue(dataSourceId, containerId, i + 1, o);
		} else if (o instanceof Double) {
			builder.addValue(dataSourceId, containerId, i + 1, o);
		} else if (o instanceof Long) {
			String asString = ((Long) o).toString();
			int asInt = ((Long) o).intValue();
			if (asString.equals(Integer.toString(asInt))) {
				builder.addValue(dataSourceId, containerId, i + 1, ((Long) o).intValue());
			} else {
				builder.addValue(dataSourceId, containerId, i + 1, ((Long) o).doubleValue());
			}
		} else if (o instanceof Integer) {
			builder.addValue(dataSourceId, containerId, i + 1, o);
		} else if (o instanceof String) {
			builder.addValue(dataSourceId, containerId, i + 1, o);
		} else {
			throw new RuntimeException("Unsupported value type: " + o.getClass());
		}
	}

	private void transformArray(List<Object> o, String dataSourceId, String containerId, FacadeXGraphBuilder builder) {
		int i = 0;
		for (Object value : o) {
			transformArrayItem(i, value, dataSourceId, containerId, builder);
			i++;
		}
	}

	private void transformArray(JsonParser parser, String dataSourceId, String containerId, FacadeXGraphBuilder builder) throws IOException {
		int i = 0;
		JsonToken token;

		while ((token = parser.nextToken()) != END_ARRAY) {
			transformArrayItem(i, token, parser, dataSourceId, containerId, builder);
			i++;
		}
	}

	private void transformObject(JsonParser parser, String dataSourceId, String containerId, FacadeXGraphBuilder builder) throws IOException {

		JsonToken token;
		Integer coercedInt;
		String coercedStr;

		while ((token = parser.nextToken()) != END_OBJECT) {
			if (token == JsonToken.FIELD_NAME) {
				String k = parser.getText();
				token = parser.nextToken();
				switch (token) {
					case START_ARRAY:
						String childContainerIdArr = StringUtils.join(containerId, "/", Triplifier.toSafeURIString(k));
						builder.addContainer(dataSourceId, containerId, Triplifier.toSafeURIString(k), childContainerIdArr);
						transformArray(parser, dataSourceId, childContainerIdArr, builder);
						break;
					case START_OBJECT:
						String childContainerId = StringUtils.join(containerId, "/", Triplifier.toSafeURIString(k));
						builder.addContainer(dataSourceId, containerId, Triplifier.toSafeURIString(k), childContainerId);
						transformObject(parser, dataSourceId, childContainerId, builder);
						break;
					case VALUE_NUMBER_FLOAT:
						logger.trace("{} float", k);
						builder.addValue(dataSourceId, containerId, k, parser.getValueAsDouble());
						break;
					case VALUE_NUMBER_INT:
						logger.trace("{} int", k);
						coercedInt = null;
						coercedStr = null;
						boolean kIsInteger = true; // assume it is
						try {
							coercedInt = parser.getValueAsInt();
						} catch (Exception e) { // could tighten this to
							// com.fasterxml.jackson.core.exc.InputCoercionException
							logger.warn("{} can not be parsed as an integer -- treating it as a string", k);
							kIsInteger = false;
							coercedStr = parser.getValueAsString();
						}
						builder.addValue(dataSourceId, containerId, k, kIsInteger ? coercedInt : coercedStr);
						break;
					case VALUE_STRING:
						builder.addValue(dataSourceId, containerId, k, parser.getValueAsString());
						break;
					case VALUE_FALSE:
					case VALUE_TRUE:
						builder.addValue(dataSourceId, containerId, k, parser.getValueAsBoolean());
						break;
					case END_ARRAY:
					case END_OBJECT:
					case FIELD_NAME:
					case VALUE_EMBEDDED_OBJECT:
					case NOT_AVAILABLE:
					case VALUE_NULL:
					default:
						break;
				}
			} else {
				throw new IOException("Unexpected token in object");
			}
		}

	}

	private void transformMap(Map o, String dataSourceId, String containerId, FacadeXGraphBuilder builder) {
		Integer coercedInt;
		String coercedStr;
		Iterator<Map.Entry> it = o.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry entry = it.next();
			String k = (String) entry.getKey();
			Object val = entry.getValue();
			if (val instanceof List) {
				String childContainerIdArr = StringUtils.join(containerId, "/", Triplifier.toSafeURIString(k));
				builder.addContainer(dataSourceId, containerId, Triplifier.toSafeURIString(k), childContainerIdArr);
				transformArray((List) val, dataSourceId, childContainerIdArr, builder);
			} else if (val instanceof Map) {
				String childContainerId = StringUtils.join(containerId, "/", Triplifier.toSafeURIString(k));
				builder.addContainer(dataSourceId, containerId, Triplifier.toSafeURIString(k), childContainerId);
				transformMap((Map) val, dataSourceId, childContainerId, builder);
			} else if (val instanceof Double) {
				builder.addValue(dataSourceId, containerId, Triplifier.toSafeURIString(k), val);
			} else if (val instanceof Long) {
				// What datatype is supposed to be long. If cast to int has the same form, keep
				// integer, otherwise double
				String asString = ((Long) val).toString();
				int asInt = ((Long) val).intValue();
				if (asString.equals(Integer.toString(asInt))) {
					builder.addValue(dataSourceId, containerId, Triplifier.toSafeURIString(k), ((Long) val).intValue());
				} else {
					builder.addValue(dataSourceId, containerId, Triplifier.toSafeURIString(k), ((Long) val).doubleValue());
				}
			} else if (val instanceof Integer) {
				builder.addValue(dataSourceId, containerId, Triplifier.toSafeURIString(k), val);
			} else if (val instanceof Boolean) {
				builder.addValue(dataSourceId, containerId, Triplifier.toSafeURIString(k), val);
			} else if (val instanceof String) {
				builder.addValue(dataSourceId, containerId, Triplifier.toSafeURIString(k), val);
			} else {
				throw new RuntimeException("Unsupported value type: " + val.getClass());
			}
		}
	}

	@Override
	public void triplify(Properties properties, FacadeXGraphBuilder builder) throws IOException, TriplifierHTTPException {

		List<String> jsonPaths = Triplifier.getPropertyValues(properties, "json.path");
		if (!jsonPaths.isEmpty()) {
			transformFromJSONPath(properties, builder, jsonPaths);
		} else {
			transform(properties, builder);
		}
	}

	@Override
	public Set<String> getMimeTypes() {
		return Sets.newHashSet("application/json");
	}

	@Override
	public Set<String> getExtensions() {
		return Sets.newHashSet("json");
	}

	private void transformFromJSONPath(Properties properties, FacadeXGraphBuilder builder, List<String> jsonPaths) throws TriplifierHTTPException, IOException {
		JsonSurfer surfer = new JsonSurfer(JacksonParser.INSTANCE, JacksonProvider.INSTANCE);
		final InputStream us = Triplifier.getInputStream(properties);
		Collector collector = surfer.collector(us);
		List<ValueBox<Collection<Object>>> matches = new ArrayList<ValueBox<Collection<Object>>>();

		for (String jpath : jsonPaths) {
			ValueBox<Collection<Object>> m = collector.collectAll(jpath);
			matches.add(m);
		}

		try (us) {
			collector.exec();
			Iterator<ValueBox<Collection<Object>>> matchesIterator = matches.iterator();
			// Only 1 data source expected
			builder.addRoot("");
			int c = 0;
			while (matchesIterator.hasNext()) {
				for (Object o : matchesIterator.next().get()) {
					transformArrayItem(c, o, "", SPARQLAnythingConstants.ROOT_ID, builder);
					c++;
				}
			}
		}
	}

	@Override
	public Iterable<Slice> slice(Properties properties) throws IOException, TriplifierHTTPException {
		List<String> jsonPaths = Triplifier.getPropertyValues(properties, PROPERTY_JSONPATH);
		if (!jsonPaths.isEmpty()) {
			return sliceFromJSONPath(properties);
		} else {
			return sliceFromArray(properties);
		}

	}

	private Iterable<Slice> sliceFromJSONPath(Properties properties) throws TriplifierHTTPException, IOException {
		JsonSurfer surfer = new JsonSurfer(JacksonParser.INSTANCE, JacksonProvider.INSTANCE);
		final InputStream us = Triplifier.getInputStream(properties);
		Collector collector = surfer.collector(us);
		List<String> jsonPathExpr = new ArrayList<String>();
		final Set<ValueBox<Collection<Object>>> matches = new HashSet<ValueBox<Collection<Object>>>();
		List<String> jsonPaths = Triplifier.getPropertyValues(properties, PROPERTY_JSONPATH);
		for (String jpath : jsonPaths) {
			ValueBox<Collection<Object>> m = collector.collectAll(jpath);
			matches.add(m);
		}

		try (us) {
			collector.exec();
			Iterator<ValueBox<Collection<Object>>> matchesIterator = matches.iterator();
			// Only 1 data source expected
			return new Iterable<Slice>() {
				@Override
				public Iterator<Slice> iterator() {

					log.debug("Iterating slices");
					return new Iterator<Slice>() {
						int sln = 0;
						Object next = null;
						Iterator<Object> objectIterator = null;

						@Override
						public boolean hasNext() {
							if (next != null) {
								return true;
							}
							next = nextObject();
//
							return next != null;

						}

						Object nextObject() {
							Object toReturn = null;
							// Iterate until there is a match!
							while (objectIterator == null || !objectIterator.hasNext()) {
								if (matchesIterator.hasNext()) {
									objectIterator = matchesIterator.next().get().iterator();
								} else {
									// No more iterators
									objectIterator = null;
									break;
								}
							}
							if (objectIterator != null && objectIterator.hasNext()) {
								toReturn = objectIterator.next();
							}
							return toReturn;
						}

						@Override
						public Slice next() {
							if (next == null) {
								return null;
							}
							sln++;
							log.trace("next slice: {}", sln);
							Object obj = next;
							next = null;
							return JSONPathSlice.makeSlice(obj, sln, "");
						}
					};
				}
			};
		}
	}

	private Iterable<Slice> sliceFromArray(Properties properties) throws IOException, TriplifierHTTPException {
		// XXX How do we close the input stream?
		final InputStream us = Triplifier.getInputStream(properties);
		JsonFactory factory = JsonFactory.builder().build();
		JsonParser parser = factory.createParser(us);
		JsonToken token = parser.nextToken();
		// If the root is an array.
		if (token == JsonToken.START_ARRAY) {

		} else {
			throw new IOException("Not a JSON array");
		}

		// Only 1 data source expected
		return new Iterable<Slice>() {
			JsonToken next = null;

			@Override
			public Iterator<Slice> iterator() {
				log.debug("Iterating slices");
				return new Iterator<Slice>() {
					int sln = 0;

					@Override
					public boolean hasNext() {
						if (next != null) {
							return true;
						}
						try {
							next = parser.nextToken();
							while (next == JsonToken.END_ARRAY || next == END_OBJECT) {
								next = parser.nextToken();
							}
						} catch (IOException e) {
							next = null;
							return false;
						}
						return next != null;

					}

					@Override
					public Slice next() {
						if (next == null) {
							return null;
						}
						sln++;
						log.trace("next slice: {}", sln);
						JsonToken tk = next;
						next = null;
						return JSONSlice.makeSlice(tk, parser, sln, "");
					}
				};
			}
		};
	}

	@Override
	public void triplify(Slice slice, Properties p, FacadeXGraphBuilder builder) {
		builder.addRoot(slice.getDatasourceId());
		try {
			if (slice instanceof JSONSlice) {
				JSONSlice jslice = (JSONSlice) slice;
				// Method is 0-indexed
				transformArrayItem(jslice.iteration() - 1, jslice.get(), jslice.getParser(), jslice.getDatasourceId(), SPARQLAnythingConstants.ROOT_ID, builder);
			} else if (slice instanceof JSONPathSlice) {
				JSONPathSlice jslice = (JSONPathSlice) slice;
				// Method is 0-indexed
				transformArrayItem(jslice.iteration() - 1, jslice.get(), jslice.getDatasourceId(), SPARQLAnythingConstants.ROOT_ID, builder);
			}
		} catch (IOException e) {
			log.error("An error occurred while transforming slice {}: {}", slice.iteration(), e);
		}
	}

	private void processSlice(int iteration, String rootId, String dataSourceId, JsonToken token, JsonParser parser, FacadeXGraphBuilder builder) {
		String sliceContainerId = StringUtils.join(rootId, "#slice", iteration);
		builder.addContainer(dataSourceId, rootId, iteration, sliceContainerId);

	}
}
