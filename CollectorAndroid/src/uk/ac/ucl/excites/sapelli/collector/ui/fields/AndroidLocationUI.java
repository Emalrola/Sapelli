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

package uk.ac.ucl.excites.sapelli.collector.ui.fields;

import java.util.Timer;
import java.util.TimerTask;

import uk.ac.ucl.excites.sapelli.collector.control.Controller;
import uk.ac.ucl.excites.sapelli.collector.control.FieldWithArguments;
import uk.ac.ucl.excites.sapelli.collector.control.Controller.LeaveRule;
import uk.ac.ucl.excites.sapelli.collector.model.fields.LocationField;
import uk.ac.ucl.excites.sapelli.collector.ui.CollectorView;
import uk.ac.ucl.excites.sapelli.shared.util.Timeoutable;
import uk.ac.ucl.excites.sapelli.storage.model.Record;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

/**
 * @author Julia, mstevens
 *
 */
public class AndroidLocationUI extends LocationUI<View, CollectorView>
{

	private Button pageView;
	private LinearLayout waitView;
	private Timer timeoutCounter = null;
	
	public AndroidLocationUI(LocationField field, Controller controller, CollectorView collectorUI)
	{
		super(field, controller, collectorUI);
	}

	@Override
	protected void cancel()
	{
		if(timeoutCounter != null)
			timeoutCounter.cancel();
		//else: do nothing
	}
	
	@Override
	protected View getPlatformView(boolean onPage, boolean enabled, Record record, boolean newRecord)
	{
		//TODO editable
		if(onPage)
		{
			if(pageView == null)
			{
				pageView = new Button(collectorUI.getContext());
				pageView.setText(field.getCaption());
				// TODO some kind of icon/image would be nice (an little flag or crosshairs?)
				pageView.setOnClickListener(new OnClickListener()
				{
					@Override
					public void onClick(View v)
					{
						controller.goTo(new FieldWithArguments(field), LeaveRule.UNCONDITIONAL_NO_STORAGE); // force leaving of the page, to go to the field itself
					}
				});
				// TODO add spinner on button (when startWithForm or startWithPage), make change it for a "got location" icon when location is obtained
				// TODO show "got location" icon when already has location
				// TODO take "enabled" into account!
			}
			return pageView;
		}
		else
		{
			// TODO show coordinates/accuracy to literate users (this will need a new XML attribute)
			if(waitView == null)
			{
				Context context = collectorUI.getContext();
				waitView = new LinearLayout(context);
				waitView.setGravity(Gravity.CENTER);
				waitView.addView(new ProgressBar(context, null, android.R.attr.progressBarStyleLarge));
			}
			
			// Cancel previous counter:
			cancel();
			
			// Start timeout counter
			timeoutCounter = new Timer();
			timeoutCounter.schedule(new TimerTask()
			{
				@Override
				public void run()
				{	//time's up!
					collectorUI.getActivity().runOnUiThread(new Runnable()
					{
						@Override
						public void run()
						{
							timeout();
						}
					});
				}
			}, ((Timeoutable) field).getTimeoutS() * 1000 /*ms*/);
			
			return waitView;
		}
		
	}

}
