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

package uk.ac.ucl.excites.sapelli.transmission.model.transport.http;

import uk.ac.ucl.excites.sapelli.transmission.model.Correspondent;
import uk.ac.ucl.excites.sapelli.transmission.model.Transmission;

/**
 * @author mstevens
 *
 */
public abstract class HTTPServer extends Correspondent
{

	private final String url;
	
	public HTTPServer(String name, String url)
	{
		super(name, Transmission.Type.HTTP);
		this.url = url;
	}
	
	/**
	 * @return the url
	 */
	public String getUrl()
	{
		return url;
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.transmission.model.Correspondent#getAddress()
	 */
	@Override
	public String getAddress()
	{
		return url;
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.transmission.model.Correspondent#favoursLosslessPayload()
	 */
	@Override
	public boolean favoursLosslessPayload()
	{
		return true;
	}

	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
			return true;
		if(obj instanceof HTTPServer)
		{
			HTTPServer that = (HTTPServer) obj;
			return	super.equals(that) && // Correspondent#equals(Object)
					this.url.equals(that.url);
		}
		return false;
	}
	
	@Override
	public int hashCode()
	{
		int hash = super.hashCode();
		hash = 31 * hash + url.hashCode();
		return hash;
	}

}