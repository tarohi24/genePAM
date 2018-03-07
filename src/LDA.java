import cc.mallet.topics.PAM4L;
import cc.mallet.topics.ParallelTopicModel;
import cc.mallet.util.Randoms;

import java.io.File;
import java.io.IOException;

/**
 * Created by wataru on 2018/03/06.
 */
public class LDA extends ExpTopicModel{
    final ParallelTopicModel tm;

    public LDA(ExpData expData, int numOfTopics, File thetaOutput, File phiOutput) {
        super(expData);
        this.tm = new ParallelTopicModel(numOfTopics, thetaOutput, phiOutput);
        this.tm.addInstances(this.instances);
    }
}
