/* $This file is distributed under the terms of the license in /doc/license.txt$ */

package edu.cornell.mannlib.vitro.webapp.searchindex.tasks;

import static edu.cornell.mannlib.vitro.webapp.search.VitroSearchTermNames.ALLTEXT;
import static edu.cornell.mannlib.vitro.webapp.search.VitroSearchTermNames.ALLTEXTUNSTEMMED;
import static edu.cornell.mannlib.vitro.webapp.search.VitroSearchTermNames.CLASSGROUP_URI;
import static edu.cornell.mannlib.vitro.webapp.search.VitroSearchTermNames.DOCID;
import static edu.cornell.mannlib.vitro.webapp.search.VitroSearchTermNames.INDEXEDTIME;
import static edu.cornell.mannlib.vitro.webapp.search.VitroSearchTermNames.MOST_SPECIFIC_TYPE_URIS;
import static edu.cornell.mannlib.vitro.webapp.search.VitroSearchTermNames.NAME_LOWERCASE_SINGLE_VALUED;
import static edu.cornell.mannlib.vitro.webapp.search.VitroSearchTermNames.NAME_RAW;
import static edu.cornell.mannlib.vitro.webapp.search.VitroSearchTermNames.RDFTYPE;
import static edu.cornell.mannlib.vitro.webapp.search.VitroSearchTermNames.URI;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;
import org.jsoup.Jsoup;

import com.hp.hpl.jena.vocabulary.OWL;
import com.hp.hpl.jena.vocabulary.RDFS;

import edu.cornell.mannlib.vitro.webapp.application.ApplicationUtils;
import edu.cornell.mannlib.vitro.webapp.beans.DataPropertyStatement;
import edu.cornell.mannlib.vitro.webapp.beans.Individual;
import edu.cornell.mannlib.vitro.webapp.beans.ObjectPropertyStatement;
import edu.cornell.mannlib.vitro.webapp.beans.VClass;
import edu.cornell.mannlib.vitro.webapp.modules.searchEngine.SearchEngine;
import edu.cornell.mannlib.vitro.webapp.modules.searchEngine.SearchInputDocument;
import edu.cornell.mannlib.vitro.webapp.modules.searchIndexer.SearchIndexerUtils;
import edu.cornell.mannlib.vitro.webapp.searchindex.documentBuilding.DocumentModifier;

public class UpdateDocumentWorkUnit implements Runnable {
	private static final Log log = LogFactory
			.getLog(UpdateDocumentWorkUnit.class);

	private static final String URI_OWL_THING = OWL.Thing.getURI();
	private static final String URI_DIFFERENT_FROM = OWL.differentFrom.getURI();
	private static final String URI_RDFS_LABEL = RDFS.label.getURI();

	private final Individual ind;
	private final List<DocumentModifier> modifiers;
	private final SearchEngine searchEngine;

	public UpdateDocumentWorkUnit(Individual ind,
			Collection<DocumentModifier> modifiers) {
		this.ind = ind;
		this.modifiers = new ArrayList<>(modifiers);
		this.searchEngine = ApplicationUtils.instance().getSearchEngine();
	}
	
	public Individual getInd() {
		return ind;
	}

	@Override
	public void run() {
		try {
			SearchInputDocument doc = searchEngine.createInputDocument();

			addIdAndUri(doc);
			addLabel(doc);
			addClasses(doc);
			addMostSpecificTypes(doc);
			addObjectPropertyText(doc);
			addDataPropertyText(doc);
			addEntityBoost(doc);

			for (DocumentModifier modifier : modifiers) {
				modifier.modifyDocument(ind, doc);
			}

			addIndexedTime(doc);

			searchEngine.add(doc);
		} catch (Exception e) {
			log.warn("Failed to add '" + ind + "' to the search index.", e);
		}
	}

	private void addIdAndUri(SearchInputDocument doc) {
		doc.addField(DOCID, SearchIndexerUtils.getIdForUri(ind.getURI()));
		doc.addField(URI, ind.getURI());
	}

	private void addLabel(SearchInputDocument doc) {
		String name = ind.getRdfsLabel();
		if (name == null) {
			name = ind.getLocalName();
		}

		doc.addField(NAME_RAW, name);
		doc.addField(NAME_LOWERCASE_SINGLE_VALUED, name);
	}

	/**
	 * For each class that the individual belongs to, record the class URI, the
	 * class group URI, the class Name, and the class boost.
	 */
	private void addClasses(SearchInputDocument doc) {
		List<VClass> vclasses = ind.getVClasses(false);
		if (vclasses == null) {
			return;
		}

		for (VClass clz : vclasses) {
			String classUri = clz.getURI();
			if (classUri == null || URI_OWL_THING.equals(classUri)) {
				continue;
			}
			doc.addField(RDFTYPE, classUri);

			String classGroupUri = clz.getGroupURI();
			if (classGroupUri != null) {
				doc.addField(CLASSGROUP_URI, classGroupUri);
			}

			addToAlltext(doc, clz.getName());

			Float boost = clz.getSearchBoost();
			if (boost != null) {
				doc.setDocumentBoost(doc.getDocumentBoost() + boost);
			}
		}
	}

	private void addMostSpecificTypes(SearchInputDocument doc) {
		List<String> mstURIs = ind.getMostSpecificTypeURIs();
		if (mstURIs != null) {
			for (String typeURI : mstURIs) {
				if (StringUtils.isNotBlank(typeURI)) {
					doc.addField(MOST_SPECIFIC_TYPE_URIS, typeURI);
				}
			}
		}
	}

	private void addObjectPropertyText(SearchInputDocument doc) {
		List<ObjectPropertyStatement> stmts = ind.getObjectPropertyStatements();
		if (stmts == null) {
			return;
		}

		for (ObjectPropertyStatement stmt : stmts) {
			if (URI_DIFFERENT_FROM.equals(stmt.getPropertyURI())) {
				continue;
			}
			addToAlltext(doc, stmt.getObject().getRdfsLabel());
		}
	}

	private void addDataPropertyText(SearchInputDocument doc) {
		List<DataPropertyStatement> stmts = ind.getDataPropertyStatements();
		if (stmts == null) {
			return;
		}

		for (DataPropertyStatement stmt : stmts) {
			if (stmt.getDatapropURI().equals(URI_RDFS_LABEL)) {
				continue;
			}
			addToAlltext(doc, stmt.getData());
		}
	}

	private void addEntityBoost(SearchInputDocument doc) {
        Float boost = ind.getSearchBoost();
		if(boost != null && ! boost.equals(0.0F)) {
			doc.setDocumentBoost(boost);                    
        }    
	}

	private void addIndexedTime(SearchInputDocument doc) {
		doc.addField(INDEXEDTIME, (Object) new DateTime().getMillis());
	}

	private void addToAlltext(SearchInputDocument doc, String raw) {
		if (StringUtils.isBlank(raw)) {
			return;
		}
		String clean = Jsoup.parse(raw).text();
		if (StringUtils.isBlank(clean)) {
			return;
		}
		doc.addField(ALLTEXT, clean);
		doc.addField(ALLTEXTUNSTEMMED, clean);

	}
}