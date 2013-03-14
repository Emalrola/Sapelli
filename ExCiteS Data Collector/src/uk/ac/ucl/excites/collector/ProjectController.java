/**
 * 
 */
package uk.ac.ucl.excites.collector;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import uk.ac.ucl.excites.collector.project.db.DataAccess;
import uk.ac.ucl.excites.collector.project.model.ChoiceField;
import uk.ac.ucl.excites.collector.project.model.EndField;
import uk.ac.ucl.excites.collector.project.model.Field;
import uk.ac.ucl.excites.collector.project.model.Field.Optionalness;
import uk.ac.ucl.excites.collector.project.model.Form;
import uk.ac.ucl.excites.collector.project.model.LocationField;
import uk.ac.ucl.excites.collector.project.model.MediaField;
import uk.ac.ucl.excites.collector.project.model.Project;
import uk.ac.ucl.excites.collector.project.ui.ButtonsState;
import uk.ac.ucl.excites.collector.util.DeviceID;
import uk.ac.ucl.excites.collector.util.LocationUtils;
import uk.ac.ucl.excites.storage.model.Record;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;


/**
 * @author mstevens, Michalis Vitos, Julia
 * 
 */
public class ProjectController implements LocationListener
{

	static private final String TAG = "ProjectController";

	private Project project;
	private DataAccess dao;
	private CollectorActivity activity;

	private long deviceID; // 32 bit _unsigned_ CRC32 hashcode

	private Form currentForm;
	private Field currentField;
	private Set<Field> tempDisabledFields;
	private Stack<Field> fieldHistory;

	private Record currentRecord;
	private List<File> currentMediaAttachments;
	
	private LocationManager locationManager;
	private Location currentBestLocation = null;

	public ProjectController(Project project, DataAccess dao, CollectorActivity activity)
	{
		this.project = project;
		this.dao = dao;
		this.activity = activity;

		fieldHistory = new Stack<Field>();
		tempDisabledFields = new HashSet<Field>();
		currentMediaAttachments = new ArrayList<File>();
		deviceID = (new DeviceID(activity)).getCRC32Hash();
	}

	public void startProject()
	{
		startForm(0); //For now projects have only one form
	}

	public void startForm(String formName)
	{
		// Find form with the given name:
		Form form = null;
		for(Form f : project.getForms())
			if(f.getName().equals(formName))
			{
				form = f;
				break;
			}
		if(form != null)
			startForm(form);
		else
			throw new IllegalArgumentException("Form " + formName + " could not be found in this project.");
	}

	public void startForm(int formIndex)
	{
		startForm(project.getForms().get(formIndex));
	}

	public void startForm(Form form)
	{
		currentForm = form;
		
		// Clear stuff:
		fieldHistory.clear();
		tempDisabledFields.clear();
		currentMediaAttachments.clear();
		currentField = null;

		// Open DB
		if(!dao.isOpen())
			dao.openDB();
		
		// Create new currentRecord:
		currentRecord = currentForm.newEntry(deviceID);
		
		// Location...
		List<LocationField> lfStartWithForm = currentForm.getLocationFields(true);
		if(!lfStartWithForm.isEmpty())
			startLocationListener(lfStartWithForm); // start listening for location updates
		else
			stopLocationListener(); // stop listening for location updates

		// Begin completing the form at the start field:
		goTo(currentForm.getStartField());
	}

	public void cancelAndRestartForm()
	{
		//Delete any attachments:
		for(File attachment : currentMediaAttachments)
			if(attachment.exists())
				attachment.delete();
		//Restart the form:
		startForm(currentForm);
	}
	
	public void goForward()
	{
		if(currentField != null)
			goTo(currentForm.getNextField(currentField));
		else
			startForm(currentForm); // this shouldn't happen really...
	}

	public void goBack()
	{
		if(!fieldHistory.isEmpty())
		{
			currentField = null; // !!! otherwise we create loops
			goTo(fieldHistory.pop());
		}
	}

	public void goTo(Field nextField)
	{
		// Leafing current field...
		if(currentField != null && currentField != nextField)
			fieldHistory.add(currentField); // Add to history
		// Entering next field...
		currentField = nextField;
		// Handle LocationField:
		if(currentField instanceof LocationField)
		{
			LocationField lf = (LocationField) currentField;
			if(lf.isWaitAtField() || lf.storeLocation(LocationUtils.getExCiteSLocation(currentBestLocation), currentRecord))
				startLocationListener(lf); // start listening for a location
			else
			{ // we already have a location
				goForward(); // skip the wait screen
				return; // !!!
			}
		}
		if(currentField instanceof MediaField)
		{
			if(((MediaField) currentField).isMaxReached(currentRecord))
			{	//Maximum number of attachments for this field is reached:
				goForward(); //skip field
				return; //!!!
			}
		}
		// Update GUI or loop/exit
		if(!(currentField instanceof EndField))
			activity.setField(currentField); // update GUI
		else
			endForm(); // currentField = _END, so we must loop or exit
	}

	public ButtonsState getButtonsState()
	{
		ButtonsState state = new ButtonsState(
				currentForm.isShowBack() && !fieldHistory.empty(),
				currentForm.isShowCancel() && !fieldHistory.empty(),
				currentForm.isShowForward() && currentField.getOptional() == Optionalness.ALWAYS);
		// Note: these paths may be null (in which case built-in defaults must be used)
		return state;
	}
	
	public boolean isFieldEndabled(Field field)
	{
		return field.isEnabled() && !tempDisabledFields.contains(field);
	}

	/**
	 * To be called from ChoiceView
	 * 
	 * @param chosenChild
	 */
	public void choiceMade(ChoiceField chosenChild)
	{
		// Note: chosenChild is not the currentField! The currentField (also a ChoiceField) is its parent.
		if(chosenChild.isLeaf())
		{
			// Store value
			if(!chosenChild.getRoot().isNoColumn())
				chosenChild.storeValue(currentRecord);
			// Go to next field
			goTo(currentForm.getNextField(chosenChild));
			/*
			 * We cannot use goForward() here because then we would first need to make the chosenChild the currentField, in which case it would end up in the
			 * fieldHistory which does not make sense because a leaf choice cannot be displayed on its own.
			 */
		}
		else
			goTo(chosenChild); // chosenChild becomes the new currentField (we go one level down in the choice tree)
	}

	public void mediaDone(File mediaAttachment)
	{
		MediaField ma = (MediaField) currentField;
		if(mediaAttachment != null && mediaAttachment.exists())
		{
			ma.incrementCount(currentRecord); // Store/increase number of pictures/recordings taken
			if(ma.isMaxReached(currentRecord) && ma.getDisableChoice() != null)
				tempDisabledFields.add(ma.getDisableChoice()); //disable the choice that makes the MA accessible
			currentMediaAttachments.add(mediaAttachment);
			goForward(); //goto next/jump field
		}
		else
		{
			if(ma.getOptional() != Optionalness.ALWAYS)
				//at least one attachment is required:
				goTo(ma); //stay at this field
			else
				goForward(); //goto next/jump field
		}
	}

	public void endForm()
	{
		//Finalise the currentRecord:
		currentForm.finish(currentRecord); //sets end-time if necessary
		
		// Open DB
		if(!dao.isOpen())
			dao.openDB();
		
		// Store currentRecord
		dao.store(currentRecord);
		
		Log.d(TAG, "Stored currentRecord:");
		Log.d(TAG, currentRecord.toString());
		
		// Signal the successful storage of the currentRecord
		// Vibration
		if(currentForm.isVibrateOnEnd())
		{
			Vibrator vibrator = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
			// Vibrate for 600 milliseconds
			vibrator.vibrate(600);
		}
		// Play sound
		String endSound = currentForm.getEndSoundPath();
		if(endSound != null && !endSound.isEmpty())
		{
			File endSoundPath = new File(project.getSoundFolderPath() + endSound);
			if(endSoundPath.exists()) // check if the file really exists
			{
				// Play the sound
				MediaPlayer mp = MediaPlayer.create(activity, Uri.fromFile(endSoundPath));
				mp.start();
				mp.setOnCompletionListener(new OnCompletionListener()
				{
					@Override
					public void onCompletion(MediaPlayer mp)
					{
						mp.release();
					}
				});
			}
		}

		// End action:
		switch(currentForm.getEndAction())
		{
			case Form.END_ACTION_LOOP:
				startForm(currentForm);
				break;
			case Form.END_ACTION_EXIT:
				activity.finish();
				break; // leaves the application!
		}
	}

	/**
	 * @return the currentForm
	 */
	public Form getCurrentForm()
	{
		return currentForm;
	}

	/**
	 * @return the project
	 */
	public Project getProject()
	{
		return project;
	}
	
	/**
	 * @return the currentRecord
	 */
	public Record getCurrentRecord()
	{
		return currentRecord;
	}

	/**
	 * @return the currentField
	 */
	public Field getCurrentField()
	{
		return currentField;
	}

	private void startLocationListener(LocationField locField)
	{
		startLocationListener(Arrays.asList(locField));
	}

	private void startLocationListener(List<LocationField> locFields)
	{
		if(locFields.isEmpty())
			return;
		// get locationmanager:
		if(locationManager == null)
			locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
		// deteriment which provider(s) we need:
		Set<String> providers = new HashSet<String>();
		for(LocationField lf : locFields)
			providers.addAll(LocationUtils.getProvider(locationManager, lf));
		// start listening to each provider:
		for(String p : providers)
			locationManager.requestLocationUpdates(p, LocationField.LISTENER_UPDATE_MIN_TIME_MS, LocationField.LISTENER_UPDATE_MIN_DISTANCE_M, this);
	}

	private void stopLocationListener()
	{
		if(locationManager != null)
			locationManager.removeUpdates(this);
	}

	@Override
	public void onLocationChanged(Location location)
	{
		if(LocationUtils.isBetterLocation(location, currentBestLocation))
			currentBestLocation = location;

		// boolean keepListening = false;
		// for(LocationField lf : currentForm.getLocationFields())
		// {
		// boolean stored = lf.storeLocation(LocationUtils.getExCiteSLocation(location), currentRecord);
		//
		// keepListening |= (lf.isWaitAtField() && currentField != lf);
		// }
		// if(!keepListening)
		// stopLocationListener();
		//

		// avoid overwrite after field?

		// if(currentField instanceof LocationField)
		// { //user is waiting for a location for the currentfield
		// activity.stopLocationTimer(); //stop waiting screen timer!
		// stopLocationListener(); //stop listening for locations
		// ((LocationField) currentField).storeLocation(LocationUtils.getExCiteSLocation(location), currentRecord); //store location
		//
		// goForward(); //continue (will leave waiting screen)
		// }
		// else if(currentForm.getLocationFields().size() == 1)
		// {
		// LocationField lf = currentForm.getLocationFields().get(0);
		// lf.storeLocation(LocationUtils.getExCiteSLocation(location), currentRecord); //store location
		// if(!lf.isWaitAtField())
		// stopLocationListener();
		// }
		// else
		// { //this should not happen really...
		//
		// }
	}

	@Override
	public void onProviderDisabled(String provider)
	{
		// does nothing for now
	}

	@Override
	public void onProviderEnabled(String provider)
	{
		// does nothing for now
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras)
	{
		// does nothing for now
	}

}
