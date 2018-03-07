import java.io.File;
import java.io.IOException;

public class Estimate {
	public static void main(String[] args) throws IOException {
		if (args.length == 7) {
			// LDA
			int numOfTopics = Integer.parseInt(args[0]);
			File data = new File(args[1]);
			File genes = new File(args[2]);
			File thetaOutput = new File(args[3]);
			File phiOutput = new File(args[4]);
			int numOfThreads = Integer.parseInt(args[5]);
			ExpData expData = new ExpData(data, genes);
			LDA model = new LDA(expData, numOfTopics, thetaOutput, phiOutput);
			model.tm.setNumThreads(numOfThreads);
			model.tm.setBurninPeriod(Integer.parseInt(args[6]));
			model.tm.estimate();
		} else if (args.length == 10){
			int numSuperTopics = Integer.parseInt(args[0]);
			int numSubTopics = Integer.parseInt(args[1]);
			int numIterations = Integer.parseInt(args[2]);
			int burninPeriod = Integer.parseInt(args[3]);

			File data = new File(args[4]);
			File genes = new File(args[5]);

			File superTopicOutput = new File(args[6]);
			File subTopicOutput = new File(args[7]);

			File superSubWeightsOutput = new File(args[8]);
			File wordOutput = new File(args[9]);


			ExpData expData = new ExpData(data, genes);
			PAM model = new PAM(expData, numSuperTopics, numSubTopics, superTopicOutput, subTopicOutput, superSubWeightsOutput, wordOutput);

			model.estimate(numIterations, burninPeriod, expData.getGenes());
		} else {
			System.err.println("Args number is invalid.");
			System.exit(1);
		}


	}
}