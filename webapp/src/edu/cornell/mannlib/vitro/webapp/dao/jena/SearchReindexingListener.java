/* $This file is distributed under the terms of the license in /doc/license.txt$ */

package edu.cornell.mannlib.vitro.webapp.dao.jena;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelChangedListener;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.shared.Lock;

import edu.cornell.mannlib.vitro.webapp.dao.jena.event.EditEvent;
import edu.cornell.mannlib.vitro.webapp.search.indexing.IndexBuilder;

/**
 * This class is thread safe.
 */
public class SearchReindexingListener implements ModelChangedListener {					
	private HashSet<String> changedUris;	
	private IndexBuilder indexBuilder;
	
	public SearchReindexingListener(IndexBuilder indexBuilder) {
		if(indexBuilder == null )
			throw new IllegalArgumentException("Constructor parameter indexBuilder must not be null");		
		this.indexBuilder = indexBuilder;
		this.changedUris = new HashSet<String>();		
	}	

	private synchronized void addChange(Statement stmt){
		if( stmt == null ) return;
		if( stmt.getSubject().isURIResource() ){			
			//changedUris.add( stmt.getSubject().getURI());
			indexBuilder.addToChangedUris(stmt.getSubject().getURI());
			log.debug(stmt.getSubject().getURI());
		}
				
		if( stmt.getObject().isURIResource() ){
			//changedUris.add( ((Resource) stmt.getObject().as(Resource.class)).getURI() );
			indexBuilder.addToChangedUris(((Resource) stmt.getObject()).getURI());			
			log.debug(((Resource) stmt.getObject().as(Resource.class)).getURI());
		}	
	}
	
//	private synchronized Set<String> getAndClearChangedUris(){
//		log.debug("Getting and clearing changed URIs.");		
//		Set<String> out = changedUris;
//		changedUris = new HashSet<String>();
//		return out;
//	}

	private void doAyncIndex(){
//		for( String uri: getAndClearChangedUris()){
//			indexBuilder.addToChangedUris(uri);
//		}				
		new Thread(indexBuilder).start();		
	}
	
	
	@Override
	public void notifyEvent(Model arg0, Object arg1) {
		if ( (arg1 instanceof EditEvent) ){
			EditEvent editEvent = (EditEvent)arg1;
			if( !editEvent.getBegin() ){// editEvent is the end of an edit				
				log.debug("Doing search index build at end of EditEvent");				
				doAyncIndex();
			}		
		} else{
			log.debug("ignoring event " + arg1.getClass().getName() + " "+ arg1 );
		}
	}
	
	@Override
	public void addedStatement(Statement stmt) {
		addChange(stmt);
		//doAyncIndex();
	}

	@Override
	public void removedStatement(Statement stmt){
		addChange(stmt);
		//doAyncIndex();
	}
	
	private static final Log log = LogFactory.getLog(SearchReindexingListener.class.getName());

	@Override
	public void addedStatements(Statement[] arg0) {
		for( Statement s: arg0){
			addChange(s);
		}
		//doAyncIndex();
	}

	@Override
	public void addedStatements(List<Statement> arg0) {
		for( Statement s: arg0){
			addChange(s);
		}
		//doAyncIndex();
	}

	@Override
	public void addedStatements(StmtIterator arg0) {
		try{
			while(arg0.hasNext()){
				Statement s = arg0.nextStatement();
				addChange(s);
			}
		}finally{
			arg0.close();
		}
		//doAyncIndex();		
	}

	@Override
	public void addedStatements(Model m) {
		m.enterCriticalSection(Lock.READ);
		StmtIterator it = null;
		try{
			it = m.listStatements();
			while(it.hasNext()){
				addChange(it.nextStatement());
			}			
		}finally{
			if( it != null ) it.close();
			m.leaveCriticalSection();
		}
		//doAyncIndex();
	}

	@Override
	public void removedStatements(Statement[] arg0) {
		//same as add stmts
		this.addedStatements(arg0);		
	}

	@Override
	public void removedStatements(List<Statement> arg0) {
		//same as add
		this.addedStatements(arg0);		
	}

	@Override
	public void removedStatements(StmtIterator arg0) {
		//same as add
		this.addedStatements(arg0);
	}

	@Override
	public void removedStatements(Model arg0) {
		//same as add
		this.addedStatements(arg0);
	}
}
