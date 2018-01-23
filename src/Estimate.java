import java.io.File;
import java.io.IOException;

public class Estimate {
	public static void main(String[] args) throws IOException {
		if (args.length != 9) {
			System.err.println("Args number is invalid.");
			System.exit(1);
		}

		int numSuperTopics = Integer.parseInt(args[0]);
		int numSubTopics = Integer.parseInt(args[1]);
		int numIterations = Integer.parseInt(args[2]);

		File data = new File(args[3]);
		File genes = new File(args[4]);

		File superTopicOutput = new File(args[5]);
		File subTopicOutput = new File(args[6]);

		File superSubWeightsOutput = new File(args[7]);
		File wordOutput = new File(args[8]);


		ExpData expData = new ExpData(data, genes);
		PAM model = new PAM(expData, numSuperTopics, numSubTopics, superTopicOutput, subTopicOutput, superSubWeightsOutput, wordOutput);


		model.estimate(numIterations, expData.getGenes());
	}
}