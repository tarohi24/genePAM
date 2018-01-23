import cc.mallet.topics.PAM4L;
import cc.mallet.util.Randoms;
import sun.misc.Regexp;

import java.io.*;
import java.util.Random;


public class PAM extends ExpTopicModel {
	final PAM4L pam4L;

	public PAM(ExpData expData, int superTopics, int subTopics, File superTopicOutput, File subTopicOutput,
	           File superSubWeightsOutput, File wordOutput) {
		super(expData);
		this.pam4L = new PAM4L(superTopics, subTopics, superTopicOutput, subTopicOutput, superSubWeightsOutput, wordOutput);
	}

	public PAM4L estimate(int numIterations, String[] genes) throws IOException {
		int seed = (int) System.currentTimeMillis(); // 現在時刻のミリ秒
		this.pam4L.estimate(this.instances, numIterations, 1, 100,
			new Randoms(seed), genes);

		System.out.println("Calculating Topic Dist...");


		return this.pam4L;
	}

}
