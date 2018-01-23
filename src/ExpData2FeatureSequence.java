import cc.mallet.pipe.Pipe;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;


public class ExpData2FeatureSequence extends Pipe {
	private String[] genes;

	ExpData2FeatureSequence(String[] genes) {
		super(new Alphabet(), null);
		this.genes = genes;
	}

	/**
	 * @param carrier int[]
	 * @return Malletに通すフォーマットのdataがsetされいてるinstance
	 */
	@Override
	public Instance pipe(Instance carrier) {
		int[] input = (int[]) carrier.getData();
		FeatureSequence fs = new FeatureSequence(getDataAlphabet(), input.length);

		for (int i = 0; i < input.length; i++) {
			for (int j = 0; j < input[i]; j++) {
				fs.add(this.genes[i]);
			}
		}
		carrier.setData(fs);
		return carrier;
	}
}
