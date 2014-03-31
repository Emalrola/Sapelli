package uk.ac.ucl.excites.sapelli.collector.db;

import java.util.List;

import uk.ac.ucl.excites.sapelli.collector.model.Project;
import uk.ac.ucl.excites.sapelli.collector.util.DuplicateException;
import uk.ac.ucl.excites.sapelli.shared.db.Store;

/**
 * Interface for Project storage back-ends
 * 
 * @author mstevens
 */
public abstract class ProjectStore implements Store
{

	/**
	 * For backwards compatibility only
	 * 
	 * @param id
	 * @param version
	 * @return
	 */
	public abstract Project retrieveV1Project(int schemaID, int schemaVersion);

	/**
	 * @param project
	 */
	public abstract void store(Project project) throws DuplicateException;

	/**
	 * Retrieves all projects
	 * 
	 * @return
	 */
	public abstract List<Project> retrieveProjects();

	/**
	 * Retrieves specific Project
	 * 
	 * @return null if project was not found
	 */
	public Project retrieveProject(final String name, final String version)
	{
		return retrieveProject(name, null, version);
	}

	/**
	 * Retrieves specific Project
	 * 
	 * @return null if project was not found
	 */
	public abstract Project retrieveProject(String name, String variant, String version);

	/**
	 * Retrieves specific Project
	 * 
	 * @return null if project was not found
	 */
	public abstract Project retrieveProject(long projectHash);

	/**
	 * Delete specific project
	 * 
	 * @return
	 */
	public abstract void delete(Project project);

}