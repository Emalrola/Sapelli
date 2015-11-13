/**
 * Sapelli data collection platform: http://sapelli.org
 * 
 * Copyright 2012-2014 University College London - ExCiteS group
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
 */

package uk.ac.ucl.excites.sapelli.storage.db.db4o;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import uk.ac.ucl.excites.sapelli.shared.db.StoreBackupper;
import uk.ac.ucl.excites.sapelli.shared.db.db4o.DB4OConnector;
import uk.ac.ucl.excites.sapelli.shared.db.exceptions.DBException;
import uk.ac.ucl.excites.sapelli.shared.util.TimeUtils;
import uk.ac.ucl.excites.sapelli.storage.StorageClient;
import uk.ac.ucl.excites.sapelli.storage.db.RecordStore;
import uk.ac.ucl.excites.sapelli.storage.model.Column;
import uk.ac.ucl.excites.sapelli.storage.model.Record;
import uk.ac.ucl.excites.sapelli.storage.model.RecordReference;
import uk.ac.ucl.excites.sapelli.storage.model.Schema;
import uk.ac.ucl.excites.sapelli.storage.model.columns.IntegerColumn;
import uk.ac.ucl.excites.sapelli.storage.model.indexes.AutoIncrementingPrimaryKey;
import uk.ac.ucl.excites.sapelli.storage.queries.RecordsQuery;
import uk.ac.ucl.excites.sapelli.storage.queries.SingleRecordQuery;
import uk.ac.ucl.excites.sapelli.storage.queries.constraints.Constraint;
import uk.ac.ucl.excites.sapelli.storage.queries.sources.Source;
import uk.ac.ucl.excites.sapelli.storage.queries.sources.SourceBySet;

import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.query.Predicate;

/**
 * DB4O implementation of {@link RecordStore}.
 * 
 * DB4O storage has many known and unknown stability, performance and functionality issues.
 * We have painstakingly tried to work around the most concerning of those, but nevertheless
 * it is essential that we move away from DB4O sooner rather than later.
 * 
 * @author mstevens
 */
public class DB4ORecordStore extends RecordStore
{

	// Statics----------------------------------------------
	static public final int ACTIVATION_DEPTH = 50;
	static public final int UPDATE_DEPTH = 50;

	// Dynamics---------------------------------------------
	private ObjectContainer db4o;
	private String filename;
	
	private AutoIncrementDictionary autoIncrementDict;
	
	public DB4ORecordStore(StorageClient client, File folder, String baseFilename) throws Exception
	{
		super(client, false); // don't make use of roll-back tasks
		this.filename = baseFilename + DATABASE_NAME_SUFFIX;
		this.db4o = DB4OConnector.open(DB4OConnector.getFile(folder, filename), Record.class, Schema.class);
		
		// Get or set the AutoIncrementDictionary:
		ObjectSet<AutoIncrementDictionary> resultSet = db4o.query(AutoIncrementDictionary.class);
		if(!resultSet.isEmpty())
			this.autoIncrementDict = resultSet.get(0);
		else
			this.autoIncrementDict = new AutoIncrementDictionary();
	}
	
	/**
	 * With DB4O there is always 1 (and only 1) implicit transaction. Explicitly opened additional transactions are only simulated.
	 * 
	 * @see uk.ac.ucl.excites.sapelli.storage.db.RecordStore#doStartTransaction()
	 */
	@Override
	protected void doStartTransaction()
	{
		// does nothing
	}

	@Override
	protected void doCommitTransaction() throws DBException
	{
		try
		{
			db4o.commit();
		}
		catch(Exception e)
		{
			throw new DBException("Could not commit changes to DB4O file", e);
		}
	}

	@Override
	protected void doRollbackTransaction() throws DBException
	{
		try
		{
			db4o.rollback();
		}
		catch(Exception e)
		{
			throw new DBException("Could not roll back changes to DB4O file", e);
		}
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.db.RecordStore#doStore(uk.ac.ucl.excites.sapelli.storage.model.Record, int)
	 */
	@Override
	protected int doStore(Record record, int action) throws DBException, IllegalStateException
	{
		// Get auto-incrementing ID column (if there is one):
		IntegerColumn autoIncrIDColumn = record.getSchema().getAutoIncrementingPrimaryKeyColumn();
		
		try
		{
			// Get ExtObjectContainer:
			ExtObjectContainer extDB4O = db4o.ext();
			
			// Variable to hold the Record instance which will be passed to db4o.store() below (provided that INSERT or UPDATE are allowed)
			Record toBeStored = record; // will remain unchanged in INSERT-case and UPDATE_case-1, but not in UPDATE-case-2
			
			// Try to find a previously stored version of the record:
			Record previouslyStored = null;
			// 	Check if the exact same (i.e. pointer-equal) record object is already in DB4O...
			if(extDB4O.isStored(record))
			{	// Yes it is (but not necessarily with the same values), get the stored version:
				previouslyStored = extDB4O.peekPersisted(record, ACTIVATION_DEPTH, isInTransaction());
				// Note: object returned by peekPersisted() is *not* "known" to the database (extDB4O.isStored(previouslyStored) would return false)
				// We call this UPDATE-case-1
			}
			// No it isn't, but perhaps there is a previously stored record with the same primary key value(s):
			else if(autoIncrIDColumn == null || autoIncrIDColumn.isValuePresent(record))
			{
				previouslyStored = retrieveRecord(record.getRecordQuery()); // will throw IllegalStateException if (part of) the non-autoIncrementing PK has not been set
				if(previouslyStored != null)
				{	// We call this UPDATE-case-2
					// Note: the retrieved record *is* "known" to the database (extDB4O.isStored(previouslyStored) would return true) ...
					toBeStored = previouslyStored; // ... hence this is the one we must pass to db4o.store() below (if UPDATEs are allowed)
				}
				//else: there was no matching record -> this is an INSERT-case
			}
			// Deal with autoIncrementing PK...
			else
			{	// This is an INSERT-case, and there is an autoIncrementing PK which has not been set, so we must set the key value:
				// Set auto-incrementing id:
				autoIncrIDColumn.storeValue(record, autoIncrementDict.getNextID(record.getSchema()));
				// Store the dictionary:
				db4o.store(autoIncrementDict);
			}
			
			// INSERT or UPDATE?
			boolean insert = previouslyStored == null;
			
			// Check if INSERT or UPDATE are need at all...
			if(	!insert && // this is an UPDATE-case-x ...
				record.hasEqualValues(previouslyStored, Collections.singleton(Schema.COLUMN_LAST_STORED_AT))) // (ignore LSA!)
			{	// ... but in fact there are no changes ...
				return RESULT_NEEDED_NO_ACTION; // ... so neither an INSERT nor an UPDATE must happen.
			}
			
			// An actual INSERT or UPDATE is needed, but is that allowed?
			if(insert ? action == ACTION_INSERT_OR_UPDATE || action == ACTION_INSERT_ONLY :
						action != ACTION_INSERT_ONLY)
			{	// Yes, we are allowed to either INSERT or UPDATE... 
				
				// Determine LSA:
				if(action != ACTION_UPDATE_ONLY_EXCEPT_LSA && record.getSchema().hasFlags(StorageClient.SCHEMA_FLAG_TRACK_CHANGES))
				{
					Long lastStoredAt = record.getLastStoredAt();
					if(lastStoredAt == null || (!insert && previouslyStored.getLastStoredAt().longValue() == record.getLastStoredAt().longValue()))
						// First insert or "local" update:
						lastStoredAt = Now();
					else if(!insert && previouslyStored.getLastStoredAt().longValue() < record.getLastStoredAt().longValue())
						// This typically means the record being stored is an updated version received from a remote source
						lastStoredAt = record.getLastStoredAt();
					else// if(!insert && previouslyStored.getLastStoredAt().longValue() > record.getLastStoredAt().longValue())
						throw new DBException("Cannot replace record with out-dated version"); // (this assumes all clocks are correct of course...)
					// Set/update LSA on the given Record instance (if record != toBeStored it will be copied below):
					record.setLastStoredAt(RecordFriendship, lastStoredAt);
				}
				
				// Update values of toBeStored when necessary...
				if(toBeStored != record) // i.e. in UPDATE-case-2
				{	// Update values of toBeStored (which is = previouslyStored) to match those of the given record ...
					for(Column<?> col : record.getSchema().getColumns(false))
						if(col != Schema.COLUMN_LAST_STORED_AT || action != ACTION_UPDATE_ONLY_EXCEPT_LSA)
							col.storeObject(toBeStored, col.retrieveValue(record));
					// Note:
					//	We don't copy LSA form record for ACTION_UPDATE_ONLY_EXCEPT_LSA, because even
					//	though it won't have been updated above it could still have been changed elsewhere.  
				}
				
				// Perform the actual INSERT or UPDATE:
				db4o.store(toBeStored);
			}
			
			// Report back:
			return insert ? RESULT_NEEDED_INSERT : RESULT_NEEDED_UPDATE;
		}
		catch(Exception e)
		{
			throw new DBException("DB4O exception", e, record);
		}
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.db.RecordStore#retrieveAllRecords()
	 */
	@Override
	public List<Record> retrieveAllRecords()
	{
		ObjectSet<Record> resultSet = db4o.query(Record.class);
		
		// Check for empty result:
		if(!resultSet.hasNext())
			return Collections.<Record> emptyList();
		
		// Activate result records & add to new ArrayList (list returned by DB4O doesn't allow sorting and possibly other things):
		List<Record> result = new ArrayList<Record>();
		for(Record r : resultSet)
		{
			db4o.activate(r, ACTIVATION_DEPTH);
			result.add(r);
		}
		return result;
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.db.RecordStore#retrieveRecords(uk.ac.ucl.excites.sapelli.storage.queries.RecordsQuery)
	 */
	@Override
	public List<Record> retrieveRecords(final RecordsQuery query)
	{
		final Source source = query.getSource();
		
		// Query for records:
		ObjectSet<Record> resultSet = db4o.query(new Predicate<Record>()
		{
			private static final long serialVersionUID = 1L;

			public boolean match(Record record)
			{
				return	// Schema check, but without full comparison, because that is expensive AND requires the record(/schema) object to be activated to a deeper level than it is at this stage:
						source instanceof SourceBySet ?
							((SourceBySet) source).isValid(record, false) :
							source.isValid(record);
			}
		});
		
		// Check for empty result:
		if(!resultSet.hasNext())
			return Collections.<Record> emptyList();
		
		// Activate result records, filter by query constraints & add to new ArrayList (list returned by DB4O doesn't allow sorting and possibly other things):
		List<Record> result = new ArrayList<Record>();
		Constraint constraints = query.getConstraints();
		while(resultSet.hasNext())
		{
			Record r = resultSet.next();
			db4o.activate(r, ACTIVATION_DEPTH);
			// Filter again: by schema (this time using full comparison), and by contraint(s) (which doesn't work inside the Predicate's match() method, probably due to insufficiently deep activation)  
			if(source.isValid(r) && (constraints == null || constraints.isValid(r))) 
				result.add(r);
		}
		
		// Sort result:
		query.getOrder().sort(result);
		
		// Apply limit if necessary & return result:
		int limit = query.getLimit();
		if(limit != RecordsQuery.NO_LIMIT && result.size() > limit)
			return result.subList(0, limit);
		else
			return result;
	}
	
	@Override
	public List<RecordReference> retrieveRecordReferences(RecordsQuery query)
	{
		List<Record> records = retrieveRecords(query);
		if(records == null)
			return null;
		List<RecordReference> result = new ArrayList<RecordReference>(records.size());
		for(Record record : records)
			result.add(record.getReference());
		return result;
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.db.RecordStore#retrieveRecord(uk.ac.ucl.excites.sapelli.storage.queries.SingleRecordQuery)
	 */
	@Override
	public Record retrieveRecord(SingleRecordQuery query)
	{
		// Run the RecordsQuery:
		List<Record> records = retrieveRecords(query.getRecordsQuery());
		
		// Run execute the SingleRecordQuery (reducing the list to 1 record), without re-running the recordsQuery, and then return the result:
		return query.execute(records, false);
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.db.RecordStore#retrieveAllDeletableRecords()
	 */
	protected List<Record> retrieveAllDeletableRecords()
	{
		return db4o.query(Record.class); // also includes records of internal schemata
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.db.RecordStore#doDelete(uk.ac.ucl.excites.sapelli.storage.model.Record)
	 */
	@Override
	public boolean doDelete(Record record) throws DBException
	{
		try
		{
			if(db4o.ext().isStored(record))
			{
				db4o.delete(record);
				return true;
			}
			else
				return false;
		}
		catch(Exception e)
		{
			throw new DBException("Error upon deleting record", e, record);
		}
	}
	
	@Override
	protected void doClose() throws DBException
	{
		doCommitTransaction(); // because DB4O does not have explicit opening of transactions (it is always using one) we should always commit before closing.
		super.doClose();
	}

	@Override
	protected void closeConnection() throws DBException
	{
		try
		{
			db4o.close();
		}
		catch(Exception e)
		{
			throw new DBException("Exception upon closing DB4O file", e);
		}
	}

	@Override
	protected void doBackup(StoreBackupper backuper, File destinationFolder) throws DBException
	{
		doCommitTransaction(); // because DB4O does not have explicit opening of transactions (it is always using one) we should always commit before backing-up
		try
		{
			File backupDB = backuper.isLabelFilesAsBackup() ?
				DB4OConnector.getFile(destinationFolder, filename + BACKUP_SUFFIX + TimeUtils.getTimestampForFileName()) :
				DB4OConnector.getFile(destinationFolder, filename);
			db4o.ext().backup(backupDB.getAbsolutePath());
		}
		catch(Exception e)
		{
			throw new DBException("Exception upon backing-up the DB4O file", e);
		}
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.db.RecordStore#hasFullIndexSupport()
	 */
	@Override
	public boolean hasFullIndexSupport()
	{
		return false;
	}
	
	/**
	 * Helper class which does the book keeping for auto-incrementing primary keys
	 * 
	 * The first auto-incrementing PK value (i.e. the one assigned to the first record of a given schema that is inserted) will be 0.
	 * Note that some (SQL) RMDBs (such as SQLite) use an initial value of 1 instead.
	 * 
	 * @author mstevens 
	 */
	private class AutoIncrementDictionary extends HashMap<Schema, Long>
	{
		
		private static final long serialVersionUID = 2L;

		public Long getNextID(Schema schema)
		{
			// Check for auto incrementing key:
			if(!(schema.getPrimaryKey() instanceof AutoIncrementingPrimaryKey))
				throw new IllegalArgumentException("Schema must have an auto-incrementing primary key");
			// Next id:
			long next = (containsKey(schema) ? get(schema) : -1l) + 1;
			// Check bounds:
			if(next == Long.MIN_VALUE) // Because: Long.MAX_VALUE + 1l = Long.MIN_VALUE
				throw new IllegalStateException("The \"table\" for records of schema " + schema.getName() + " is full!");
			// Store it:
			put(schema, next); // hash map always keeps the last used id
			// Return it:
			return next;
		}
		
	}

}
