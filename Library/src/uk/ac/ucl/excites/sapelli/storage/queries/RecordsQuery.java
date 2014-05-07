package uk.ac.ucl.excites.sapelli.storage.queries;

import java.util.Collections;
import java.util.List;

import uk.ac.ucl.excites.sapelli.storage.model.ComparatorColumn;
import uk.ac.ucl.excites.sapelli.storage.model.Record;
import uk.ac.ucl.excites.sapelli.storage.model.Schema;
import uk.ac.ucl.excites.sapelli.storage.queries.constraints.AndConstraint;
import uk.ac.ucl.excites.sapelli.storage.queries.constraints.Constraint;
import uk.ac.ucl.excites.sapelli.storage.queries.constraints.ConstraintVisitor;
import uk.ac.ucl.excites.sapelli.storage.util.ColumnPointer;

public class RecordsQuery
{

	// STATICS-------------------------------------------------------
	static public final int NO_LIMIT = -1;
	static public final boolean DEFAULT_ORDER = true; // ASCending
	
	// DYNAMICS------------------------------------------------------
	private final List<Schema> sourceSchemata;
	private final AndConstraint constraints;
	private final ColumnPointer orderBy;
	private final boolean orderAsc;
	private final Integer limit;
	
	public RecordsQuery()
	{
		this(null, (ColumnPointer) null, DEFAULT_ORDER, NO_LIMIT, (Constraint[]) null);
	}
	
	public RecordsQuery(Schema sourceSchema)
	{
		this(Collections.<Schema> singletonList(sourceSchema));
	}
	
	public RecordsQuery(List<Schema> sourceSchemata)
	{
		this(sourceSchemata, (ColumnPointer) null, DEFAULT_ORDER, NO_LIMIT, (Constraint[]) null);
	}
	
	public RecordsQuery(ComparatorColumn<?> orderBy, boolean orderAsc)
	{
		this((List<Schema>) null, orderBy, orderAsc, NO_LIMIT, (Constraint[]) null);
	}
	
	public RecordsQuery(ColumnPointer orderBy, boolean orderAsc)
	{
		this(null, orderBy, orderAsc, NO_LIMIT, (Constraint[]) null);
	}
	
	public RecordsQuery(int limit)
	{
		this(null, (ColumnPointer) null, DEFAULT_ORDER, limit, (Constraint[]) null);
	}
	
	public RecordsQuery(Constraint... constraints)
	{
		this(null, (ColumnPointer) null, DEFAULT_ORDER, NO_LIMIT, constraints);
	}
	
	public RecordsQuery(Schema sourceSchema, Constraint... constraints)
	{	
		this(Collections.<Schema> singletonList(sourceSchema), constraints);
	}
	
	public RecordsQuery(List<Schema> sourceSchemata, Constraint... constraints)
	{
		this(sourceSchemata, (ColumnPointer) null, DEFAULT_ORDER, NO_LIMIT, constraints);
	}
	
	public RecordsQuery(Schema sourceSchema, ComparatorColumn<?> orderBy, boolean orderAsc)
	{
		this(sourceSchema, orderBy, orderAsc, NO_LIMIT, (Constraint[]) null);
	}
	
	public RecordsQuery(Schema sourceSchema, ColumnPointer orderBy, boolean orderAsc)
	{
		this(Collections.<Schema> singletonList(sourceSchema), orderBy, orderAsc);
	}
	
	public RecordsQuery(List<Schema> sourceSchemata, ColumnPointer orderBy, boolean orderAsc)
	{
		this(sourceSchemata, orderBy, orderAsc, NO_LIMIT, (Constraint[]) null);
	}
	
	public RecordsQuery(Schema sourceSchema, ComparatorColumn<?> orderBy, boolean orderAsc, Constraint... constraints)
	{
		this(sourceSchema, orderBy, orderAsc, NO_LIMIT, constraints);
	}
	
	public RecordsQuery(Schema sourceSchema, ColumnPointer orderBy, boolean orderAsc, Constraint... constraints)
	{
		this(Collections.<Schema> singletonList(sourceSchema), orderBy, orderAsc, constraints);
	}
	
	public RecordsQuery(List<Schema> sourceSchemata, ColumnPointer orderBy, boolean orderAsc, Constraint... constraints)
	{
		this(sourceSchemata, orderBy, orderAsc, NO_LIMIT, constraints);
	}
	
	public RecordsQuery(ComparatorColumn<?> orderBy, boolean orderAsc, Constraint... constraints)
	{
		this((List<Schema>) null, orderBy, orderAsc, NO_LIMIT, constraints);
	}
	
	public RecordsQuery(ColumnPointer orderBy, boolean orderAsc, Constraint... constraints)
	{
		this(null, orderBy, orderAsc, NO_LIMIT, constraints);
	}
	
	public RecordsQuery(Schema sourceSchema, int limit)
	{
		this(Collections.<Schema> singletonList(sourceSchema), limit);
	}
	
	public RecordsQuery(List<Schema> sourceSchemata, int limit)
	{
		this(sourceSchemata, (ColumnPointer) null, DEFAULT_ORDER, limit, (Constraint[]) null);
	}
	
	public RecordsQuery(int limit, Constraint... constraints)
	{
		this(null, (ColumnPointer) null, DEFAULT_ORDER, limit, constraints);
	}
	
	public RecordsQuery(Schema sourceSchema, int limit, Constraint... constraints)
	{
		this(Collections.<Schema> singletonList(sourceSchema), limit, constraints);
	}
	
	public RecordsQuery(List<Schema> sourceSchemata, int limit, Constraint... constraints)
	{
		this(sourceSchemata, (ColumnPointer) null, DEFAULT_ORDER, limit, constraints);
	}
	
	public RecordsQuery(ComparatorColumn<?> orderBy, boolean orderAsc, int limit)
	{
		this((List<Schema>) null, orderBy, orderAsc, limit, (Constraint[]) null);
	}
	
	public RecordsQuery(ColumnPointer orderBy, boolean orderAsc, int limit)
	{
		this(null, orderBy, orderAsc, limit, (Constraint[]) null);
	}
	
	public RecordsQuery(Schema sourceSchema, ComparatorColumn<?> orderBy, boolean orderAsc, int limit)
	{
		this(sourceSchema, orderBy, orderAsc, limit, (Constraint[]) null);
	}
	
	public RecordsQuery(Schema sourceSchema, ColumnPointer orderBy, boolean orderAsc, int limit)
	{
		this(Collections.<Schema> singletonList(sourceSchema), orderBy, orderAsc, limit);
	}
	
	public RecordsQuery(List<Schema> sourceSchemata, ColumnPointer orderBy, boolean orderAsc, int limit)
	{
		this(sourceSchemata, orderBy, orderAsc, limit, (Constraint[]) null);
	}
	
	public RecordsQuery(Schema sourceSchema, ComparatorColumn<?> orderBy, boolean orderAsc, int limit, Constraint... constraints)
	{
		this(Collections.<Schema> singletonList(sourceSchema), orderBy, orderAsc, limit, constraints);
	}
	
	public RecordsQuery(List<Schema> sourceSchemata, ComparatorColumn<?> orderBy, boolean orderAsc, int limit, Constraint... constraints)
	{
		this(sourceSchemata, new ColumnPointer(orderBy), orderAsc, limit, constraints);
	}
	
	/**
	 * @param sourceSchemata may be null or empty (to query records of *any* schema)
	 * @param orderBy
	 * @param orderAsc soring order: ASCending (true) or DESCending (false)
	 * @param limit
	 * @param constraints
	 */
	public RecordsQuery(List<Schema> sourceSchemata, ColumnPointer orderBy, boolean orderAsc, int limit, Constraint... constraints)
	{
		this.sourceSchemata = (sourceSchemata != null ? sourceSchemata : Collections.<Schema> emptyList());
		if(constraints != null && constraints.length == 1 && constraints[0] instanceof AndConstraint)
			this.constraints = (AndConstraint) constraints[0]; // flatten AND
		else
			this.constraints = new AndConstraint(constraints); // can deal with the array or one of its elements being null
		this.orderBy = orderBy;
		this.orderAsc = orderAsc;
		if(limit < NO_LIMIT || limit == 0)
			throw new IllegalArgumentException("Limit must be > 0");
		this.limit = limit != NO_LIMIT ? limit : null;
	}
	
	/**
	 * Executes the query in Java runtime memory, using a list of records as source
	 * 
	 * @param sourceRecords
	 */
	public List<Record> execute(List<Record> sourceRecords)
	{
		List<Record> records = sourceRecords;
		
		// Apply constraints:
		records = getInMemoryConstraits().filter(records);
		
		// Sort:
		sort(records);
		
		// Limit:
		if(limit != null && records.size() > limit)
			records = records.subList(0, limit);
		
		return records;
	}
	
	public void sort(List<Record> records)
	{
		if(orderBy != null)
			Collections.sort(records, orderAsc ? orderBy : Collections.reverseOrder(orderBy));
	}
	
	private Constraint getInMemoryConstraits()
	{
		if(sourceSchemata.isEmpty())
			return constraints; //Any schema
		else
		{	// Specific schema: add additional constraint to check for it
			Constraint schemaCheck = new Constraint()
			{
				@Override
				protected boolean _isValid(Record record)
				{
					return sourceSchemata.contains(record.getSchema());
				}
	
				@Override
				public void accept(ConstraintVisitor visitor) { /* ignore */ }
			};
			if(constraints.getSubConstraints().isEmpty())
				return schemaCheck;
			else
				return new AndConstraint(schemaCheck, constraints);
		}
	}

	/**
	 * @return the constraints
	 */
	public Constraint getConstraints()
	{
		return constraints;
	}

	/**
	 * @return the orderBy
	 */
	public ColumnPointer getOrderBy()
	{
		return orderBy;
	}

	/**
	 * @return the orderAsc
	 */
	public boolean isOrderAsc()
	{
		return orderAsc;
	}

	/**
	 * @return the limit
	 */
	public int getLimit()
	{
		if(limit == null)
			return NO_LIMIT;
		return limit;
	}
	
	public boolean isAnySchema()
	{
		return sourceSchemata.isEmpty();
	}
	
	/**
	 * @return the sourceSchema
	 */
	public List<Schema> getSourceSchemata()
	{
		return sourceSchemata;
	}

}
