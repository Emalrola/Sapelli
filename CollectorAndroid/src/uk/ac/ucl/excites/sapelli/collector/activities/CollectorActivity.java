package uk.ac.ucl.excites.sapelli.collector.activities;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import uk.ac.ucl.excites.sapelli.R;
import uk.ac.ucl.excites.sapelli.collector.BuildInfo;
import uk.ac.ucl.excites.sapelli.collector.control.CollectorController;
import uk.ac.ucl.excites.sapelli.collector.model.Trigger;
import uk.ac.ucl.excites.sapelli.collector.model.Trigger.Key;
import uk.ac.ucl.excites.sapelli.collector.model.fields.PhotoField;
import uk.ac.ucl.excites.sapelli.collector.ui.CollectorView;
import uk.ac.ucl.excites.sapelli.collector.ui.ControlsUI.Control;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.AndroidAudioUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.AndroidPhotoUI;
import uk.ac.ucl.excites.sapelli.shared.util.TimeUtils;
import uk.ac.ucl.excites.sapelli.shared.util.io.FileHelpers;
import uk.ac.ucl.excites.sapelli.storage.db.RecordStore;
import uk.ac.ucl.excites.sapelli.storage.eximport.xml.XMLRecordsExporter;
import uk.ac.ucl.excites.sapelli.util.Debug;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;

/**
 * Main Collector activity
 * 
 * @author mstevens, julia, Michalis Vitos
 */
public class CollectorActivity extends ProjectLoadingActivity
{

	// STATICS--------------------------------------------------------
	static private final String TAG = "CollectorActivity";

	static private final String TEMP_PHOTO_PREFIX = "tmpPhoto";
	static private final String TEMP_PHOTO_SUFFIX = ".tmp";
	static private final String TEMP_PHOTO_PATH_KEY = "tmpPhotoPath";

	// Request codes for returning data from intents
	static public final int RETURN_PHOTO_CAPTURE = 1;
	static public final int RETURN_VIDEO_CAPTURE = 2;
	static public final int RETURN_AUDIO_CAPTURE = 3;

	private static final int TIMEOUT_MIN = 5; // timeout after 5 minutes
	
	// DYNAMICS-------------------------------------------------------
	private RecordStore recordStore;
	private CollectorController controller;

	// Temp location to save a photo
	private File tmpPhotoFile;

	// Timeouts, futures & triggers:
	protected boolean pausedForActivityResult = false;
	protected boolean timedOut = false;
	private ScheduledExecutorService scheduleTaskExecutor;
	private ScheduledFuture<?> exitFuture;
	private Map<Trigger,ScheduledFuture<?>> fixedTimerTriggerFutures;
	private List<Trigger> keyPressTriggers;
	private SparseArray<Trigger> keyCodeToTrigger = null;
	private Trigger anyKeyTrigger = null;
	
	// UI
	private CollectorView collectorView;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState); // sets app & projectStore members!

		// Retrieve the tmpPhotoLocation for the saved state
		if(savedInstanceState != null && savedInstanceState.containsKey(TEMP_PHOTO_PATH_KEY))
			tmpPhotoFile = new File(savedInstanceState.getString(TEMP_PHOTO_PATH_KEY));
		
		// Get recordstore object:
		try
		{
			recordStore = app.getRecordStore(this);
		}
		catch(Exception e)
		{
			showErrorDialog("Could not open Project- or RecordStore: " + e.getLocalizedMessage(), true);
			return;
		}
		
		// Check if we can access read/write to the Sapelli folder (created on the SD card or internal mass storage if there is no physical SD card):
		try
		{
			app.getSapelliFolder(); //throws IllegalStateException if not accessible or not create-able
		}
		catch(IllegalStateException ise)
		{	// Inform the user and close the application
			showErrorDialog("Sapelli needs write access to the external/mass storage in order to function. Please insert an SD card and restart the application.", true);
			return;
		}
		
		// Scheduling...
		scheduleTaskExecutor = Executors.newScheduledThreadPool(4); // Creates a thread pool that can schedule commands to run after a given duration, or to execute periodically.
		fixedTimerTriggerFutures = new HashMap<Trigger, ScheduledFuture<?>>();
		
		// Key press triggers
		keyPressTriggers = new ArrayList<Trigger>();
		keyCodeToTrigger = new SparseArray<Trigger>();
		
		// UI setup:
		requestWindowFeature(Window.FEATURE_NO_TITLE); // Remove title
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); // Lock the orientation
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN); // Set to FullScreen

		// Set-up root layout
		collectorView = new CollectorView(this);
		setContentView(collectorView);
		
		// Load the project (mandatory):
		loadProject(true);
	}

	/*
	 * @see uk.ac.ucl.excites.sapelli.collector.activities.ProjectLoadingActivity#postLoadInitialisation()
	 */
	@Override
	protected void postLoadInitialisation()
	{
		// Check if project path is accessible:
		if(!FileHelpers.isReadableWritableDirectory(new File(project.getProjectFolderPath())))
		{	// show error (activity will be exited after used clicks OK in the dialog):
			showErrorDialog("The file storage folder of this project resides at a path that is currently inaccessible (" + project.getProjectFolderPath() + "). You may need to reinsert your SD card, or remove and reload the project.", true);
			return;
		}

		// Set-up controller:
		controller = new CollectorController(project, collectorView, projectStore, recordStore, this);
		collectorView.initialise(controller); // !!!
		
		// Start project:
		controller.startProject();
		
		// Show demo disclaimer if needed:
		if(BuildInfo.DEMO_BUILD)
			showOKDialog("Disclaimer", "This is " + getString(R.string.app_name) + " " + BuildInfo.getVersionInfo() + ".\nFor demonstration purposes only.\nPush the volume-down key to export data.");
	}

	/**
	 * Handle device key presses (down)
	 */
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		// Check for keytriggers...
		Trigger keyTrigger = null;
		//	"Any" key trigger:
		keyTrigger = anyKeyTrigger;
		//	Specific key trigger:
		if(keyTrigger == null)
			keyTrigger = keyCodeToTrigger.get(keyCode);
		//	Fire if we found a matching trigger:
		if(keyTrigger != null)
		{
			controller.fireTrigger(keyTrigger);
			return true;
		}

		// Handle non-trigger firing events...
		switch(keyCode)
		{
			case KeyEvent.KEYCODE_BACK:
			case KeyEvent.KEYCODE_DPAD_LEFT:
				collectorView.getControlsUI().handleControlEvent(Control.BACK, true);
				return true;
			case KeyEvent.KEYCODE_DPAD_RIGHT:
				collectorView.getControlsUI().handleControlEvent(Control.FORWARD, true);
				return true;
			case KeyEvent.KEYCODE_VOLUME_DOWN:
				if(BuildInfo.DEMO_BUILD)
					//TODO export
					showInfoDialog("Exported " + exportDemoRecords(true) + " records to an XML file in " + project.getDataFolderPath() + ".");
				return true;
			case KeyEvent.KEYCODE_VOLUME_UP:
				return true;
		}
		
		// Pass to super...
		return super.onKeyDown(keyCode, event);
	}

	/**
	 * Handle device key presses (up)
	 */
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event)
	{
		switch(keyCode)
		{
		case KeyEvent.KEYCODE_VOLUME_DOWN:
			return true;
		case KeyEvent.KEYCODE_VOLUME_UP:
			return true;
		}
		return super.onKeyUp(keyCode, event);
	}
	
	private int exportDemoRecords(boolean delete)
	{
		int count = 0;
		if(recordStore != null && project != null)
		{
			Log.d(TAG, "Exporting records...");
			try
			{
				(new XMLRecordsExporter(project.getDataFolder())).export(recordStore.retrieveAllRecords(), "DemoRecords");
				if(delete)
					recordStore.deleteAllRecords();
			}
			catch(Exception e)
			{
				Log.e(TAG, "Error upon exporting records", e);
			}
		}
		return count;
	}
	
	public void startAudioRecorderApp(AndroidAudioUI audioUI)
	{
		// TODO call native audio recorder (maybe look at how ODK Collect does it)
	}
	
	private void audioRecorderDone(int resultCode)
	{
		// Just in case ...
		if(!(collectorView.getCurrentFieldUI() instanceof AndroidAudioUI))
		{
			// TODO delete tmpAudio file if not null
			return;
		}
		
		AndroidAudioUI audioUI = (AndroidAudioUI) collectorView.getCurrentFieldUI();
		if(resultCode == RESULT_OK)
		{
			// deal with file ... (see cameraDone())
			
			//audioUI.mediaDone(..., true);
		}
		else
		// if(resultCode == RESULT_CANCELED)
		{
			// TODO delete file if not null & existing
			//audioUI.mediaDone(null, true);
		}
	}
	
	public void startCameraApp(AndroidPhotoUI photoUI)
	{
		/*
		 * Use native/Android camera app
		 * 
		 * Note: There is a known issue regarding the returned intent from MediaStore.ACTION_IMAGE_CAPTURE: -
		 * https://code.google.com/p/android/issues/detail?id=1480 -
		 * http://stackoverflow.com/questions/6530743/beautiful-way-to-come-over-bug-with-action-image-capture -
		 * http://stackoverflow.com/questions/1910608/android-action-image-capture-intent/1932268#1932268 -
		 * http://stackoverflow.com/questions/12952859/capturing-images-with-mediastore-action-image-capture-intent-in-android
		 * 
		 * As a workaround we create a temporary file for the image to be saved and afterwards (in cameraDone()) we rename the file to the correct name.
		 */
		if(!isIntentAvailable(this, MediaStore.ACTION_IMAGE_CAPTURE)) // check if the device is able to handle PhotoField Intents
		{ // Device cannot take photos
			Log.i(TAG, "Cannot take photo due to device limitation.");
			photoUI.mediaDone(null, true); // skip the PhotoField field (pass null to indicate no file was created)
		}
		else
		{ // Device can take photos
			tmpPhotoFile = null;
			try
			{
				// Set up temp file (in the projects data folder)
				tmpPhotoFile = File.createTempFile(TEMP_PHOTO_PREFIX, TEMP_PHOTO_SUFFIX, project.getTempFolder()); // getTempFolder() does the necessary IO checks
				// Set-up intent:
				Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
				takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(tmpPhotoFile));
				// Fire intent:
				pausedForActivityResult = true;
				startActivityForResult(takePictureIntent, RETURN_PHOTO_CAPTURE);
			}
			catch(Exception e)
			{
				if(tmpPhotoFile != null)
					tmpPhotoFile.delete();
				Log.e(TAG, "setPhoto() error", e);
				photoUI.mediaDone(null, true);
			}
		}
	}

	private void cameraDone(int resultCode)
	{
		// Just in case ...
		if(!(collectorView.getCurrentFieldUI() instanceof AndroidPhotoUI))
		{
			if(tmpPhotoFile != null)
				tmpPhotoFile.delete();
			return;
		}
		
		AndroidPhotoUI photoUI = (AndroidPhotoUI) collectorView.getCurrentFieldUI();
		if(resultCode == RESULT_OK)
		{
			if(tmpPhotoFile != null && tmpPhotoFile.exists())
			{
				try
				{ // Rename the file & pass it to the controller
					File newPhoto = ((PhotoField) controller.getCurrentField()).getNewTempFile(controller.getCurrentRecord());
					tmpPhotoFile.renameTo(newPhoto);
					photoUI.mediaDone(newPhoto, true);
				}
				catch(Exception e)
				{ // could not rename the file
					tmpPhotoFile.delete();
					photoUI.mediaDone(null, true);
				}
			}
			else
				photoUI.mediaDone(null, true);
		}
		else
		// if(resultCode == RESULT_CANCELED)
		{
			if(tmpPhotoFile != null)
				tmpPhotoFile.delete(); // Delete the tmp file from the device
			photoUI.mediaDone(null, true);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		pausedForActivityResult = false;
		switch(requestCode)
		{
		case RETURN_AUDIO_CAPTURE:
			/* TODO */
			break;
		case RETURN_PHOTO_CAPTURE:
			cameraDone(resultCode);
			break;
		case RETURN_VIDEO_CAPTURE:
			/* TODO */
			break;
		default:
			return;
		}
	}

	@Override
	public void onSaveInstanceState(Bundle bundle)
	{
		super.onSaveInstanceState(bundle);

		// If the app is taking a photo, save the tmpPhotoLocation
		if(tmpPhotoFile != null)
			bundle.putString(TEMP_PHOTO_PATH_KEY, tmpPhotoFile.getAbsolutePath());
	}

	public static boolean isIntentAvailable(Context context, String action)
	{
		final PackageManager packageManager = context.getPackageManager();
		final Intent intent = new Intent(action);
		List<ResolveInfo> list = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
		return list.size() > 0;
	}

	@Override
	protected void onPause()
	{
		// set timeout timer:
		if(!pausedForActivityResult)
		{
			timedOut = false;
			cancelExitFuture(); // just in case...
			Runnable exitTask = new Runnable()
			{
				@Override
				public void run()
				{ // time's up!
					collectorView.cancelCurrentField();
					controller.cancelAndStop();
					timedOut = true;
					Log.i(TAG, "Time-out reached");
				}
			};
			exitFuture = scheduleTaskExecutor.schedule(exitTask, TIMEOUT_MIN, TimeUnit.MINUTES);

			Debug.d("Scheduled a timeout to take place at: " + TimeUtils.formatTime(TimeUtils.getShiftedCalendar(Calendar.MINUTE, TIMEOUT_MIN), "HH:mm:ss.S"));
		}
		// super:
		super.onPause();
	}
	
	private void cancelExitFuture()
	{
		if(exitFuture != null)
		{
			exitFuture.cancel(true);
			exitFuture = null;
		}
	}

	@Override
	protected void onResume()
	{
		// super:
		super.onResume();

		if(pausedForActivityResult)
			pausedForActivityResult = false;
		else
		{
			// restart project if needed:
			if(timedOut)
			{ 
				// restart project:
				controller.startProject();
				timedOut = false;
			}
			else
				cancelExitFuture(); // cancel exit timer if needed
		}
	}

	@Override
	protected void onNewIntent(Intent intent)
	{
		super.onNewIntent(intent);
		// Change the current intent
		setIntent(intent);
		
		if(controller != null)
			controller.cancelAndStop();
		
		// Load the project (mandatory):
		loadProject(true);
	}

	@Override
	protected void onDestroy()
	{
		// Clean up:
		collectorView.cancelCurrentField();
		cancelExitFuture(); // cancel exit timer if needed
		if(controller != null)
			controller.cancelAndStop();
		// Signal that the activity no longer needs the Store objects:
		app.discardStoreUsage(recordStore, this);
		// super:
		super.onDestroy(); // discards projectStore
	}

	public void setupTimerTrigger(final Trigger trigger)
	{
		Runnable fireTrigger = new Runnable()
		{
			@Override
			public void run()
			{
				CollectorActivity.this.runOnUiThread(new Runnable()
				{
					@Override
					public void run()
					{
						controller.fireTrigger(trigger);
					}
				});
			}
		};		
		fixedTimerTriggerFutures.put(trigger, scheduleTaskExecutor.schedule(fireTrigger, trigger.getFixedTimer(), TimeUnit.SECONDS));
	}
	
	public void disableTimerTrigger(Trigger trigger)
	{
		ScheduledFuture<?> future = fixedTimerTriggerFutures.remove(trigger);
		if(future != null)
			future.cancel(false);
	}
	
	public void setupKeyPressTrigger(Trigger trigger)
	{
		keyPressTriggers.add(trigger);
		refreshTriggerKeyCodes(); // Refresh the key code mappings
	}
	
	public void disableKeyPressTrigger(Trigger trigger)
	{
		keyPressTriggers.remove(trigger);
		refreshTriggerKeyCodes(); // Refresh the key code mappings
	}
	
	/**
	 * Note:
	 * The reason we refresh the whole keyCodeToTrigger hash map must be refreshed after every setup/disable of a
	 * key press trigger is that a page-trigger may be listening to the same key as a form-trigger, in which case the
	 * former should take preference over the later. But if the page-trigger is disabled the form-trigger should
	 * become active again. This should work because page-triggers will always come after the form-triggers in the
	 * keyPressTriggers list.
	 */
	protected void refreshTriggerKeyCodes()
	{
		// Clear:
		keyCodeToTrigger.clear();
		anyKeyTrigger = null;
		// Setup:
		for(Trigger t : keyPressTriggers)
			for(Key k : t.getKeys())
			{
				int keyCode = -1;
				switch(k) // map Trigger.Key enum values onto the KEYCODE's of Android's KeyEvent class 
				{
					case ANY : anyKeyTrigger = t; break;
					case BACK : keyCode = KeyEvent.KEYCODE_BACK; break;
					case SEARCH : keyCode = KeyEvent.KEYCODE_SEARCH; break;
					case HOME : keyCode = KeyEvent.KEYCODE_HOME; break;
					case VOLUME_DOWN : keyCode = KeyEvent.KEYCODE_VOLUME_DOWN; break;
					//case VOLUME_MUTE : keyCode = KeyEvent.KEYCODE_VOLUME_MUTE; break;
					case VOLUME_UP : keyCode = KeyEvent.KEYCODE_VOLUME_UP; break;
					default: break;
				}
				if(keyCode != -1)
					keyCodeToTrigger.put(keyCode, t);
			}
	}
	
}
