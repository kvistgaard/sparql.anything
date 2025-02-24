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

package io.github.sparqlanything.model.test;

import io.github.sparqlanything.model.IRIArgument;
import io.github.sparqlanything.model.Triplifier;
import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

public class TriplifierTest {

	@Test
	public void instantiateURLTest() {
		Properties p = new Properties();
		p.setProperty(IRIArgument.LOCATION.toString(), "file:///a/b.c");
		Assert.assertEquals("file:///a/b.c#", Triplifier.getRootArgument(p));
	}

}
