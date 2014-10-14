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

package uk.ac.ucl.excites.sapelli.collector.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import uk.ac.ucl.excites.sapelli.collector.control.FieldWithArguments;
import uk.ac.ucl.excites.sapelli.collector.model.fields.EndField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.LocationField;
import uk.ac.ucl.excites.sapelli.shared.util.CollectionUtils;
import uk.ac.ucl.excites.sapelli.storage.model.Column;
import uk.ac.ucl.excites.sapelli.storage.model.PrimaryKey;
import uk.ac.ucl.excites.sapelli.storage.model.Record;
import uk.ac.ucl.excites.sapelli.storage.model.Schema;
import uk.ac.ucl.excites.sapelli.storage.model.columns.IntegerColumn;
import uk.ac.ucl.excites.sapelli.storage.model.columns.TimeStampColumn;
import uk.ac.ucl.excites.sapelli.storage.types.TimeStamp;
import uk.ac.ucl.excites.sapelli.storage.util.ModelFullException;

/**
 * @author mstevens, Michalis Vitos
 *
 */
public class Form
{

	// Statics--------------------------------------------------------
	public static final boolean END_TIME_DEFAULT = false;

	public static final int MAX_FIELDS = 512;
	
	// Where to go next:
	static public enum Next
	{
		LOOPFORM,
		LOOPPROJ,
		PREVFORM,
		NEXTFORM,
		EXITAPP
	}
	public static final Next DEFAULT_NEXT = Next.LOOPFORM;
	public static final String V1X_NEXT_LOOP = "LOOP";
	public static final String V1X_NEXT_EXIT = "EXIT";
	
	public static final boolean V1X_DEFAULT_SHOW_BACK = true;
	public static final boolean V1X_DEFAULT_SHOW_CANCEL = true;
	public static final boolean V1X_DEFAULT_SHOW_FORWARD = true;
	
	public static final boolean DEFAULT_SKIP_ON_BACK = false;
	public static final boolean DEFAULT_SINGLE_PAGE = false;
	public static final boolean DEFAULT_VIBRATE = true;
	public static final String DEFAULT_BUTTON_BACKGROUND_COLOR = "#BABABA"; // gray
	public static final boolean DEFAULT_CLICK_ANIMATION = true;
	public static final boolean DEFAULT_OBFUSCATE_MEDIA_FILES = false;

	public static final String COLUMN_TIMESTAMP_START_NAME = "StartTime";
	public static final TimeStampColumn COLUMN_TIMESTAMP_START = TimeStampColumn.Century21NoMS(COLUMN_TIMESTAMP_START_NAME, false, true);
	public static final String COLUMN_TIMESTAMP_END_NAME = "EndTime";
	public static final TimeStampColumn COLUMN_TIMESTAMP_END = TimeStampColumn.Century21NoMS(COLUMN_TIMESTAMP_END_NAME, false, true);
	public static final String COLUMN_DEVICE_ID_NAME = "DeviceID";
	public static final IntegerColumn COLUMN_DEVICE_ID = new IntegerColumn(COLUMN_DEVICE_ID_NAME, false, false, 32);
	
	// The Screen Transition type between different Screens
	public static enum ScreenTransition
	{
		NONE, VERTICAL, HORIZONTAL
	}

	public static final ScreenTransition DEFAULT_SCREEN_TRANSITION = ScreenTransition.NONE;

	// Dynamics-------------------------------------------------------
	public static enum AudioFeedback
	{
		NONE, LONG_CLICK, SEQUENTIAL
	}

	public static final AudioFeedback DEFAULT_AUDIO_FEEDBACK = AudioFeedback.NONE;

	// Buttons Default Description Text (used for accessibility support)
	public static final String DEFAULT_FORWARD_BUTTON_DESCRIPTION = "Forward";
	public static final String DEFAULT_CANCEL_BUTTON_DESCRIPTION = "Cancel";
	public static final String DEFAULT_BACK_BUTTON_DESCRIPTION = "Back";

	// Dynamics-------------------------------------------------------
	private final Project project;
	private final String id;
	private final short position;
	private boolean producesRecords = true;
	private boolean skipOnBack = DEFAULT_SKIP_ON_BACK;
	private Schema schema;

	private transient List<String> warnings;
	
	// Fields
	private Field startField;
	private final List<Field> fields;
	private List<Trigger> triggers;
	
	// Android shortcut:
	private String shortcutImageRelativePath;

	// Click Animation:
	private boolean clickAnimation = DEFAULT_CLICK_ANIMATION;
	
	// ScreenTransition:
	private ScreenTransition screenTransition = DEFAULT_SCREEN_TRANSITION;

	// Obfuscate Media Files:
	private AudioFeedback audioFeedback = DEFAULT_AUDIO_FEEDBACK;

	// Obfuscate Media Files:
	private boolean obfuscateMediaFiles = DEFAULT_OBFUSCATE_MEDIA_FILES;

	// Timestamps
	private boolean storeEndTime;

	// End action:
	private Next next = DEFAULT_NEXT;
	private boolean vibrateOnSave = DEFAULT_VIBRATE;
	private String saveSoundRelativePath;

	// Buttons:
	private String buttonBackgroundColor = DEFAULT_BUTTON_BACKGROUND_COLOR;
	private String backButtonImageRelativePath;
	private String backButtonDescription = DEFAULT_BACK_BUTTON_DESCRIPTION;
	private String cancelButtonImageRelativePath;
	private String cancelButtonDescription = DEFAULT_CANCEL_BUTTON_DESCRIPTION;
	private String forwardButtonImageRelativePath;
	private String forwardButtonDescription = DEFAULT_FORWARD_BUTTON_DESCRIPTION;
	
	public Form(Project project, String id)
	{
		if(project == null || id == null || id.isEmpty())
			throw new IllegalArgumentException("A project and non-empty id are required!");
		
		this.project = project;
		this.id = id;
		
		this.fields = new ArrayList<Field>();
		this.position = (short) project.getForms().size();
		project.addForm(this); //!!!
	}

	/**
	 * @return the project
	 */
	public Project getProject()
	{
		return project;
	}

	/**
	 * @param f
	 * @throws IllegalStateException when the maximum number of forms is reached
	 */
	public void addField(Field f) throws IllegalStateException
	{
		 if(fields.size() == MAX_FIELDS)
			throw new IllegalStateException("Maximum number of fields reached");
		fields.add(f);
	}

	public int getFieldPosition(Field field)
	{
		return fields.indexOf(field.getRoot());
	}

	/**
	 * @param current
	 * @return the next field to go to along with passed arguments, or null if the next field could not be determined (likely because the current field is part of a page)
	 */
	public FieldWithArguments getNextFieldAndArguments(Field current)
	{
		// Check for jump field (possibly the one of a parent in case of ChoiceField):
		Field nextF = current.getJump();
		if(nextF == null)
		{	// No jump is set, check for field below current one:
			int currentPos = getFieldPosition(current);
			if(currentPos < 0)
				// This field is not part of the form (it is likely part of a page):
				return null; // don't throw an exception here
			if(currentPos + 1 < fields.size())
				nextF = fields.get(currentPos + 1); // go to next field in the form
			else
				nextF = new EndField(this, true, next); // current field is the last of the form, go to the form's "next", but save the record first
		}
		return new FieldWithArguments(nextF, current.getNextFieldArguments());
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
	 * Find a field of the form by its position
	 * 
	 * @param fieldPosition
	 * @return the field at the specified position, or null if no such field exists in this form
	 */
	public Field getField(int fieldPosition)
	{
		try
		{
			return fields.get(fieldPosition);
		}
		catch(IndexOutOfBoundsException e)
		{
			return null;
		}
	}

	/**
	 * @return the start
	 */
	public Field getStartField()
	{
		return startField;
	}

	/**
	 * @param start
	 *            the start to set
	 */
	public void setStartField(Field start)
	{
		this.startField = start;
	}

	public void addTrigger(Trigger trigger)
	{
		if(triggers == null)
			 triggers = new ArrayList<Trigger>();
		triggers.add(trigger);
	}

	/**
	 * @return the triggers
	 */
	public List<Trigger> getTriggers()
	{
		return triggers != null ? triggers : Collections.<Trigger> emptyList();
	}

	/**
	 * @return the clickAnimation
	 */
	public boolean isClickAnimation()
	{
		return clickAnimation;
	}

	/**
	 * @param clickAnimation
	 *            the animation to set
	 */
	public void setClickAnimation(boolean clickAnimation)
	{
		this.clickAnimation = clickAnimation;
	}

	/**
	 * @return the screenTransition
	 */
	public ScreenTransition getScreenTransition()
	{
		return screenTransition;
	}

	/**
	 * @param screenTransitionStr
	 *            the screenTransition to set
	 */
	public void setScreenTransition(String screenTransitionStr)
	{
		if(screenTransitionStr == null)
			return; // default ScreenTransition will be used
		screenTransitionStr = screenTransitionStr.toUpperCase(); // Make upper case
		try
		{
			this.screenTransition = ScreenTransition.valueOf(screenTransitionStr);
		}
		catch(IllegalArgumentException iae)
		{
			throw iae;
		}
	}

	/**
	 * @return the obfuscateMediaFiles
	 */
	public AudioFeedback getAudioFeedback()
	{
		return audioFeedback;
	}

	/**
	 * @param audioFeedbackStr
	 *            the audioFeedbackStr to set
	 */
	public void setAudioFeedback(String audioFeedbackStr)
	{
		if(audioFeedbackStr == null)
			return; // default Audio Feedback will be used
		audioFeedbackStr = audioFeedbackStr.toUpperCase(); // Make upper case
		try
		{
			this.audioFeedback = AudioFeedback.valueOf(audioFeedbackStr);
		}
		catch(IllegalArgumentException iae)
		{
			throw iae;
		}
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
	public String getBackButtonDescription()
	{
		return backButtonDescription;
	}

	/**
	 * @param backButtonDescription the backButtonDescription to set
	 */
	public void setBackButtonDescription(String backButtonDescription)
	{
		this.backButtonDescription = backButtonDescription;
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
	public String getCancelButtonDescription()
	{
		return cancelButtonDescription;
	}

	/**
	 * @param cancelButtonDescription the cancelButtonDescription to set
	 */
	public void setCancelButtonDescription(String cancelButtonDescription)
	{
		this.cancelButtonDescription = cancelButtonDescription;
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
	public String getForwardButtonDescription()
	{
		return forwardButtonDescription;
	}

	/**
	 * @param forwardButtonDescription the forwardButtonDescription to set
	 */
	public void setForwardButtonDescription(String forwardButtonDescription)
	{
		this.forwardButtonDescription = forwardButtonDescription;
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
		List<LocationField> locFields = new ArrayList<LocationField>();
		for(Field f : fields)
			if(f instanceof LocationField)
				locFields.add((LocationField) f);
		return locFields;
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
	 * @return the skipOnBack
	 */
	public boolean isSkipOnBack()
	{
		return skipOnBack;
	}

	/**
	 * @param skipOnBack the skipOnBack to set
	 */
	public void setSkipOnBack(boolean skipOnBack)
	{
		this.skipOnBack = skipOnBack;
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
	 * @return the position within the project
	 */
	public short getPosition()
	{
		return position;
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
	 * Generates the {@link Schema} for this form. It will contain all columns defined by fields in the form, and the
	 * implicitly added columns (StartTime & DeviceID, which together are used as the primary key, and the optional EndTime).
	 * However, those implicit columns are only added if at least 1 user-defined field has a column. If there are no
	 * user-defined fields with columns then no implicit columns are added and then the whole schema is pointless,
	 * therefore in that case the {@link #producesRecords} variable will be set to {@code false} to avoid future attempts
	 * at generating a schema.
	 * 
	 * @throws ModelFullException
	 */
	public void initialiseStorage() throws ModelFullException
	{
		if(!producesRecords)
			return;
		if(schema == null)
		{	
			// Generate columns for user-defined top-level fields:
			List<Column<?>> userDefinedColumns = new ArrayList<Column<?>>();
			for(Field f : fields)
				/*	Important: do *NOT* check noColumn here and do *NOT* replace the call
				 *  to Field#addColumnTo(List<Column<?>>) by a call to Field#getColumn()! 
				 *  The reason (in both cases) is that composite fields like Pages, do not
				 *  have a column of their own but their children do. */
				f.addColumnTo(userDefinedColumns);
	
			// Check if there are user-defined columns at all, if not we don't need to generate a schema at all...
			if(userDefinedColumns.isEmpty())
			{
				producesRecords = false; // this will avoid that we try to generate a schema again
				// this.schema stays null
			}
			else
			{
				// Create new Schema:
				schema = new Schema(project.getModel(),
									project.getModel().getName() + ":" + id);
				
				/* Add implicit columns
				 * 	StartTime & DeviceID together form the primary key of our records.
				 * 	These columns are implicitly added, together with EndTime if the
				 * 	appropriate attribute was set, *BUT* only if there is at least one
				 * 	user-defined field _with_ a column.
				 */
				// StartTime column:
				schema.addColumn(COLUMN_TIMESTAMP_START);
				// EndTime column:
				if(storeEndTime)
					schema.addColumn(COLUMN_TIMESTAMP_END);
				// Device ID column:
				schema.addColumn(COLUMN_DEVICE_ID);
				// Add primary key on StartTime & DeviceID:
				schema.setPrimaryKey(PrimaryKey.WithColumnNames(COLUMN_TIMESTAMP_START, COLUMN_DEVICE_ID));
				
				// Add user-defined columns
				schema.addColumns(userDefinedColumns);
				
				// Seal the schema:
				schema.seal();
			}
		}
	}
	
	/**
	 * Gets the schema. Will return {@code null} in case of a non-data-producing form.
	 * 
	 * @return
	 */
	public Schema getSchema()
	{
		if(schema == null)
			initialiseStorage();
		return schema;
	}
	
	/**
	 * Returns the column associated with the given field.
	 * 
	 * @param field
	 * @return the (non-virtual) column for the given field, or {@code null} in case the field has no column or the schema has not been initialised yet(!)
	 */
	public Column<?> getColumnFor(Field field)
	{
		if(!field.isNoColumn() && producesRecords && schema != null)
		{
			Column<?> col = schema.getColumn(field.getID(), false);
			if(col == null)
				col = schema.getColumn(Column.SanitiseName(field.getID()), false); // try again with sanitised name!
			return col; // may still be null
		}
		else
			return null;
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

	public Record newRecord(long deviceID)
	{
		if(isProducesRecords())
		{
			Record record = getSchema().createRecord();
	
			// Set current time as start timestamp
			COLUMN_TIMESTAMP_START.storeValue(record, TimeStamp.now());
	
			// Set deviceID
			COLUMN_DEVICE_ID.storeValue(record, deviceID);
	
			return record;
		}
		else
			return null;
	}
	
	public TimeStamp getStartTime(Record record)
	{
		return getStartTime(record, false);
	}
	
	public TimeStamp getStartTime(Record record, boolean asStoredBinary)
	{
		if(asStoredBinary)
			return COLUMN_TIMESTAMP_START.retrieveValueAsStoredBinary(record);
		else
			return COLUMN_TIMESTAMP_START.retrieveValue(record);
	}
	
	public TimeStamp getEndTime(Record record)
	{
		if(isStoreEndTime())
			return COLUMN_TIMESTAMP_END.retrieveValue(record);
		else
			return null;
	}
	
	public long getDeviceID(Record record)
	{
		return COLUMN_DEVICE_ID.retrieveValue(record);
	}
	
	public void finish(Record record)
	{
		if(storeEndTime)
			// Set current time as end timestamp
			COLUMN_TIMESTAMP_END.storeValue(record, TimeStamp.now());
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
	
	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
			return true; // references to same object
		if(obj instanceof Form)
		{
			Form that = (Form) obj;
			return	this.id.equals(that.id) &&
					this.project.toString().equals(that.project.toString()) && // DO NOT INCLUDE project ITSELF HERE (otherwise we create an endless loop!)
					this.position == that.position &&
					this.producesRecords == that.producesRecords &&
					this.skipOnBack == that.skipOnBack &&
					(this.schema != null ? this.schema.equals(that.schema, true, true, false) : that.schema == null) &&
					(this.startField != null ? this.startField.equals(that.startField) : that.startField == null) &&
					this.fields.equals(that.fields) &&
					this.getTriggers().equals(that.getTriggers()) &&
					(this.shortcutImageRelativePath != null ? this.shortcutImageRelativePath.equals(that.shortcutImageRelativePath) : that.shortcutImageRelativePath == null) &&
					this.clickAnimation == that.clickAnimation &&
					this.screenTransition == that.screenTransition &&
					this.obfuscateMediaFiles == that.obfuscateMediaFiles &&
					this.storeEndTime == that.storeEndTime &&
					this.next == that.next &&
					this.vibrateOnSave == that.vibrateOnSave &&
					(this.saveSoundRelativePath != null ? this.saveSoundRelativePath.equals(that.saveSoundRelativePath) : that.saveSoundRelativePath == null) &&
					this.buttonBackgroundColor.equals(that.backButtonImageRelativePath) &&
					(this.backButtonImageRelativePath != null ? this.backButtonImageRelativePath.equals(that.backButtonImageRelativePath) : that.backButtonImageRelativePath == null) &&
					(this.cancelButtonImageRelativePath != null ? this.cancelButtonImageRelativePath.equals(that.cancelButtonImageRelativePath) : that.cancelButtonImageRelativePath == null) &&
					(this.forwardButtonImageRelativePath != null ? this.forwardButtonImageRelativePath.equals(that.forwardButtonImageRelativePath) : that.forwardButtonImageRelativePath == null);
		}
		else
			return false;
	}
	
	@Override
	public int hashCode()
	{
		int hash = 1;
		hash = 31 * hash + id.hashCode();
		hash = 31 * hash + project.toString().hashCode(); // DO NOT INCLUDE project ITSELF HERE (otherwise we create an endless loop!)
		hash = 31 * hash + (int) position;
		hash = 31 * hash + (producesRecords ? 0 : 1);
		hash = 31 * hash + (skipOnBack ? 0 : 1);
		hash = 31 * hash + (startField == null ? 0 : startField.hashCode());
		hash = 31 * hash + fields.hashCode();
		hash = 31 * hash + (triggers == null ? 0 : triggers.hashCode());
		hash = 31 * hash + (shortcutImageRelativePath == null ? 0 : shortcutImageRelativePath.hashCode());
		hash = 31 * hash + (clickAnimation ? 0 : 1);
		hash = 31 * hash + screenTransition.ordinal();
		hash = 31 * hash + (obfuscateMediaFiles ? 0 : 1);
		hash = 31 * hash + (storeEndTime ? 0 : 1);
		hash = 31 * hash + next.ordinal();
		hash = 31 * hash + (vibrateOnSave ? 0 : 1);
		hash = 31 * hash + (saveSoundRelativePath == null ? 0 : saveSoundRelativePath.hashCode());
		hash = 31 * hash + buttonBackgroundColor.hashCode();
		hash = 31 * hash + (backButtonImageRelativePath == null ? 0 : backButtonImageRelativePath.hashCode());
		hash = 31 * hash + (cancelButtonImageRelativePath == null ? 0 : cancelButtonImageRelativePath.hashCode());
		hash = 31 * hash + (forwardButtonImageRelativePath == null ? 0 : forwardButtonImageRelativePath.hashCode());
		// There is no need to include the schema.hashCode() in this computation because the schema it is entirely inferred from things that are included in the computation.
		return hash;
	}

}
