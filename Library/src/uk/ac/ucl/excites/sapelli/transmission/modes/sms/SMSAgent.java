package uk.ac.ucl.excites.sapelli.transmission.modes.sms;


/**
 * @author julia, mstevens
 *
 */
public class SMSAgent
{
	
	//Statics
	static final char SEPARATOR = ';';
	
	static public SMSAgent Parse(String str)
	{
		String[] parts = str.split("\\" + SEPARATOR);
		return new SMSAgent(parts[0]);
	}
	
	//Dynamics
	private String phoneNumber;
	
	public SMSAgent(String phoneNumber)
	{
		if(phoneNumber == null || phoneNumber.isEmpty())
			throw new IllegalArgumentException("Invalid phone number.");
		this.phoneNumber = phoneNumber;
	}
	
	/**
	 * @return the phoneNumber
	 */
	public String getPhoneNumber()
	{
		return phoneNumber;
	}
	
	@Override
	public int hashCode()
	{
		return phoneNumber.hashCode();
	}
	
	@Override
	public boolean equals(Object o)
	{
		if(o instanceof SMSAgent)
			return phoneNumber.equals(((SMSAgent) o).phoneNumber);
		else
			return false;
	}
	
	@Override
	public String toString()
	{
		return phoneNumber + SEPARATOR;
	}

}