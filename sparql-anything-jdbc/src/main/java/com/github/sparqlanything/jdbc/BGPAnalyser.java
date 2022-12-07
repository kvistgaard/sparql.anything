/*
 * Copyright (c) 2022 SPARQL Anything Contributors @ http://github.com/sparql-anything
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.github.sparqlanything.jdbc;

import com.github.sparqlanything.model.Triplifier;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.algebra.op.OpBGP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class BGPAnalyser {
	final static Logger L = LoggerFactory.getLogger(BGPAnalyser.class);
	private Properties properties;
	private String namesNamespace;
	private OpBGP opBGP;
	private Translation translation;

	private Map<Node, Interpretation> constraints = null;
	private InconsistentAssumptionException exception;

	public BGPAnalyser(Properties properties, OpBGP opBGP){
		this.properties = properties;
		this.opBGP = opBGP;
		this.translation = new Translation(properties);
		interpret();
	}

	public Map<Node, Interpretation> interpretations(){
		if(this.constraints == null){
			return Collections.emptyMap();
		}
		return Collections.unmodifiableMap(this.constraints);
	}

	public boolean isException(){
		return this.exception != null;
	}

	private void interpret()  {
		this.constraints = new HashMap<Node,Interpretation>();
		List<Triple> tripleList = opBGP.getPattern().getList();
		try {
			// A) Gather Interpretations from the BGP, iterate until no new Interpreations are retrieved
			boolean lookForConstraints = true;
			while (lookForConstraints) {
				lookForConstraints = false;
				for (Triple triple : tripleList) {
					L.trace("intepreting triple: {}", triple);
					Node subject = triple.getSubject();
					Node predicate = triple.getPredicate();
					Node object = triple.getObject();

					// If node was observed before, retrieve previous Interpretations
					Interpretation subjectInterpretation = constrainSubject(subject, triple);
					lookForConstraints = updateConstraint(subject, subjectInterpretation);
					Interpretation predicateInterpretation = constrainPredicate(predicate, triple);
					lookForConstraints = lookForConstraints || updateConstraint(predicate, predicateInterpretation);
					Interpretation objectInterpretation = constrainObject(object, triple);
					lookForConstraints = lookForConstraints || updateConstraint(object, objectInterpretation);
					L.trace("look for constraints: {}", lookForConstraints);
				}
			}
		}catch(InconsistentAssumptionException e){
			this.exception = e;
			L.warn("No solution for BGP", opBGP);
			if(L.isDebugEnabled()){
				L.error("Debug enabled. Logging InconsistentAssumptionException.");
			}
			L.error("No solution for BGP (reason)", opBGP);
		}
	}

	private boolean interpretedAs(Node n, Class<? extends Interpretation> as){
		if(!constraints.containsKey(n)){
			return false;
		}
		return constraints.get(n).getClass().equals(as);
	}

	private Interpretation constrainSubject(Node subject, Triple triple) throws InconsistentAssumptionException {

		// ContainerTable(S) <- URI(S) | FXRoot(O) | SlotRow(P) | ContainerRow(O)
		if(subject.isURI() || interpretedAs(triple.getObject(), Assumption.FXRoot.class) ||
			interpretedAs(triple.getPredicate(), Assumption.SlotRow.class) ||
			interpretedAs(triple.getObject(), Assumption.ContainerRow.class)){
			return new Assumption.ContainerTable();
		}

		// ContainerRow(S) <- SlotColumn(P) | SlotValue(O) | TypeTable(O)
		if(interpretedAs(triple.getPredicate(), Assumption.SlotColumn.class) ||
				interpretedAs(triple.getObject(), Assumption.TypeTable.class) ||
				interpretedAs(triple.getObject(), Assumption.SlotValue.class) ){
			return new Assumption.ContainerRow();
		}

		// if subject is named entity
		if(subject.isURI()){
			// ContainerTable(S) <- URI(S)
			if(translation.nodeContainerIsTable(subject)){
				return new Assumption.ContainerTable();
			}else{
				// Unknown entity URI
				throw new InconsistentEntityException(subject, "URI does not match table name pattern");
			}
		} else {
			// if subject is a variable or a blank node
			return new Assumption.Subject();
		}
	}

	private Interpretation constrainObject(Node object, Triple triple) throws InconsistentEntityException {
		// SlotValue(O) <- SlotColumn(P)
		if(interpretedAs(triple.getPredicate(), Assumption.SlotColumn.class) ){
			return new Assumption.SlotValue();
		}

		// ContainerRow(O) <- SlotRow(P)
		if(interpretedAs(triple.getPredicate(), Assumption.SlotRow.class) ){
			return new Assumption.ContainerRow();
		}

		// if object is named entity then it can only be a table type
		if(object.isURI()){
			// TypeTable(O) <- URI(O)
			if(object.getURI().equals(Triplifier.FACADE_X_TYPE_ROOT)){
				return new Assumption.FXRoot();
			} else if(translation.nodeTypeIsTable(object)){
				return new Assumption.TypeTable();
			} else {
				// Unknown entity URI
				throw new InconsistentEntityException(object);
			}
		} else if(object.isBlank() || object.isVariable()){
			return new Assumption.Object();
		} else if(object.isLiteral()){
			// SlotValue(O) <- TL(O)
			return new Assumption.SlotValue();
		}
		throw new InconsistentEntityException(object, "Object cannot be of this type");
	}

	private Interpretation constrainPredicate(Node predicate, Triple triple) throws InconsistentAssumptionException {
		// SlotColumn(P) <- SlotValue(O)
		if(interpretedAs(triple.getObject(), Assumption.SlotValue.class) ){
			return new Assumption.SlotColumn();
		}
		// TypeProperty(P) <- TypeTable(O)
		if(interpretedAs(triple.getObject(), Assumption.TypeTable.class) ||
			interpretedAs(triple.getObject(), Assumption.FXRoot.class)){
			return new Assumption.TypeProperty();
		}
		// SlotRow(P) <- ContainerRow(O)
		if(interpretedAs(triple.getObject(), Assumption.ContainerRow.class) ){
			return new Assumption.SlotRow();
		}

		if(!predicate.isVariable()){
			if(translation.nodeSlotIsRowNum(predicate)){
				// SlotRow(P) <- CMP(P)
				return new Assumption.SlotRow();
			}else if(translation.nodeSlotIsColumn(predicate)){
				// SlotColumn(P) <- URI(fx:*)
				return new Assumption.SlotColumn();
			}else if(translation.nodeSlotIsTypeProperty(predicate)){
				// TypeProperty(P) <- URI(rdf:type)
				return new Assumption.TypeProperty();
			}else{
				// Unknown entity URI
				throw new InconsistentEntityException(predicate);
			}
		}
		return new Assumption.Predicate();
	}

	private boolean updateConstraint(Node node, Interpretation constraint) throws InconsistentAssumptionException {
		Interpretation previous = null;
		// Check if node was observed before
		if(constraints.containsKey(node)){
			previous = constraints.get(node);
			Class<? extends Interpretation> wasType = previous.getClass();
			Class<? extends Interpretation> isType = constraint.getClass();
			// If previous Interpretation exist, check consistency:
			// If new interpretation is inconsistent with old one, throw an exception
			if(previous.inconsistentTypes().contains(isType) ||
				constraint.inconsistentTypes().contains(wasType)){
				throw new InconsistentTypesException(wasType, isType);
			}
			// Types are consistent, now keep the most specific
			if(isType.equals(wasType)){
				// 1) they are the same -> OK, no changes
				return false;
			} else {
				// 2) either or specialises the other (e.g. Subject > Container > ContainerTable), keep the more specialised and remove the more general
				if(previous.specialisationOfTypes().contains(isType)){
					// keep old type
					return false;
				}else if(constraint.specialisationOfTypes().contains(wasType)){
					// keep new type
					constraints.put(node, constraint);
					return true;
				}else{
					// Ops, we haven't specified things properly!
					throw new InconsistentTypesException(wasType, isType);
				}
			}
		} else {
			constraints.put(node, constraint);
			return true;
		}
	}
}
