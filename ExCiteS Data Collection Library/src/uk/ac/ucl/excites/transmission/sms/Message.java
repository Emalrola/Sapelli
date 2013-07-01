package uk.ac.ucl.excites.transmission.sms;

import java.util.Arrays;
import java.util.Comparator;

import org.joda.time.DateTime;

/**
 * @author mstevens
 *
 */
public abstract class Message implements Comparable<Message>
{
	
	protected SMSAgent sender;
	protected SMSAgent receiver;
	protected SMSTransmission transmission;
	protected int transmissionID;
	protected DateTime sentAt;		//only on sending side
	protected DateTime deliveredAt;	//only on sending side
	protected DateTime receivedAt;	//only on receiving side
	protected int partNumber;
	protected int totalParts;
	

	/**
	 * To be called on sending side.
	 * 
	 * @param receiver
	 * @param transmission
	 * @param partNumber
	 * @param totalParts
	 */
	public Message(SMSAgent receiver, SMSTransmission transmission, int partNumber, int totalParts)
	{
		if(partNumber < 1 || totalParts < 1 || partNumber > totalParts)
			throw new IllegalArgumentException("Invalid part number (" + partNumber + ") of total number of parts (" + totalParts + ").");
		this.receiver = receiver;
		this.transmission = transmission;
		this.transmissionID = transmission.getID();
		this.partNumber  = partNumber;
		this.totalParts = totalParts;
	}
	
	/**
	 * To be called on receiving side
	 * 
	 * @param sender
	 */
	public Message(SMSAgent sender, DateTime receivedAt)
	{
		this.sender = sender;
		this.receivedAt = receivedAt;
	}
		
	public abstract byte[] getPayload();
	
	public abstract void send(SMSService smsService);
	
	public void setTransmission(SMSTransmission transmission)
	{
		if(this.transmission == null)
			this.transmission = transmission;
		else
			throw new IllegalStateException("Cannot change transmission.");
	}
	
	/**
	 * @return the transmissionID
	 */
	public int getTransmissionID()
	{
		return transmissionID;
	}

	/**
	 * @return the partNumber
	 */
	public int getPartNumber()
	{
		return partNumber;
	}

	/**
	 * @return the totalParts
	 */
	public int getTotalParts()
	{
		return totalParts;
	}

	public boolean isSent()
	{
		return (sentAt != null);
	}
	
	public DateTime getSentAt()
	{
		return sentAt;
	}
	
	public boolean isReceived()
	{
		return (receivedAt != null);
	}
	
	public DateTime getReceivedAt()
	{
		return receivedAt;
	}
	
	public void sentCallback()
	{
		sentAt = new DateTime(); //= now
		transmission.partSent(this);
	}
	
	public boolean isDelivered()
	{
		return (deliveredAt != null);
	}
	
	public DateTime getDeliveredAt()
	{
		return deliveredAt;
	}

	public void deliveryCallback()
	{
		deliveredAt = new DateTime(); //TODO get actual time of reception by receiver?
		transmission.partDelivered(this);
	}
	
	public SMSAgent getReceiver()
	{
		return receiver;
	}
	
	public SMSAgent getSender()
	{
		return sender;
	}
	
	public SMSTransmission getTransmission()
	{
		return transmission;
	}
		
	@Override
	public int compareTo(Message another)
	{
		return this.getPartNumber() - another.getPartNumber();
	}
	

	/**
	 * hashCode() method
	 * Ignores transmission (but not transmissionID), sentAt, deliveredAt & receivedAt
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode()
	{
		int hash = 1;
		hash = 31 * hash + (sender == null ? 0 : sender.hashCode());
		hash = 31 * hash + (receiver == null ? 0 : receiver.hashCode());
		hash = 31 * hash + transmissionID;
		hash = 31 * hash + partNumber;
		hash = 31 * hash + totalParts;
		hash = 31 * hash + Arrays.hashCode(getPayload());
		return hash;
	}
	
	/**
	 * equals() method
	 * Ignores transmission (but not transmissionID), sentAt, deliveredAt & receivedAt
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj)
	{
		if(obj instanceof Message)
		{
			Message another = (Message) obj;
			return	(this.sender == null ? another.sender == null : this.sender.equals(another.sender)) &&
					(this.receiver == null ? another.receiver == null : this.receiver.equals(another.receiver)) &&
					this.transmissionID == another.transmissionID &&
					this.partNumber == another.partNumber &&
					this.totalParts == another.totalParts &&
					Arrays.equals(this.getPayload(), another.getPayload());
		}
		return false;
	}
	
	/**
	 * MessageComparator class.
	 * 
	 * Note:
	 * 	This class is no longer used because Message now implements Comparable<Message>.
	 * 	However for the time being we should not remove the comparator class because that breaks compatibility with old DB4LO dumps.
	 * 
	 * @deprecated
	 * @author mstevens
	 */
	public static class MessageComparator implements Comparator<Message>
	{

		@Override
		public int compare(Message lhs, Message rhs)
		{
			return lhs.getPartNumber() - rhs.getPartNumber();
		}
		
	}
	
}
