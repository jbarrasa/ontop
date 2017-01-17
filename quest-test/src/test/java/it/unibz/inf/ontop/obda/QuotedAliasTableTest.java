package it.unibz.inf.ontop.obda;

/*
 * #%L
 * ontop-test
 * %%
 * Copyright (C) 2009 - 2014 Free University of Bozen-Bolzano
 * %%
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
 * #L%
 */

import it.unibz.inf.ontop.quest.AbstractVirtualModeTest;


/**
 * Class to test that quotes from table names are removed correctly.
 * We use the npd database.
 * @see TableJSQL
 */
public class QuotedAliasTableTest extends AbstractVirtualModeTest {
	static final String owlfile = "src/test/resources/new/extended-npd-v2-ql_a_postgres.owl";
    static final String obdafile = "src/test/resources/new/npd-v2.obda";

	public QuotedAliasTableTest() {
		super(owlfile, obdafile);
	}


	/**
	 * Test OBDA table
	 * @throws Exception
	 */
	public void test() throws Exception {
		String query = "PREFIX npdv: <http://sws.ifi.uio.no/vocab/npd-v2#> SELECT DISTINCT ?x WHERE {"
				+ "?x a npdv:CompanyReserve . "
				+	" }";
		
		// Now we are ready for querying obda
		// npd query 1
		countResults(query, 52668);
	}
}
