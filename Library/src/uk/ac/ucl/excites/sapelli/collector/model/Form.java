/**
 *
 */
package uk.ac.ucl.excites.sapelli.collector.model;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;

import uk.ac.ucl.excites.sapelli.collector.model.fields.EndField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.LocationField;
import uk.ac.ucl.excites.sapelli.storage.model.Column;
import uk.ac.ucl.excites.sapelli.storage.model.Index;
import uk.ac.ucl.excites.sapelli.storage.model.Record;
import uk.ac.ucl.excites.sapelli.storage.model.Schema;
import uk.ac.ucl.excites.sapelli.storage.model.columns.DateTimeColumn;
import uk.ac.ucl.excites.sapelli.storage.model.columns.IntegerColumn;
import uk.ac.ucl.excites.sapelli.storage.util.IntegerRangeMapping;
import uk.ac.ucl.excites.sapelli.util.CollectionUtils;

/**
 * @author mstevens, Michalis Vitos
 *
 */
public class Form
{

	// Statics--------------------------------------------------------
	/**
	 * Allowed form indexes: 0 to {@link Project#MAX_FORMS} - 1
	 */
	public static final IntegerRangeMapping FORM_INDEX_FIELD = new IntegerRangeMapping(0, Project.MAX_FORMS - 1);
	public static final int FORM_INDEX_SIZE = FORM_INDEX_FIELD.getSize(); //bits
	
	public static final boolean END_TIME_DEFAULT = false;

	// Where to go next:
	static public enum Next
	{
		LOOPFORM,
		LOOPPROJ,
		PREVFORM,
		EXITAPP
	}
	public static final Next DEFAULT_NEXT = Next.LOOPFORM;
	public static final String V1X_NEXT_LOOP = "LOOP";
	public static final String V1X_NEXT_EXIT = "EXIT";
	
	public static final boolean DEFAULT_SINGLE_PAGE = false;
	public static final boolean DEFAULT_VIBRATE = true;
	public static final boolean DEFAULT_SHOW_BACK = true;
	public static final boolean DEFAULT_SHOW_CANCEL = true;
	public static final boolean DEFAULT_SHOW_FORWARD = true;
	public static final String DEFAULT_BUTTON_BACKGROUND_COLOR = "#E8E8E8"; //light gray
	public static final boolean DEFAULT_ANIMATION = true;
	public static final boolean DEFAULT_OBFUSCATE_MEDIA_FILES = false;

	public static final String COLUMN_TIMESTAMP_START = "StartTime";
	public static final String COLUMN_TIMESTAMP_END = "EndTime";
	public static final String COLUMN_DEVICE_ID = "DeviceID";
	

	// Dynamics-------------------------------------------------------
	private final Project project;
	private final int index;
	private boolean producesRecords = true;
	private Schema schema;
	private final String id;

	private transient List<String> warnings;
	
	// Fields
	private Field start;
	private final List<Field> fields;
	private final List<LocationField> locationFields;
	private final List<Trigger> triggers;
	
	// Android shortcut:
	private String shortcutImageRelativePath;

	// Animation:
	private boolean animation = DEFAULT_ANIMATION;
	
	// Obfuscate Media Files:
	private boolean obfuscateMediaFiles = DEFAULT_OBFUSCATE_MEDIA_FILES;

	// Timestamps
	private boolean storeEndTime;

	// End action:
	private Next next = DEFAULT_NEXT;
	private boolean vibrateOnSave = DEFAULT_VIBRATE;
	private String saveSoundRelativePath;

	// Buttons:
	private boolean showBack = DEFAULT_SHOW_BACK;
	private boolean showCancel = DEFAULT_SHOW_CANCEL;
	private boolean showForward = DEFAULT_SHOW_FORWARD;
	private String buttonBackgroundColor = DEFAULT_BUTTON_BACKGROUND_COLOR;
	private String backButtonImageRelativePath;
	private String cancelButtonImageRelativePath;
	private String forwardButtonImageRelativePath;
	
	public Form(Project project, String id)
	{
		this.project = project;
		this.id = id;
		
		this.fields = new ArrayList<Field>();
		this.locationFields = new ArrayList<LocationField>();
		this.triggers = new ArrayList<Trigger>();
		
		// Set Form index & add it to the Project:
		if(FORM_INDEX_FIELD.fits(project.getForms().size()))
			this.index = project.getForms().size();
		else
			throw new IllegalArgumentException("Invalid form index, valid values are " + FORM_INDEX_FIELD.getLogicalRangeString() + " (up to " + Project.MAX_FORMS + " forms per project).");
		project.addForm(this); //!!!
	}

	public void initialiseStorage()
	{
		getSchema(); //this will also trigger all Columns to be created/initialised
	}

	/**
	 * @return the project
	 */
	public Project getProject()
	{
		return project;
	}

	public void addField(Field f)
	{
		fields.add(f);
		if(f instanceof LocationField)
			locationFields.add((LocationField) f);
	}

	public int getFieldIndex(Field field)
	{
		return fields.indexOf(field.getRoot());
	}

	public Field getNextField(Field current)
	{
		int currentIndex = getFieldIndex(current);
		// Exception handling:
		if(currentIndex < 0)
			throw new IllegalArgumentException("The current field is not part of this form.");
		// Check for jump field (possibly the one of a parent in case of ChoiceField):
		Field nextF = current.getJump();
		if(nextF == null)
			// No jump is set, check for field below current one:
			if(currentIndex + 1 < fields.size())
				nextF = fields.get(currentIndex + 1); // go to next field in the form
			else
				nextF = new EndField(this, true, next); // current field is the last of the form, go to the form's "next", but save the record first
		return nextF; // use jump as next
	}

	/**
	 * Returns the Form ID
	 * 
	 * @return
	 */
	public String getID()
	{
		return id;
	}
	
	/**
	 * Returns the Form ID (method kept for backwards compatibility)
	 * 
	 * @return
	 */
	public String getName()
	{
		return getID();
	}

	public List<Field> getFields()
	{
		return fields;
	}
	
	/**
	 * Find a field of the form by its ID 
	 * 
	 * @param fieldID
	 * @return the field with the specified ID, or null if no such field exists in this form
	 */
	public Field getField(String fieldID)
	{
		for(Field f : fields)
			if(f.getID().equalsIgnoreCase(fieldID)) // field IDs are treated as case insensitive
				return f;
		return null;
	}

	/**
	 * @return the start
	 */
	public Field getStartField()
	{
		return start;
	}

	/**
	 * @param start
	 *            the start to set
	 */
	public void setStartField(Field start)
	{
		this.start = start;
	}

	public void addTrigger(Trigger trigger)
	{
		triggers.add(trigger);
	}

	/**
	 * @return the triggers
	 */
	public List<Trigger> getTriggers()
	{
		return triggers;
	}

	/**
	 * @return the animation
	 */
	public boolean isAnimation()
	{
		return animation;
	}

	/**
	 * @param animation the animation to set
	 */
	public void setAnimation(boolean animation)
	{
		this.animation = animation;
	}

	/**
	 * @return the obfuscateMediaFiles
	 */
	public boolean isObfuscateMediaFiles()
	{
		return obfuscateMediaFiles;
	}

	/**
	 * @param obfuscateMediaFiles
	 *            the obfuscateMediaFiles to set
	 */
	public void setObfuscateMediaFiles(boolean obfuscateMediaFiles)
	{
		this.obfuscateMediaFiles = obfuscateMediaFiles;
	}

	/**
	 * @return the shortcutImageRelativePath
	 */
	public String getShortcutImageRelativePath()
	{
		return shortcutImageRelativePath;
	}

	/**
	 * @param shortcutImageRelativePath
	 *            the shortcutImageRelativePath to set
	 */
	public void setShortcutImageRelativePath(String shortcutImageRelativePath)
	{
		this.shortcutImageRelativePath = shortcutImageRelativePath;
	}

	/**
	 * @return the showBack
	 */
	public boolean isShowBack()
	{
		return showBack;
	}

	/**
	 * @param showBack
	 *            the showBack to set
	 */
	public void setShowBack(boolean showBack)
	{
		this.showBack = showBack;
	}

	/**
	 * @return the showCancel
	 */
	public boolean isShowCancel()
	{
		return showCancel;
	}

	/**
	 * @param showCancel
	 *            the showCancel to set
	 */
	public void setShowCancel(boolean showCancel)
	{
		this.showCancel = showCancel;
	}

	/**
	 * @return the showForward
	 */
	public boolean isShowForward()
	{
		return showForward;
	}

	/**
	 * @param showForward
	 *            the showForward to set
	 */
	public void setShowForward(boolean showForward)
	{
		this.showForward = showForward;
	}

	/**
	 * @return the backButtonImageRelativePath
	 */
	public String getBackButtonImageRelativePath()
	{
		return backButtonImageRelativePath;
	}

	/**
	 * @param backButtonImageRelativePath the backButtonImageRelativePath to set
	 */
	public void setBackButtonImageRelativePath(String backButtonImageRelativePath)
	{
		this.backButtonImageRelativePath = backButtonImageRelativePath;
	}

	/**
	 * @return the cancelButtonImageRelativePath
	 */
	public String getCancelButtonImageRelativePath()
	{
		return cancelButtonImageRelativePath;
	}

	/**
	 * @param cancelButtonImageRelativePath the cancelButtonImageRelativePath to set
	 */
	public void setCancelButtonImageRelativePath(String cancelButtonImageRelativePath)
	{
		this.cancelButtonImageRelativePath = cancelButtonImageRelativePath;
	}

	/**
	 * @return the forwardButtonImageRelativePath
	 */
	public String getForwardButtonImageRelativePath()
	{
		return forwardButtonImageRelativePath;
	}

	/**
	 * @param forwardButtonImageRelativePath the forwardButtonImageRelativePath to set
	 */
	public void setForwardButtonImageRelativePath(String forwardButtonImageRelativePath)
	{
		this.forwardButtonImageRelativePath = forwardButtonImageRelativePath;
	}

	/**
	 * @return the buttonBackgroundColor
	 */
	public String getButtonBackgroundColor()
	{
		return buttonBackgroundColor;
	}

	/**
	 * @param buttonBackgroundColor the buttonBackgroundColor to set
	 */
	public void setButtonBackgroundColor(String buttonBackgroundColor)
	{
		this.buttonBackgroundColor = buttonBackgroundColor;
	}

	public List<LocationField> getLocationFields()
	{
		return locationFields;
	}

	public List<LocationField> getLocationFields(boolean onlyStartWithForm)
	{
		if(onlyStartWithForm)
		{
			List<LocationField> startLF = new ArrayList<LocationField>();
			for(LocationField lf : getLocationFields())
				if(lf.isStartWithForm())
					startLF.add(lf);
			return startLF;
		}
		return getLocationFields();
	}

	/**
	 * @return the storeEndTime
	 */
	public boolean isStoreEndTime()
	{
		return storeEndTime;
	}

	/**
	 * @param storeEndTime
	 *            the storeEndTime to set
	 */
	public void setStoreEndTime(boolean storeEndTime)
	{
		this.storeEndTime = storeEndTime;
	}

	/**
	 * @return the next
	 */
	public Next getNext()
	{
		return next;
	}

	/**
	 * @param next the next to set
	 * @throws IllegalArgumentException	when the nextStr is not recognised
	 */
	public void setNext(String nextStr) throws IllegalArgumentException
	{
		if(nextStr == null)
			return; //default next will be used
		if(nextStr.startsWith("_"))
			nextStr = nextStr.substring(1); //Strip off leading '_'
		nextStr = nextStr.toUpperCase(); //Make upper case
		try
		{
			this.next = Next.valueOf(nextStr);
		}
		catch(IllegalArgumentException iae)
		{
			if(V1X_NEXT_LOOP.equals(nextStr))
				this.next = Next.LOOPFORM;
			else if(V1X_NEXT_EXIT.equals(nextStr))
				this.next = Next.EXITAPP;
			else
				throw iae;
		}
	}

	/**
	 * @return the vibrateOnSave
	 */
	public boolean isVibrateOnSave()
	{
		return vibrateOnSave;
	}

	/**
	 * @param vibrateOnSave
	 *
	 */
	public void setVibrateOnSave(boolean vibrateOnSave)
	{
		this.vibrateOnSave = vibrateOnSave;
	}

	/**
	 * @return the saveSoundRelativePath
	 */
	public String getSaveSoundRelativePath()
	{
		return saveSoundRelativePath;
	}

	/**
	 * Set the save sound
	 * @param saveSoundRelativePath
	 */
	public void setSaveSoundRelativePath(String saveSoundRelativePath)
	{
		this.saveSoundRelativePath = saveSoundRelativePath;
	}

	/**
	 * @return the index
	 */
	public int getIndex()
	{
		return index;
	}

	/**
	 * @return the producesRecords
	 */
	public boolean isProducesRecords()
	{
		if(producesRecords)
			getSchema(); // make sure getSchema() is at least called once
		return producesRecords;
	}

	/**
	 * The returned Schema object will contain all columns defined by fields in the form, plus the implicitly added
	 * columns (StartTime & DeviceID, which together are used as the primary key, and the optional EndTime). However,
	 * those implicit columns are only added if at least 1 user-defined field has a column. If there are no user-defined
	 * fields with columns then no implicit columns are added and then the whole schema is pointless, therefore in that
	 * case this method will return null instead of a columnless Schema object and the {@link #producesRecords} variable
	 * will be set to {@code false}. 
	 * 
	 * @return
	 */
	public Schema getSchema()
	{
		if(!producesRecords)
			return null;
		if(schema == null)
		{	
			// Generate columns for user-defined fields:
			List<Column<?>> userDefinedColumns = new ArrayList<Column<?>>();
			for(Field f : fields)
				if(!f.isNoColumn())
					userDefinedColumns.addAll(f.getColumns());
	
			// Check if there are user-defined columns at all, if not we don't need to generate a schema at all...
			if(userDefinedColumns.isEmpty())
			{
				producesRecords = false; // this will avoid that we try to generate a schema again
				// this.schema stays null
			}
			else
			{	
				// Create new Schema:
				schema = new Schema(project.getHash(),	/* Project#hash becomes Schema#usageID */
									this.index, 		/* Form#index becomes Schema#usageSubID */
									project.getName() +
									(project.getVariant() != null ? '_' + project.getVariant() : "") +
									"_v" + project.getVersion() +
									":" + id /* = form "name"*/);
				
				/* Add implicit columns
				 * 	StartTime & DeviceID together form the primary key of our records.
				 * 	These columns are implicitly added, together with EndTime if the
				 * 	appropriate attribute was set, *BUT* only if there is at least one
				 * 	user-defined field _with_ a column.
				 */
				// StartTime column:
				DateTimeColumn startTimeCol = DateTimeColumn.Century21NoMS(COLUMN_TIMESTAMP_START, false);		
				schema.addColumn(startTimeCol);
				// EndTime column:
				if(storeEndTime)
					schema.addColumn(DateTimeColumn.Century21NoMS(COLUMN_TIMESTAMP_END, false));
				// Device ID column:
				IntegerColumn deviceIDCol = new IntegerColumn(COLUMN_DEVICE_ID, false, false, 32);
				schema.addColumn(deviceIDCol);
				// Add primary key on StartTime & DeviceID:
				schema.addIndex(new Index(COLUMN_TIMESTAMP_START + COLUMN_DEVICE_ID, true, startTimeCol, deviceIDCol), true);
				
				// Add user-defined columns
				schema.addColumns(userDefinedColumns);
				
				// Seal the schema:
				schema.seal();
			}
		}
		return schema;
	}
	
	/**
	 * Override the schema object with another one, if compatible
	 * 
	 * @param newSchema
	 */
	/*public void setSchema(Schema newSchema)
	{
		if(getSchema().equals(newSchema, true, true)) // check if the schema is identical/equivalent to the one we have/need 
			this.schema = newSchema; // we accept the new one
		else
			throw new IllegalArgumentException("The provided schema is not compatible with this form!");
	}*/

	public CollectorRecord newRecord(long deviceID)
	{
		if(isProducesRecords())
		{
			CollectorRecord record = new CollectorRecord(this);
	
			// Set current time as start timestamp
			((DateTimeColumn) schema.getColumn(COLUMN_TIMESTAMP_START)).storeValue(record, new DateTime() /*= now*/);
	
			// Set deviceID
			((IntegerColumn) schema.getColumn(COLUMN_DEVICE_ID)).storeValue(record, deviceID);
	
			return record;
		}
		else
			return null;
	}
	
	public Column<?> getColumnFor(Field field)
	{
		if(isProducesRecords())
			return getSchema().getColumn(field.getID());
		else
			return null;
	}
	
	public void finish(Record record)
	{
		if(storeEndTime)
			// Set current time as end timestamp
			((DateTimeColumn) schema.getColumn(COLUMN_TIMESTAMP_END)).storeValue(record, new DateTime() /*= now*/);
	}
	
	public void addWarning(String warning)
	{
		if(warnings == null)
			warnings = new ArrayList<String>();
		warnings.add(warning);
	}
	
	public List<String> getWarnings()
	{
		if(warnings == null)
			return new ArrayList<String>(); //leave this.warnings null
		return warnings;
	}
	
	public List<File> getFiles(Project project)
	{
		List<File> paths = new ArrayList<File>();
		CollectionUtils.addIgnoreNull(paths, project.getImageFile(backButtonImageRelativePath));
		CollectionUtils.addIgnoreNull(paths, project.getImageFile(cancelButtonImageRelativePath));
		CollectionUtils.addIgnoreNull(paths, project.getImageFile(forwardButtonImageRelativePath));
		CollectionUtils.addIgnoreNull(paths, project.getImageFile(shortcutImageRelativePath));
		CollectionUtils.addIgnoreNull(paths, project.getSoundFile(saveSoundRelativePath));
		//Add paths for fields:
		for(Field field : fields)
			CollectionUtils.addAllIgnoreNull(paths, field.getFiles(project));
		return paths;
	}
	
	public String toString()
	{
		return id;
	}

}