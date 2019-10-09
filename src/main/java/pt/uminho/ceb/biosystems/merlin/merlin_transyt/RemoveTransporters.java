package pt.uminho.ceb.biosystems.merlin.merlin_transyt;

import java.util.GregorianCalendar;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import es.uvigo.ei.aibench.core.operation.annotation.Cancel;
import es.uvigo.ei.aibench.core.operation.annotation.Direction;
import es.uvigo.ei.aibench.core.operation.annotation.Operation;
import es.uvigo.ei.aibench.core.operation.annotation.Port;
import es.uvigo.ei.aibench.core.operation.annotation.Progress;
import es.uvigo.ei.aibench.workbench.Workbench;
import pt.uminho.ceb.biosystems.merlin.aibench.datatypes.WorkspaceAIB;
import pt.uminho.ceb.biosystems.merlin.aibench.utilities.TimeLeftProgress;
import pt.uminho.ceb.biosystems.merlin.core.utilities.Enumerators.SequenceType;
import pt.uminho.ceb.biosystems.merlin.core.utilities.Enumerators.SourceType;
import pt.uminho.ceb.biosystems.merlin.services.model.ModelReactionsServices;
import pt.uminho.ceb.biosystems.merlin.services.model.ModelSequenceServices;


/**
 * @author 
 *
 */
@Operation(name="TranSyT",description="remove transport reactions from the model")
public class RemoveTransporters implements Observer {

	private WorkspaceAIB project;
	private TimeLeftProgress progress = new TimeLeftProgress();
	private AtomicBoolean cancel = new AtomicBoolean(false);
	private AtomicInteger querySize;
	private AtomicInteger counter = new AtomicInteger(0);
	private long startTime;
	private String message;


	final static Logger logger = LoggerFactory.getLogger(RemoveTransporters.class);


	@Port(direction=Direction.INPUT, name="new model",description="select the new model workspace",validateMethod="checkNewProject", order = 1)
	public void setNewProject(WorkspaceAIB project) throws Exception {

		ModelReactionsServices.removeReactionsBySource(this.project.getName(), SourceType.TRANSPORTERS);

	}

	//////////////////////////ValidateMethods/////////////////////////////
	/**
	 * @param project
	 */
	public void checkNewProject(WorkspaceAIB project) {

		if(project == null) {

			throw new IllegalArgumentException("no worksapce selected!");
		}
		else {

			this.project = project;

			try {

				if(!ModelSequenceServices.checkGenomeSequences(project.getName(), SequenceType.PROTEIN)) {
					throw new IllegalArgumentException("please set the project fasta ('.faa' or '.fna') files");
				}
				else if(this.project.getTaxonomyID()<0) {

					throw new IllegalArgumentException("please enter the taxonomic identification from NCBI taxonomy");
				}

			} 
			catch (Exception e) {
				Workbench.getInstance().error(e);
				e.printStackTrace();
			}
		}
	}

	/**
	 * @return the progress
	 */
	@Progress(progressDialogTitle = "TranSyT", modal = false, workingLabel = "TranSyT is running...", preferredWidth = 400, preferredHeight=300)
	public TimeLeftProgress getProgress() {

		return progress;
	}

	/**
	 * @param cancel the cancel to set
	 */
	@Cancel
	public void setCancel() {

		progress.setTime(0, 0, 0);
		this.cancel.set(true);
	}

	/* (non-Javadoc)
	 * @see java.util.Observer#update(java.util.Observable, java.lang.Object)
	 */
	public void update(Observable o, Object arg) {

		this.progress.setTime(GregorianCalendar.getInstance().getTimeInMillis() - this.startTime, this.counter.get(), this.querySize.get(), message);
	}




	//	public void propertyChange(PropertyChangeEvent evt) {
	//		// TODO Auto-generated method stub
	//		
	//	}
}