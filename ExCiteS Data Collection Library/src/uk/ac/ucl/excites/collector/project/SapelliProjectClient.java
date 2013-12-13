/**
 * 
 */
package uk.ac.ucl.excites.collector.project;

import java.util.HashSet;
import java.util.Set;

import uk.ac.ucl.excites.collector.database.DataAccess;
import uk.ac.ucl.excites.collector.project.model.Form;
import uk.ac.ucl.excites.collector.project.model.Project;
import uk.ac.ucl.excites.storage.model.Column;
import uk.ac.ucl.excites.storage.model.Record;
import uk.ac.ucl.excites.storage.model.Schema;
import uk.ac.ucl.excites.transmission.Settings;
import uk.ac.ucl.excites.transmission.TransmissionClient;

/**
 * @author mstevens
 *
 */
public class SapelliProjectClient implements TransmissionClient
{

	private DataAccess dao;
	
	public SapelliProjectClient(DataAccess dao)
	{
		this.dao = dao;
	}
	
	@Override
	public Schema getSchema(long usageID, int usageSubID)
	{
		Project p = dao.retrieveProject(usageID); //usageID = Project#hash
		if(p != null)
			return p.getForm(usageSubID).getSchema(); //usageSubID = Form#index
		else
			return null;
	}
	
	@Override
	public Schema getSchemaV1(int schemaID, int schemaVersion)
	{
		Project p = dao.retrieveV1Project(schemaID, schemaVersion);
		if(p != null)
			return p.getForm(0).getSchema(); // return schema of the first (and assumed only) form
		else
			return null;
	}

	@Override
	public Record getNewRecord(Schema schema)
	{
		//TODO start using FormRecord instead!
		return new Record(schema);
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.transmission.TransmissionClient#getSettingsFor(uk.ac.ucl.excites.storage.model.Schema)
	 */
	@Override
	public Settings getSettingsFor(Schema schema)
	{
		/*TODO FIX THIS
		 * This is buggy/hacky! Because schema's can be shared by multiple forms (and no schema ID/version duplicates are allowed)
		 * we cannot safely determine transmission settings based on the schema id/version.
		 */
//		List<Form> forms = dao.retrieveForms(schema.getID(), schema.getVersion());
//		if(!forms.isEmpty())
//		{
//			if(forms.get(0)/*HACK!*/.getProject() != null)
//				return forms.get(0).getProject().getTransmissionSettings();
//			else
//				return null;
//		}
//		else
//		{
//			return null;
//		}
		return null;
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.transmission.TransmissionClient#getFactoredOutColumnsFor(uk.ac.ucl.excites.storage.model.Schema)
	 */
	@Override
	public Set<Column<?>> getFactoredOutColumnsFor(Schema schema)
	{
		Set<Column<?>> columns = new HashSet<Column<?>>();
		columns.add(schema.getColumn(Form.COLUMN_DEVICE_ID));
		return columns;
	}

}