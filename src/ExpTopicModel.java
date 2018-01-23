import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.iterator.ArrayIterator;
import cc.mallet.types.InstanceList;

import java.util.Arrays;


public abstract class ExpTopicModel {
	final ExpData expData;
	final Pipe pipe;
	final InstanceList instances;

	public ExpTopicModel(ExpData expData) {
		this.expData = expData;
		this.pipe = new ExpData2FeatureSequence(expData.getGenes());
		this.instances = new InstanceList(pipe);
		for (int[] data : expData.getData()) {
			instances.addThruPipe(new ArrayIterator(Arrays.asList(data)));
		}
	}
}
