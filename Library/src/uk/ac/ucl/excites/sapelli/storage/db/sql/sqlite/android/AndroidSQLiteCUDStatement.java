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

package uk.ac.ucl.excites.sapelli.storage.db.sql.sqlite.android;

import java.util.List;

import uk.ac.ucl.excites.sapelli.shared.db.DBException;
import uk.ac.ucl.excites.sapelli.storage.db.sql.sqlite.SQLiteRecordStore.SQLiteColumn;
import uk.ac.ucl.excites.sapelli.storage.db.sql.sqlite.statements.ISQLiteCUDStatement;
import uk.ac.ucl.excites.sapelli.storage.model.Record;
import android.annotation.TargetApi;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.Build;
import android.util.Log;

/**
 * @author mstevens
 *
 * @see android.database.sqlite.SQLiteDatabase
 * @see android.database.sqlite.SQLiteStatement
 */
public class AndroidSQLiteCUDStatement implements ISQLiteCUDStatement
{
	
	public enum Kind
	{
		INSERT,
		UPDATE,
		DELETE,
		// others later?
	}
	
	private final SQLiteStatement androidSQLiteSt;
	private Kind kind;
	private List<SQLiteColumn<?, ?>> paramCols;
	
	/**
	 * @param sql
	 * @throws SQLException
	 */
	public AndroidSQLiteCUDStatement(SQLiteDatabase db, String sql, List<SQLiteColumn<?, ?>> paramCols) throws SQLException
	{
		androidSQLiteSt = db.compileStatement(sql);
		Log.d("SQLite", "Compile statement: " + sql); // TODO remove debug logging
		for(Kind k : Kind.values())
			if(k.name().equalsIgnoreCase(sql.substring(0, k.name().length())))
			{
				this.kind = k;
				break;
			}
		if(kind == null)
			throw new IllegalArgumentException("Unsupported kind of SQL statement: " + sql);
		this.paramCols = paramCols;
	}
	
	@Override
	public void retrieveAndBindAll(Record record) throws DBException
	{
		int p = 0;
		for(SQLiteColumn<?, ?> sqliteCol : paramCols)
			sqliteCol.retrieveAndBind(this, p++, record);
	}
	
	@Override
	public void bindBlob(int paramIdx, byte[] value)
	{
		androidSQLiteSt.bindBlob(paramIdx + 1, value); // SQLiteProgram uses 1-based parameter indexes 
	}

	@Override
	public void bindLong(int paramIdx, Long value)
	{
		androidSQLiteSt.bindLong(paramIdx + 1, value); // SQLiteProgram uses 1-based parameter indexes
	}

	@Override
	public void bindDouble(int paramIdx, Double value)
	{
		androidSQLiteSt.bindDouble(paramIdx + 1, value); // SQLiteProgram uses 1-based parameter indexes
	}

	@Override
	public void bindString(int paramIdx, String value)
	{
		androidSQLiteSt.bindString(paramIdx + 1, value); // SQLiteProgram uses 1-based parameter indexes
	}

	@Override
	public void bindNull(int paramIdx)
	{
		androidSQLiteSt.bindNull(paramIdx + 1); // SQLiteProgram uses 1-based parameter indexes
	}

	@Override
	public void clearAllBindings()
	{
		androidSQLiteSt.clearBindings();
	}
	
	@Override
	public void executeCUD() throws DBException
	{
		try
		{
			switch(kind)
			{
				case INSERT :
					if(androidSQLiteSt.executeInsert() == -1)
						throw new DBException("Execution of " + Kind.INSERT + " statement failed");
					break;
				case UPDATE : 
				case DELETE :
					if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) /* API level 11 */
					{
						executeUpdateDeleteNew();
						break;
					}
				default :
					androidSQLiteSt.execute();
			}
		}
		catch(SQLException sqlE)
		{
			throw new DBException("Failed to execute " + kind.name() + " statement", sqlE);
		}
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void executeUpdateDeleteNew()
	{
		androidSQLiteSt.executeUpdateDelete();
	}
	
}