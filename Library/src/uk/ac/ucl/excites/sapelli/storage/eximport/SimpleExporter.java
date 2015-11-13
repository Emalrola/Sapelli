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

package uk.ac.ucl.excites.sapelli.storage.eximport;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.joda.time.DateTime;

import uk.ac.ucl.excites.sapelli.shared.db.exceptions.DBException;
import uk.ac.ucl.excites.sapelli.shared.io.text.FileWriter;
import uk.ac.ucl.excites.sapelli.storage.db.RecordStore;
import uk.ac.ucl.excites.sapelli.storage.model.Record;
import uk.ac.ucl.excites.sapelli.storage.model.Schema;
import uk.ac.ucl.excites.sapelli.storage.queries.Order;
import uk.ac.ucl.excites.sapelli.storage.queries.RecordsQuery;
import uk.ac.ucl.excites.sapelli.storage.queries.constraints.Constraint;
import uk.ac.ucl.excites.sapelli.storage.queries.constraints.EqualityConstraint;
import uk.ac.ucl.excites.sapelli.storage.queries.constraints.OrConstraint;
import uk.ac.ucl.excites.sapelli.storage.queries.constraints.RuleConstraint;
import uk.ac.ucl.excites.sapelli.storage.queries.constraints.RuleConstraint.Comparison;
import uk.ac.ucl.excites.sapelli.storage.queries.sources.Source;
import uk.ac.ucl.excites.sapelli.storage.types.TimeStamp;
import uk.ac.ucl.excites.sapelli.storage.visitors.SimpleSchemaTraverser;

/**
 * Abstract superclass for Exporters based on a SimpleSchemaTraverser
 * 
 * TODO extract more shared behaviour out of CSVRecordsExporter and XMLRecordsExporter
 * 
 * @author mstevens
 */
public abstract class SimpleExporter extends SimpleSchemaTraverser implements Exporter
{
	
	// STATIC ------------------------------------------------------------
	/**
	 * @param recStore
	 * @param selectionQuery
	 * @param excludePreviouslyExported
	 * @return
	 */
	static public RecordsQuery GetRecordsQuery(Source source, Order order, int limit, Constraint constraints, boolean excludePreviouslyExported)
	{
		return new RecordsQuery(source,
								order,
								limit,
								constraints,
								(excludePreviouslyExported ?
									// Add constraints to exclude previously exported records:
									new OrConstraint(	EqualityConstraint.IsNull(Schema.COLUMN_LAST_EXPORTED_AT), // never exported
														new RuleConstraint(Schema.COLUMN_LAST_EXPORTED_AT, Schema.COLUMN_LAST_STORED_AT, Comparison.SMALLER)) : // previously exported but changed since
									null));
	}
	
	/**
	 * @param recStore
	 * @param records
	 * @param exportTime
	 * @throws DBException
	 */
	static public void MarkAsExported(RecordStore recStore, List<Record> records, final TimeStamp exportTime) throws DBException
	{
		for(Record record : records)
		{
			// Set LEA:
			record.setLastExportedAt(exportTime.getMsSinceEpoch());
			// Update record in RecordStore without affecting LSA:
			recStore.update(record, false);
		}
	}

	// DYNAMIC -----------------------------------------------------------
	protected File exportFolder;
	protected boolean forceExportUnexportable = false;
	
	protected FileWriter writer = null;
	
	/**
	 * @return the forceExportUnexportable
	 */
	public boolean isForceExportUnexportable()
	{
		return forceExportUnexportable;
	}

	/**
	 * @param forceExportUnexportable the forceExportUnexportable to set
	 */
	public void setForceExportUnexportable(boolean forceExportUnexportable)
	{
		this.forceExportUnexportable = forceExportUnexportable;
	}
	
	protected abstract void openWriter(String description, DateTime timestamp) throws IOException;
	
	protected abstract void closeWriter();
	
	@Override
	public boolean skipNonBinarySerialisedLocationSubColumns()
	{
		return false;
	}

	@Override
	public boolean skipNonBinarySerialisedOrientationSubColumns()
	{
		return false;
	}

	@Override
	public boolean includeVirtualColumns()
	{
		return true;
	}

}
