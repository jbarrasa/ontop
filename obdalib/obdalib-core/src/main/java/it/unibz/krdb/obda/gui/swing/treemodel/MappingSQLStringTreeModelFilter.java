package it.unibz.krdb.obda.gui.swing.treemodel;

import it.unibz.krdb.obda.model.OBDAMappingAxiom;
import it.unibz.krdb.obda.model.SQLQuery;

/*
 * @author
 * This filter receives a string in the constructor and returns true if any mapping contains the string in the body.
 *
 */
public class MappingSQLStringTreeModelFilter extends TreeModelFilter<OBDAMappingAxiom> {

  public MappingSQLStringTreeModelFilter() {
    super.bNegation = false;
  }

	@Override
	public boolean match(OBDAMappingAxiom object) {

    final SQLQuery bodyquery = (SQLQuery) object.getSourceQuery();

    boolean isMatch = false;

	  String[] vecKeyword = strFilter.split(" ");
    for (String keyword : vecKeyword) {
      isMatch = match(keyword, bodyquery.toString());
      if(isMatch) {
        break;  // end loop if a match is found!
      }
    }
    // no match found!
		return (bNegation ? !isMatch : isMatch);
	}

  /** A helper method to check a match */
	public static boolean match(String keyword, String query) {

	  if (query.indexOf(keyword) != -1) {  // match found!
      return true;
    }
	  return false;
	}
}
