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

package uk.ac.ucl.excites.sapelli.collector.remote;

import java.io.File;

import uk.ac.ucl.excites.sapelli.shared.db.Store;
import uk.ac.ucl.excites.sapelli.shared.db.StoreBackupper;
import uk.ac.ucl.excites.sapelli.shared.db.exceptions.DBException;

/**
 * 
 * @author benelliott
 */
public class RemoteStore extends Store
{
	
	// STATICS---------------------------------------------	

	// Receiver Schema
//	static final public Schema RECEIVER_SCHEMA = new Schema(CORRESPONDENT_MANAGEMENT_MODEL, "Receiver");
//	static final public IntegerColumn RECEIVER_COLUMN_ID = new IntegerColumn("ID", false, Receiver.RECEIVER_ID_FIELD);
//	static final public ForeignKeyColumn RECEIVER_COLUMN_PROJECT_ID = new ForeignKeyColumn("ProjectID", ProjectRecordStore.PROJECT_SCHEMA, false);
//	static final public ForeignKeyColumn RECEIVER_COLUMN_CORRESPONDENT_NAME = new ForeignKeyColumn("CorrespondentName", CORRESPONDENT_SCHEMA, false);
//	static final public IntegerColumn RECEIVER_COLUMN_RETRANSMIT_INTERVAL = new IntegerColumn("RetransmitInterval", false, false, Receiver.RETRANSMIT_INTERVAL_SIZE_BITS);
//	static final public BooleanColumn RECEIVER_COLUMN_ENCRYPT = new BooleanColumn("Encrypt", false);
//	//	Add columns to Receiver Schema & seal it:
//	static
//	{
//		RECEIVER_SCHEMA.addColumn(RECEIVER_COLUMN_ID);
//		RECEIVER_SCHEMA.addColumn(RECEIVER_COLUMN_PROJECT_ID);
//		RECEIVER_SCHEMA.addColumn(RECEIVER_COLUMN_CORRESPONDENT_NAME);
//		RECEIVER_SCHEMA.addColumn(RECEIVER_COLUMN_RETRANSMIT_INTERVAL);
//		RECEIVER_SCHEMA.addColumn(RECEIVER_COLUMN_ENCRYPT);
//		RECEIVER_SCHEMA.setPrimaryKey(new AutoIncrementingPrimaryKey("IDIdx", RECEIVER_COLUMN_ID));
//		RECEIVER_SCHEMA.seal();
//	}
//	// Sender Schema
//	static final public Schema SENDER_SCHEMA = new Schema(CORRESPONDENT_MANAGEMENT_MODEL, "Receiver");
//	static final public IntegerColumn SENDER_COLUMN_ID = new IntegerColumn("ID", false, Sender.SENDER_ID_FIELD);
//	static final public ForeignKeyColumn SENDER_COLUMN_PROJECT_ID = new ForeignKeyColumn("ProjectID", ProjectRecordStore.PROJECT_SCHEMA, false);
//	static final public ForeignKeyColumn SENDER_COLUMN_CORRESPONDENT_NAME = new ForeignKeyColumn("CorrespondentName", CORRESPONDENT_SCHEMA, false);
//	static final public BooleanColumn SENDER_COLUMN_ACK = new BooleanColumn("Ack", false);
//	// Add columns to Sender Schema and seal it:
//	static
//	{
//		SENDER_SCHEMA.addColumn(SENDER_COLUMN_ID);
//		SENDER_SCHEMA.addColumn(SENDER_COLUMN_PROJECT_ID);
//		SENDER_SCHEMA.addColumn(SENDER_COLUMN_CORRESPONDENT_NAME);
//		SENDER_SCHEMA.addColumn(SENDER_COLUMN_ACK);
//		SENDER_SCHEMA.setPrimaryKey(new AutoIncrementingPrimaryKey("IDIdx", SENDER_COLUMN_ID));
//		SENDER_SCHEMA.seal();
//		
//		// seal the model too:
//		CORRESPONDENT_MANAGEMENT_MODEL.seal();
//	}
	
	@Override
	protected void doClose() throws DBException
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void backup(StoreBackupper backuper, File destinationFolder) throws DBException
	{
		// TODO Auto-generated method stub

	}

}
